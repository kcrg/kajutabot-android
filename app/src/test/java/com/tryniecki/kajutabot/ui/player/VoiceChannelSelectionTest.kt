package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceChannelSelectionTest {
    private fun channel(id: String) = DiscordVoiceChannelResponse(
        id = id, name = id, position = 0, userCount = 0, isConnected = false,
    )

    @Test fun onlyChannelIsSelectedAutomatically() {
        assertEquals("voice", preferredVoiceChannelId(listOf(channel("voice")), null))
        assertEquals("voice", preferredVoiceChannelId(listOf(channel("voice")), "removed"))
    }

    @Test fun validSavedChoiceWinsAndMultipleChannelsNeedManualChoice() {
        val channels = listOf(channel("first"), channel("second"))
        assertEquals("second", preferredVoiceChannelId(channels, "second"))
        assertNull(preferredVoiceChannelId(channels, null))
        assertNull(preferredVoiceChannelId(channels, "removed"))
        assertNull(preferredVoiceChannelId(emptyList(), null))
    }
}
