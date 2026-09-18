package com.tryniecki.kajutabot.prefs

import android.content.Context

class GuildSelectionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var guildId: String?
        get() = prefs.getString(KEY_GUILD, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_GUILD) else putString(KEY_GUILD, value)
            }.apply()
        }

    var voiceChannelId: String?
        get() = prefs.getString(KEY_CHANNEL, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_CHANNEL) else putString(KEY_CHANNEL, value)
            }.apply()
        }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "kajutabot_selection"
        private const val KEY_GUILD = "guild_id"
        private const val KEY_CHANNEL = "voice_channel_id"
    }
}
