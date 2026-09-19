package com.tryniecki.kajutabot.prefs

import android.content.Context

class FavoritesPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("kajutabot_favorites", Context.MODE_PRIVATE)

    fun shuffle(userId: String): Boolean = prefs.getBoolean("shuffle_$userId", false)

    fun setShuffle(userId: String, enabled: Boolean) {
        prefs.edit().putBoolean("shuffle_$userId", enabled).apply()
    }
}
