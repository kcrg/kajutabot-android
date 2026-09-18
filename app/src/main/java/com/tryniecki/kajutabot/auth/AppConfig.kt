package com.tryniecki.kajutabot.auth

data class AppConfig(
    val apiBaseUrl: String,
    val discordClientId: String,
) {
    val redirectUri: String
        get() = DiscordOAuth.redirectUri(discordClientId)

    val isOAuthConfigured: Boolean
        get() = discordClientId.isNotBlank()
}
