package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the narrow state projections: each slice must carry
 * exactly its own fields, so typing in Search search can't change the
 * player-screen or mini-player slices (and vice versa).
 */
class PlayerStateProjectionTest {

    @Test
    fun `mini player keeps snapshot observation time when switching pages`() {
        val state = PlayerUiState(
            queue = snapshot(),
            queueObservedAtElapsedRealtimeMs = 12_345L,
        )
        assertEquals(12_345L, state.toPlayerScreenState().queueObservedAtElapsedRealtimeMs)
        assertEquals(12_345L, state.toMiniPlayerState()?.slide?.positionObservedAtElapsedRealtimeMs)
    }

    @Test
    fun `transitional empty queue snapshot retains presentation in player and mini player`() {
        val previous = snapshot().nowPlayingPresentationOrNull()
        val state = PlayerUiState(
            queue = snapshot(version = 11, nowPlaying = null).copy(pendingEntriesCount = 1),
            transitionNowPlaying = previous,
            transitionStartedAtNanos = 11,
            queueObservedAtElapsedRealtimeMs = 12_345L,
        )
        assertEquals(previous, state.effectiveNowPlaying)
        assertEquals(previous, state.toPlayerScreenState().presentedNowPlaying)
        assertEquals(nowPlayingSlide(previous, hasQueue = true).identity, state.toMiniPlayerState()?.slide?.identity)
        assertEquals(12_345L, state.toMiniPlayerState()?.slide?.positionObservedAtElapsedRealtimeMs)
    }

    @Test
    fun `queue reorder keeps playback controls available`() {
        assertFalse(shouldBlockPlaybackControls(true, null, true))
        assertFalse(shouldBlockPlaybackControls(true, null, false))
        assertFalse(shouldBlockPlaybackControls(false, null, false))
    }

    @Test
    fun `missing voice channel is marked only after selection loads`() {
        val missing = PlayerUiState(selectedGuildId = "g1", isLoadingGuilds = false).toPlayerScreenState()
        assertTrue(missing.needsVoiceChannelSelection)
        assertFalse(missing.copy(isLoadingVoiceChannels = true).needsVoiceChannelSelection)
        assertFalse(missing.copy(isLoadingGuilds = true).needsVoiceChannelSelection)
        assertFalse(missing.copy(selectedVoiceChannelId = "c1").needsVoiceChannelSelection)
        assertFalse(missing.copy(selectedGuildId = null).needsVoiceChannelSelection)
    }

    private fun track(title: String = "Track") = PlaybackTrackResponse(
        contentId = "vid-1",
        contentType = "youtube",
        title = title,
        url = "https://example.com/watch?v=vid-1",
        durationMilliseconds = 120_000L,
        artworkUrl = null,
        playCount = 0,
    )

    private fun snapshot(
        guildId: String = "g1",
        version: Long = 10L,
        nowPlaying: PlaybackTrackResponse? = track(),
    ) = QueueSnapshotResponse(
        guildId = guildId,
        voiceChannelId = "c1",
        nowPlaying = nowPlaying,
        nowPlayingFromRadio = false,
        radio = RadioStateResponse(isEnabled = false),
        pendingEntries = emptyList(),
        pendingEntriesCount = 0,
        pendingDurationMilliseconds = 0,
        version = version,
        queueVersion = version,
        nowPlayingStartedAt = "2026-09-18T12:00:00Z",
    )

    @Test
    fun `typing in search does not change player screen slice`() {
        val before = PlayerUiState(searchQuery = "a").toPlayerScreenState()
        val after = PlayerUiState(searchQuery = "abcdefgh").toPlayerScreenState()
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
        ).toSearchUiState()
        val after = PlayerUiState(
            queue = snapshot(version = 11L),
            searchQuery = "niyola",
        ).toSearchUiState()
        assertEquals(before, after)
    }

    @Test
    fun `search history is carried only by add track slice`() {
        val state = PlayerUiState(searchHistory = listOf("one", "two"))

        assertEquals(listOf("one", "two"), state.toSearchUiState().searchHistory)
        assertEquals(
            PlayerUiState().toPlayerScreenState(),
            state.toPlayerScreenState(),
        )
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
    fun `selected guild without first queue snapshot stays in initial loading state`() {
        val state = PlayerScreenState(
            selectedGuildId = "g1",
            isLoadingGuilds = false,
            queue = null,
            queueLoadState = QueueLoadState.LOADING,
        )

        assertTrue(state.isInitialContentLoading)
    }

    @Test
    fun `resolved empty queue is content not loading`() {
        val state = PlayerScreenState(
            selectedGuildId = "g1",
            isLoadingGuilds = false,
            queue = snapshot(nowPlaying = null),
            queueLoadState = QueueLoadState.READY,
        )

        assertFalse(state.isInitialContentLoading)
    }

}
