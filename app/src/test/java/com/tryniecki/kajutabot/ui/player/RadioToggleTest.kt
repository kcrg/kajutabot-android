package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioToggleTest {
    @Test
    fun `favorites source is passed as radio parameter with current queue version`() {
        val action = decideRadioToggle(snapshot(radioEnabled = false), "voice-1", favoritesSelected = true)
        val request = (action as RadioToggleAction.Enable).request
        assertEquals("favorites", request.mode)
        assertEquals(7L, request.expectedQueueVersion)
    }

    @Test
    fun `cache remains default and active favorites use the same disable action`() {
        val cache = decideRadioToggle(snapshot(radioEnabled = false), "voice-1")
        assertEquals("cache", (cache as RadioToggleAction.Enable).request.mode)
        val queue = snapshot(radioEnabled = true).let { it.copy(radio = it.radio.copy(mode = "favorites")) }
        assertEquals(RadioToggleAction.Disable(7L), decideRadioToggle(queue, null, favoritesSelected = true))
    }

    @Test
    fun `active radio switches both ways without disabling and uses bound channel`() {
        val cached = snapshot(radioEnabled = true, min = 120, max = 900)
        val toFavorites = decideRadioSourceToggle(cached, "another-channel") as RadioToggleAction.Enable
        assertEquals("favorites", toFavorites.request.mode)
        assertEquals("c1", toFavorites.request.voiceChannelId)
        assertEquals(7L, toFavorites.request.expectedQueueVersion)
        assertEquals(120, toFavorites.request.minimumDurationSeconds)
        assertEquals(900, toFavorites.request.maximumDurationSeconds)
        val favorites = cached.copy(radio = cached.radio.copy(mode = "favorites"))
        val toCache = decideRadioSourceToggle(favorites, null) as RadioToggleAction.Enable
        assertEquals("cache", toCache.request.mode)
        assertEquals("c1", toCache.request.voiceChannelId)
    }

    @Test
    fun `source change without any channel sends no request`() {
        val queue = snapshot(radioEnabled = true).copy(voiceChannelId = null)
        assertEquals(RadioToggleAction.MissingVoiceChannel, decideRadioSourceToggle(queue, null))
    }

    private fun snapshot(
        radioEnabled: Boolean,
        min: Int? = null,
        max: Int? = null,
        version: Long = 17L,
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
        pendingEntriesCount = 0,
        pendingDurationMilliseconds = 0,
        version = version,
        queueVersion = 7L,
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
        assertEquals(7L, (action as RadioToggleAction.Disable).expectedQueueVersion)
    }

    @Test
    fun `enabling without channel sends no request`() {
        val action = decideRadioToggle(snapshot(radioEnabled = false), null)
        assertEquals(RadioToggleAction.MissingVoiceChannel, action)
    }
}
