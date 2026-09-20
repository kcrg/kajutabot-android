package com.tryniecki.kajutabot.api.model.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
enum class SessionType {
    @SerialName("discord") DISCORD,
    @SerialName("guest") GUEST,
}

@Serializable
data class DiscordOAuthExchangeRequest(
    val code: String,
    val codeVerifier: String,
    val redirectUri: String,
)

@Serializable
data class RefreshUserSessionRequest(
    val refreshToken: String,
)

@Serializable
data class AuthUserResponse(
    val discordUserId: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String? = null,
)

@Serializable
data class AuthSessionResponse(
    val accessToken: String,
    val accessTokenExpiresAtUtc: String,
    val refreshToken: String? = null,
    val refreshTokenExpiresAtUtc: String? = null,
    val user: AuthUserResponse,
    val sessionType: SessionType = SessionType.DISCORD,
)
