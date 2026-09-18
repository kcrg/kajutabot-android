package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioToggleTest {
    private fun snapshot(
        radioEnabled: Boolean,
        min: Int? = null,
        max: Int? = null,
        version: Long = 7L,
    ) = QueueSnapshotResponse(
        guildId = "g1",
        voiceChannelId = "c1",
        nowPlaying = null,
        nowPlayingFromRadio = false,
        radio = RadioStateResponse(
            isEnabled = radioEnabled,
            minimumDurationSeconds = min,
            maximumDurationSeconds = max,
        ),
        pendingEntries = emptyList(),
        pendingDurationMilliseconds = 0,
        version = version,
    )

    @Test
    fun `disabled radio builds enable request with defaults and current version`() {
        val action = decideRadioToggle(snapshot(radioEnabled = false), "voice-1")
        assertTrue(action is RadioToggleAction.Enable)
        val request = (action as RadioToggleAction.Enable).request
        assertEquals("voice-1", request.voiceChannelId)
        assertEquals(60, request.minimumDurationSeconds)
        assertEquals(600, request.maximumDurationSeconds)
        assertEquals(7L, request.expectedQueueVersion)
    }

    @Test
    fun `disabled radio keeps backend min-max when present`() {
        val action = decideRadioToggle(
            snapshot(radioEnabled = false, min = 120, max = 900),
            "voice-1",
        )
        val request = (action as RadioToggleAction.Enable).request
        assertEquals(120, request.minimumDurationSeconds)
        assertEquals(900, request.maximumDurationSeconds)
    }

    @Test
    fun `enabled radio builds disable with current version`() {
        val action = decideRadioToggle(snapshot(radioEnabled = true), "voice-1")
        assertTrue(action is RadioToggleAction.Disable)
        assertEquals(7L, (action as RadioToggleAction.Disable).expectedVersion)
    }

    @Test
    fun `enabling without channel sends no request`() {
        val action = decideRadioToggle(snapshot(radioEnabled = false), null)
        assertEquals(RadioToggleAction.MissingVoiceChannel, action)
    }
}
