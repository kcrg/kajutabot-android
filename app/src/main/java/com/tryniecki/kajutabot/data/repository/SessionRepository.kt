package com.tryniecki.kajutabot.data.repository

import com.tryniecki.kajutabot.auth.AuthState
import com.tryniecki.kajutabot.auth.GuestLoginResult
import com.tryniecki.kajutabot.auth.LogoutResult
import com.tryniecki.kajutabot.auth.OAuthCallbackResult
import com.tryniecki.kajutabot.auth.OAuthStartResult
import com.tryniecki.kajutabot.auth.SessionManager
import com.tryniecki.kajutabot.auth.UserSession
import kotlinx.coroutines.flow.StateFlow

class SessionRepository(
    private val sessionManager: SessionManager,
) {
    val authState: StateFlow<AuthState> = sessionManager.authState
    val sessionIdentity: StateFlow<Long?> = sessionManager.sessionIdentity

    fun currentSession(): UserSession? = sessionManager.currentUserSession()

    suspend fun restore() = sessionManager.restore()
    suspend fun retryRestore() = sessionManager.retryRestore()
    fun startLogin(): OAuthStartResult = sessionManager.startLogin()
    suspend fun continueAsGuest(): GuestLoginResult = sessionManager.continueAsGuest()

    suspend fun handleOAuthCallback(
        code: String?,
        state: String?,
        error: String?,
    ): OAuthCallbackResult = sessionManager.handleOAuthCallback(code, state, error)

    suspend fun logout(): LogoutResult = sessionManager.logout()
}
