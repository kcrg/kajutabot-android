package com.tryniecki.qumi.ui.app

import androidx.lifecycle.ViewModel
import com.tryniecki.qumi.ui.feedback.FeedbackEffect
import com.tryniecki.qumi.ui.feedback.UserFeedback
import com.tryniecki.qumi.auth.GuestLoginResult
import com.tryniecki.qumi.auth.OAuthCallbackResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.tryniecki.qumi.AppContainer
import com.tryniecki.qumi.api.model.auth.SessionType
import com.tryniecki.qumi.auth.AuthState
import com.tryniecki.qumi.auth.LogoutResult
import com.tryniecki.qumi.auth.OAuthStartResult
import com.tryniecki.qumi.data.preferences.UserPreferencesRepository
import com.tryniecki.qumi.data.repository.SessionRepository
import com.tryniecki.qumi.ui.text.UiText
import com.tryniecki.qumi.ui.theme.PlatformThemeController
import com.tryniecki.qumi.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUiState(
    val isSigningIn: Boolean = false,
    val isGuestSigningIn: Boolean = false,
    val isLoggingOut: Boolean = false,
    val loginUrl: String? = null,
    val accountError: UiText? = null,
    val themeMode: ThemeMode = ThemeMode.NATIVE,
    val isThemeInitialized: Boolean = false,
    val sessionType: SessionType? = null,
)

