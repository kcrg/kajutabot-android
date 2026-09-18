package com.tryniecki.kajutabot.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.auth.AuthState
import com.tryniecki.kajutabot.auth.LogoutResult
import com.tryniecki.kajutabot.auth.OAuthCallbackResult
import com.tryniecki.kajutabot.auth.OAuthStartResult
import com.tryniecki.kajutabot.ui.navigation.AppDestination
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

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn.asStateFlow()

    private val _openUrl = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openUrl: SharedFlow<String> = _openUrl.asSharedFlow()

    private val _currentDestination = MutableStateFlow(AppDestination.PLAYER)
    val currentDestination: StateFlow<AppDestination> = _currentDestination.asStateFlow()

    private val _pendingSharedUrl = MutableStateFlow<String?>(null)
    val pendingSharedUrl: StateFlow<String?> = _pendingSharedUrl.asStateFlow()

    init {
        viewModelScope.launch {
            container.sessionManager.restore()
        }
    }

    fun onDestinationChange(destination: AppDestination) {
        _currentDestination.value = destination
    }

    fun goToPlayer() {
        _currentDestination.value = AppDestination.PLAYER
    }

    fun onSharedUrl(url: String?) {
        if (url.isNullOrBlank()) return
        _pendingSharedUrl.value = url
        _currentDestination.value = AppDestination.PLAYER
    }

    fun consumePendingSharedUrl(): String? {
        val url = _pendingSharedUrl.value
        _pendingSharedUrl.value = null
        return url
    }

    fun peekPendingSharedUrl(): String? = _pendingSharedUrl.value

    fun clearPendingSharedUrl() {
        _pendingSharedUrl.value = null
    }

    fun startLogin() {
        when (val result = container.sessionManager.startLogin()) {
            is OAuthStartResult.Ready -> _openUrl.tryEmit(result.url)
            is OAuthStartResult.Misconfigured -> {
                // Surface via authState SignedOut message without clearing session if signed in.
                // SessionManager does not change state on start, so emit is handled by UI reading result.
                // As a fallback, login screen reads misconfiguration from AppConfig directly.
            }
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
