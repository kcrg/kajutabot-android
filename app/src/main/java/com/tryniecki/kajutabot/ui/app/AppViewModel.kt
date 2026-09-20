package com.tryniecki.kajutabot.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.auth.AuthState
import com.tryniecki.kajutabot.auth.LogoutResult
import com.tryniecki.kajutabot.auth.OAuthCallbackResult
import com.tryniecki.kajutabot.auth.OAuthStartResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val authState: StateFlow<AuthState> = container.sessionManager.authState
    val sessionIdentity: StateFlow<Long?> = container.sessionManager.sessionIdentity

    fun ownerForSession(identity: Long): SessionViewModelOwner {
        return container.ownerForSession(identity)
    }

    private fun resetSessionUi() {
        _pendingSharedUrl.value = null
    }

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()
    private val _isGuestSigningIn = MutableStateFlow(false)
    val isGuestSigningIn: StateFlow<Boolean> = _isGuestSigningIn.asStateFlow()

    private val _openUrl = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openUrl: SharedFlow<String> = _openUrl.asSharedFlow()

    private val _pendingSharedUrl = MutableStateFlow<String?>(null)
    val pendingSharedUrl: StateFlow<String?> = _pendingSharedUrl.asStateFlow()

    init {
        viewModelScope.launch {
            var previousIdentity: Long? = null
            sessionIdentity.collect { identity ->
                if (previousIdentity != null && identity != previousIdentity) resetSessionUi()
                previousIdentity = identity
            }
        }
        viewModelScope.launch {
            container.sessionManager.restore()
        }
    }

    fun onSharedUrl(url: String?) {
        if (url.isNullOrBlank()) return
        _pendingSharedUrl.value = url
    }

    fun clearPendingSharedUrl() {
        _pendingSharedUrl.value = null
    }

    fun startLogin() {
        if (_isSigningIn.value) return
        when (val result = container.sessionManager.startLogin()) {
            is OAuthStartResult.Ready -> _openUrl.tryEmit(result.url)
            is OAuthStartResult.Misconfigured -> {
                // Surface via authState SignedOut message without clearing session if signed in.
                // SessionManager does not change state on start, so emit is handled by UI reading result.
                // As a fallback, login screen reads misconfiguration from AppConfig directly.
            }
        }
    }

    fun continueAsGuest() {
        if (_isSigningIn.value) return
        _isSigningIn.value = true
        _isGuestSigningIn.value = true
        viewModelScope.launch {
            try {
                container.sessionManager.continueAsGuest()
            } finally {
                _isSigningIn.value = false
                _isGuestSigningIn.value = false
            }
        }
    }

    fun switchGuestToDiscord(onFailure: (String) -> Unit) {
        if (_isSigningIn.value) return
        _isSigningIn.value = true
        viewModelScope.launch {
            try {
                when (val result = container.sessionManager.logout()) {
                    is LogoutResult.NeedsRetry -> onFailure(result.message)
                    else -> {
                        container.selectionStore.clear()
                        resetSessionUi()
                        startLoginAfterGuestLogout(onFailure)
                    }
                }
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    private fun startLoginAfterGuestLogout(onFailure: (String) -> Unit) {
        when (val result = container.sessionManager.startLogin()) {
            is OAuthStartResult.Ready -> _openUrl.tryEmit(result.url)
            is OAuthStartResult.Misconfigured -> onFailure(result.message)
        }
    }

    fun handleOAuthCallback(code: String?, state: String?, error: String?) {
        viewModelScope.launch {
            _isSigningIn.value = true
            try {
                container.sessionManager.handleOAuthCallback(code, state, error)
            } finally {
                _isSigningIn.value = false
            }
        }
    }

    fun logout(onResult: (LogoutResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = container.sessionManager.logout()
            if (result is LogoutResult.SignedOut || result is LogoutResult.LocalOnly) {
                container.selectionStore.clear()
                resetSessionUi()
            }
            onResult(result)
        }
    }

    fun retryRestore() {
        viewModelScope.launch {
            container.sessionManager.retryRestore()
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppViewModel(container) as T
        }
    }
}
