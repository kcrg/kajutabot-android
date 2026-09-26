package com.tryniecki.kajutabot.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.api.model.auth.SessionType
import com.tryniecki.kajutabot.auth.AuthState
import com.tryniecki.kajutabot.auth.LogoutResult
import com.tryniecki.kajutabot.auth.OAuthStartResult
import com.tryniecki.kajutabot.data.preferences.UserPreferencesRepository
import com.tryniecki.kajutabot.data.repository.SessionRepository
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.ui.theme.PlatformThemeController
import com.tryniecki.kajutabot.ui.theme.ThemeMode
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
    val pendingSharedUrl: String? = null,
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
    val ui: StateFlow<AppUiState> = _ui.asStateFlow()

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
                pendingSharedUrl = null,
                accountError = null,
                loginUrl = null,
            )
        }
    }

    fun onSharedUrl(url: String?) {
        if (url.isNullOrBlank()) return
        _ui.update { it.copy(pendingSharedUrl = url) }
    }

    fun clearPendingSharedUrl() {
        _ui.update { it.copy(pendingSharedUrl = null) }
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
            is OAuthStartResult.Misconfigured -> _ui.update { it.copy(accountError = result.message) }
        }
    }

    fun continueAsGuest() {
        if (_ui.value.isSigningIn) return
        _ui.update { it.copy(isSigningIn = true, isGuestSigningIn = true, accountError = null) }
        viewModelScope.launch {
            try {
                sessionRepository.continueAsGuest()
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
                    is LogoutResult.NeedsRetry -> _ui.update { it.copy(accountError = result.message) }
                    else -> {
                        preferencesRepository.clearGuildSelection()
                        resetSessionUi()
                        when (val start = sessionRepository.startLogin()) {
                            is OAuthStartResult.Ready -> _ui.update { it.copy(loginUrl = start.url) }
                            is OAuthStartResult.Misconfigured -> _ui.update { it.copy(accountError = start.message) }
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
                sessionRepository.handleOAuthCallback(code, state, error)
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
                    is LogoutResult.NeedsRetry -> _ui.update { it.copy(accountError = result.message) }
                    is LogoutResult.SignedOut,
                    is LogoutResult.LocalOnly -> {
                        preferencesRepository.clearGuildSelection()
                        resetSessionUi()
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
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        if (_ui.value.themeMode == mode) return
        _ui.update { it.copy(themeMode = mode) }
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
