package com.tryniecki.kajutabot.auth

import android.net.Uri

object DiscordOAuth {
    const val AUTHORIZE_URL = "https://discord.com/oauth2/authorize"

    fun redirectUri(clientId: String): String =
        "discord-$clientId:/authorize/callback"

    fun buildAuthorizationUrl(
        clientId: String,
        redirectUri: String,
        codeChallenge: String,
        state: String,
    ): String {
        return Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", "identify")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
    }

    fun isOAuthCallback(uri: Uri, clientId: String): Boolean {
        if (clientId.isBlank()) return false
        val expectedScheme = "discord-$clientId"
        return uri.scheme == expectedScheme &&
            uri.path == "/authorize/callback"
    }
}