class AppViewModel(
    private val sessionRepository: SessionRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val platformThemeController: PlatformThemeController,
    private val sessionOwnerProvider: (Long) -> SessionViewModelOwner,
    val isOAuthConfigured: Boolean,
) : ViewModel() {
    val authState: StateFlow<AuthState> = sessionRepository.authState
    val sessionIdentity: StateFlow<Long?> = sessionRepository.sessionIdentity

    private val _ui = MutableStateFlow(AppUiState())
    private val feedback = UserFeedback()
    val feedbackEvents = feedback.events
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()
    private val sharedUrls = ShareEventInbox()
    val sharedUrlEvents = sharedUrls.events

    fun ownerForSession(identity: Long): SessionViewModelOwner = sessionOwnerProvider(identity)

    init {
        viewModelScope.launch {
            preferencesRepository.themeMode.collectLatest { mode ->
                platformThemeController.apply(mode)
                _ui.update { it.copy(themeMode = mode, isThemeInitialized = true) }
            }
        }
        viewModelScope.launch {
            var previousIdentity: Long? = null
            sessionIdentity.collect { identity ->
                if (previousIdentity != null && identity != previousIdentity) {
                    resetSessionUi()
                }
                _ui.update {
                    it.copy(sessionType = if (identity == null) null else sessionRepository.currentSession()?.sessionType)
                }
                previousIdentity = identity
            }
        }
        viewModelScope.launch {
            sessionRepository.restore()
        }
    }

    private fun resetSessionUi() {
        _ui.update {
            it.copy(
                accountError = null,
                loginUrl = null,
            )
        }
    }

    fun onSharedUrl(url: String?) {
        sharedUrls.offer(url)
    }

    fun acknowledgeLoginUrl(url: String) {
        _ui.update { current ->
            if (current.loginUrl == url) current.copy(loginUrl = null) else current
        }
    }

    fun dismissAccountError() {
        _ui.update { it.copy(accountError = null) }
    }

    fun startLogin() {
        if (_ui.value.isSigningIn) return
        when (val result = sessionRepository.startLogin()) {
            is OAuthStartResult.Ready -> _ui.update { it.copy(loginUrl = result.url, accountError = null) }
            is OAuthStartResult.Misconfigured -> {
                _ui.update { it.copy(accountError = result.message) }
                feedback.emit(FeedbackEffect.Reject)
            }
        }
    }

    fun continueAsGuest() {
        if (_ui.value.isSigningIn) return
        _ui.update { it.copy(isSigningIn = true, isGuestSigningIn = true, accountError = null) }
        viewModelScope.launch {
            try {
                when (sessionRepository.continueAsGuest()) {
                    GuestLoginResult.SignedIn -> feedback.emit(FeedbackEffect.Confirm)
                    is GuestLoginResult.Failed -> feedback.emit(FeedbackEffect.Reject)
                    GuestLoginResult.Superseded -> Unit
                }
            } finally {
                _ui.update { it.copy(isSigningIn = false, isGuestSigningIn = false) }
            }
        }
    }

    fun switchGuestToDiscord() {
        if (_ui.value.isSigningIn || _ui.value.isLoggingOut) return
        _ui.update { it.copy(isSigningIn = true, accountError = null) }
        viewModelScope.launch {
            try {
                when (val result = sessionRepository.logout()) {
                    is LogoutResult.NeedsRetry -> {
                        _ui.update { it.copy(accountError = result.message) }
                        feedback.emit(FeedbackEffect.Reject)
                    }
                    else -> {
                        preferencesRepository.clearGuildSelection()
                        resetSessionUi()
                        when (val start = sessionRepository.startLogin()) {
                            is OAuthStartResult.Ready -> _ui.update { it.copy(loginUrl = start.url) }
                            is OAuthStartResult.Misconfigured -> {
                                _ui.update { it.copy(accountError = start.message) }
                                feedback.emit(FeedbackEffect.Reject)
                            }
                        }
                    }
                }
            } finally {
                _ui.update { it.copy(isSigningIn = false) }
            }
        }
    }

    fun handleOAuthCallback(code: String?, state: String?, error: String?) {
        viewModelScope.launch {
            _ui.update { it.copy(isSigningIn = true, accountError = null) }
            try {
                when (sessionRepository.handleOAuthCallback(code, state, error)) {
                    OAuthCallbackResult.Exchanged -> feedback.emit(FeedbackEffect.Confirm)
                    is OAuthCallbackResult.Failed -> feedback.emit(FeedbackEffect.Reject)
                    is OAuthCallbackResult.Ignored -> Unit
                }
            } finally {
                _ui.update { it.copy(isSigningIn = false) }
            }
        }
    }

    fun logout() {
        if (_ui.value.isLoggingOut) return
        _ui.update { it.copy(isLoggingOut = true, accountError = null) }
        viewModelScope.launch {
            try {
                when (val result = sessionRepository.logout()) {
                    is LogoutResult.NeedsRetry -> {
                        _ui.update { it.copy(accountError = result.message) }
                        feedback.emit(FeedbackEffect.Reject)
                    }
                    is LogoutResult.SignedOut,
                    is LogoutResult.LocalOnly -> {
                        preferencesRepository.clearGuildSelection()
                        resetSessionUi()
                        feedback.emit(FeedbackEffect.Confirm)
                    }
                }
            } finally {
                _ui.update { it.copy(isLoggingOut = false) }
            }
        }
    }

    fun retryRestore() {
        viewModelScope.launch { sessionRepository.retryRestore() }
    }

    fun onboardingCompleted(sessionType: SessionType, discordUserId: String): Flow<Boolean> =
        preferencesRepository.onboardingCompleted(sessionType, discordUserId)

    fun completeOnboarding(sessionType: SessionType, discordUserId: String) {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(sessionType, discordUserId)
            feedback.emit(FeedbackEffect.Confirm)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        if (_ui.value.themeMode == mode) return
        _ui.update { it.copy(themeMode = mode) }
        feedback.emit(FeedbackEffect.Selection)
        platformThemeController.apply(mode)
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AppViewModel(
                        sessionRepository = container.sessionRepository,
                        preferencesRepository = container.preferencesRepository,
                        platformThemeController = container.platformThemeController,
                        sessionOwnerProvider = container::ownerForSession,
                        isOAuthConfigured = container.appConfig.isOAuthConfigured,
                    )
                }
            }
    }
}
