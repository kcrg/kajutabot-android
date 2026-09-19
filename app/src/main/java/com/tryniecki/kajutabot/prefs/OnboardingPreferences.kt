package com.tryniecki.kajutabot.prefs

import android.content.Context

class OnboardingPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isCompletedFor(discordUserId: String): Boolean =
        prefs.getBoolean(completedKey(discordUserId), false)

    fun setCompletedFor(discordUserId: String, completed: Boolean = true) {
        prefs.edit().putBoolean(completedKey(discordUserId), completed).apply()
    }

    private fun completedKey(discordUserId: String): String =
        "$KEY_COMPLETED_PREFIX$discordUserId"

    companion object {
        private const val PREFS_NAME = "kajutabot_onboarding"
        private const val KEY_COMPLETED_PREFIX = "completed_"
    }
}
