package com.tryniecki.kajutabot.api.model.auth

import kotlinx.serialization.Serializable

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
    val refreshToken: String,
    val refreshTokenExpiresAtUtc: String,
    val user: AuthUserResponse,
)
