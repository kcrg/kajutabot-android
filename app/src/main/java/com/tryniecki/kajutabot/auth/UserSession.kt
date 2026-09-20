package com.tryniecki.kajutabot.auth

import com.tryniecki.kajutabot.api.model.auth.AuthUserResponse
import com.tryniecki.kajutabot.api.model.auth.SessionType

data class UserSession(
    val accessToken: String,
    val accessTokenExpiresAtUtc: String,
    val refreshToken: String?,
    val refreshTokenExpiresAtUtc: String?,
    val user: AuthUserResponse,
    val sessionType: SessionType = SessionType.DISCORD,
)

sealed interface AuthState {
    data object Restoring : AuthState
    data class SignedOut(val message: String? = null) : AuthState
    data class SignedIn(val user: AuthUserResponse) : AuthState
    data class RecoverableError(val message: String) : AuthState
}
