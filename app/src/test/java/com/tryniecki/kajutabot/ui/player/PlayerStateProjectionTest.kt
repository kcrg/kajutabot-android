package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.model.common.TrackResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression tests for the narrow state projections: each slice must carry
 * exactly its own fields, so typing in AddTrack search can't change the
 * player-screen, mini-player or polling slices (and vice versa).
 */
class PlayerStateProjectionTest {

    private fun track(title: String = "Track") = TrackResponse(
        contentId = "vid-1",
        contentType = "youtube",
        title = title,
        url = "https://example.com/watch?v=vid-1",
        durationMilliseconds = 120_000L,
        thumbnailUrl = null,
        playCount = 0,
        cachedAt = null,
        lastPlayedAt = null,
    )

    private fun snapshot(
        guildId: String = "g1",
        version: Long = 10L,
        nowPlaying: TrackResponse? = track(),
    ) = QueueSnapshotResponse(
        guildId = guildId,
        voiceChannelId = "c1",
        nowPlaying = nowPlaying,
        nowPlayingFromRadio = false,
        radio = RadioStateResponse(isEnabled = false),
        pendingEntries = emptyList(),
        pendingDurationMilliseconds = 0,
        version = version,
        nowPlayingStartedAt = "2026-09-18T12:00:00Z",
    )

    @Test
    fun `typing in search does not change player screen slice`() {
        val before = PlayerUiState(searchQuery = "a").toPlayerScreenState()
        val after = PlayerUiState(searchQuery = "abcdefgh").toPlayerScreenState()
        assertEquals(before, after)
    }

    @Test
    fun `typing in search does not change polling keys`() {
        val before = PlayerUiState(
            selectedGuildId = "g1",
            queue = snapshot(),
            searchQuery = "a",
        ).toPollingKeys()
        val after = PlayerUiState(
            selectedGuildId = "g1",
            queue = snapshot(),
            searchQuery = "abcdefgh",
        ).toPollingKeys()
        assertEquals(before, after)
    }

    @Test
    fun `typing in search does not change mini player slice`() {
        val before = PlayerUiState(queue = snapshot(), searchQuery = "a").toMiniPlayerState()
        val after = PlayerUiState(queue = snapshot(), searchQuery = "abcdefgh").toMiniPlayerState()
        assertEquals(before, after)
    }

    @Test
    fun `queue update does not change authenticated entry slice`() {
        val before = PlayerUiState(
            selectedGuildId = "g1",
            selectedVoiceChannelId = "c1",
            queue = snapshot(version = 10L),
        ).toPlayerEntryState()
        val after = PlayerUiState(
            selectedGuildId = "g1",
            selectedVoiceChannelId = "c1",
            queue = snapshot(version = 11L),
        ).toPlayerEntryState()
        assertEquals(before, after)
    }

    @Test
    fun `queue update does not change add track slice`() {
        val before = PlayerUiState(
            queue = snapshot(version = 10L),
            searchQuery = "niyola",
        ).toAddTrackUiState()
        val after = PlayerUiState(
            queue = snapshot(version = 11L),
            searchQuery = "niyola",
        ).toAddTrackUiState()
        assertEquals(before, after)
    }

    @Test
    fun `mini player slice is null without queue or without track`() {
        assertNull(PlayerUiState(queue = null).toMiniPlayerState())
        assertNull(PlayerUiState(queue = snapshot(nowPlaying = null)).toMiniPlayerState())
    }

    @Test
    fun `mini player slice carries slide and mutating flag`() {
        val state = PlayerUiState(queue = snapshot(), isMutating = true).toMiniPlayerState()
        assertEquals(true, state?.isMutating)
        assertEquals(true, state?.slide?.hasTrack)
        assertEquals("Track", state?.slide?.title)
        assertEquals("Track", state?.track?.title)
    }

    @Test
    fun `polling keys carry guild and playback identity`() {
        val keys = PlayerUiState(
            selectedGuildId = "g1",
            queue = snapshot(),
        ).toPollingKeys()
        assertEquals("g1", keys.guildId)
        assertEquals(
            playbackIdentity(track(), "2026-09-18T12:00:00Z"),
            keys.playbackKey,
        )
    }

    @Test
    fun `polling keys are null without selection or track`() {
        assertEquals(
            PlayerPollingKeys(guildId = null, playbackKey = null),
            PlayerUiState(queue = snapshot()).toPollingKeys(),
        )
        assertEquals(
            PlayerPollingKeys(guildId = "g1", playbackKey = null),
            PlayerUiState(selectedGuildId = "g1", queue = snapshot(nowPlaying = null)).toPollingKeys(),
        )
    }
}
