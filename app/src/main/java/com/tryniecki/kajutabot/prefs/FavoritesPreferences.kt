package com.tryniecki.kajutabot.prefs

import android.content.Context
import com.tryniecki.kajutabot.api.model.auth.SessionType
import com.tryniecki.kajutabot.auth.UserSession

/** Only a local preference key. The backend resolves Favorites ownership from the JWT. */
internal fun favoritesPreferenceOwnerKey(session: UserSession): String = when (session.sessionType) {
    SessionType.DISCORD -> session.user.discordUserId // Preserve existing installed-user keys.
    SessionType.GUEST -> "guest"
}

class FavoritesPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("kajutabot_favorites", Context.MODE_PRIVATE)

    fun shuffle(ownerKey: String): Boolean = prefs.getBoolean("shuffle_$ownerKey", false)

    fun setShuffle(ownerKey: String, enabled: Boolean) {
        prefs.edit().putBoolean("shuffle_$ownerKey", enabled).apply()
    }
}
