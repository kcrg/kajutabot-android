package com.tryniecki.kajutabot.auth

import android.content.Context
import android.content.SharedPreferences

data class PendingOAuth(
    val state: String,
    val codeVerifier: String,
    val createdAtMillis: Long,
)

class PendingOAuthStore(
    context: Context,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun save(state: String, codeVerifier: String) {
        prefs.edit()
            .putString(KEY_STATE, state)
            .putString(KEY_VERIFIER, codeVerifier)
            .putLong(KEY_CREATED_AT, timeProvider())
            .apply()
    }

    fun load(): PendingOAuth? {
        val state = prefs.getString(KEY_STATE, null) ?: return null
        val verifier = prefs.getString(KEY_VERIFIER, null) ?: return null
        val createdAt = prefs.getLong(KEY_CREATED_AT, 0L)
        if (state.isBlank() || verifier.isBlank() || createdAt <= 0L) return null
        return PendingOAuth(state, verifier, createdAt)
    }

    fun loadValid(maxAgeMillis: Long = MAX_AGE_MILLIS): PendingOAuth? {
        val pending = load() ?: return null
        if (timeProvider() - pending.createdAtMillis > maxAgeMillis) {
            clear()
            return null
        }
        return pending
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "kajutabot_oauth_pending"
        const val MAX_AGE_MILLIS = 15L * 60L * 1000L

        private const val KEY_STATE = "oauth_state"
        private const val KEY_VERIFIER = "oauth_verifier"
        private const val KEY_CREATED_AT = "oauth_created_at"
    }
}
