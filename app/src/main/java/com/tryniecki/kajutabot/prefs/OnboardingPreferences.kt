package com.tryniecki.kajutabot.prefs

import android.content.Context
import com.tryniecki.kajutabot.api.model.auth.SessionType

class OnboardingPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isCompletedFor(discordUserId: String): Boolean =
        prefs.getBoolean(completedKey(discordUserId), false)

    fun isCompletedFor(sessionType: SessionType, discordUserId: String): Boolean =
        isCompletedFor(identityKey(sessionType, discordUserId))

    fun setCompletedFor(discordUserId: String, completed: Boolean = true) {
        prefs.edit().putBoolean(completedKey(discordUserId), completed).apply()
    }

    fun setCompletedFor(sessionType: SessionType, discordUserId: String, completed: Boolean = true) =
        setCompletedFor(identityKey(sessionType, discordUserId), completed)

    private fun identityKey(sessionType: SessionType, discordUserId: String): String =
        onboardingIdentityKey(sessionType, discordUserId)

    private fun completedKey(discordUserId: String): String =
        "$KEY_COMPLETED_PREFIX$discordUserId"

    companion object {
        private const val PREFS_NAME = "kajutabot_onboarding"
        private const val KEY_COMPLETED_PREFIX = "completed_"
    }
}

internal fun onboardingIdentityKey(sessionType: SessionType, discordUserId: String): String =
    if (sessionType == SessionType.GUEST) "guest" else discordUserId
