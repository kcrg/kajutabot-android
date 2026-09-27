package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressTest {
    private fun track(contentId: String = "vid-1") = PlaybackTrackResponse(
        contentId = contentId,
        contentType = "YouTube",
        title = "Track",
        url = "https://example.com/watch?v=$contentId",
        durationMilliseconds = 120_000,
        artworkUrl = null,
        playCount = 0,
    )

    private fun snapshot(
        guildId: String = "g1",
        version: Long = 10,
        nowPlaying: PlaybackTrackResponse? = track(),
        instanceId: String? = "play-1",
        positionMs: Long? = 30_000,
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
        nowPlayingStartedAt = if (positionMs == null) null else "2026-09-27T12:00:00Z",
        playbackInstanceId = instanceId,
        playbackPositionMilliseconds = positionMs,
    )

    @Test
    fun `backend position is clamped and missing position stays unknown`() {
        assertEquals(30_000L, backendPositionMs(30_000, 120_000))
        assertEquals(0L, backendPositionMs(-1, 120_000))
        assertEquals(120_000L, backendPositionMs(130_000, 120_000))
        assertNull(backendPositionMs(null, 120_000))
        assertNull(backendPositionMs(20, 0))
    }

    @Test
    fun `monotonic anchor advances and clamps without wall clock`() {
        assertEquals(15_000L, currentPositionMs(10_000, 1_000, 6_000, 120_000))
        assertEquals(120_000L, currentPositionMs(119_000, 1_000, 11_000, 120_000))
        assertEquals(0.25f, progressFraction(30_000, 120_000), 0.0001f)
    }

    @Test
    fun `repeat restart resets identity even for the same content`() {
        val before = nowPlayingSlide(snapshot(instanceId = "play-1", positionMs = 110_000))
        val repeated = nowPlayingSlide(snapshot(version = 11, instanceId = "play-2", positionMs = 0))
        assertTrue(before.identity != repeated.identity)
        assertEquals(0L, repeated.positionMs)
        assertEquals(before.title, repeated.title)
    }

    @Test
    fun `progress and metadata updates keep playback identity`() {
        val before = nowPlayingSlide(snapshot(nowPlaying = track().copy(artworkUrl = "old")))
        val after = nowPlayingSlide(snapshot(version = 11, nowPlaying = track().copy(artworkUrl = "new"), positionMs = 35_000))
        assertEquals(before.identity, after.identity)
        assertEquals(35_000L, after.positionMs)
        assertFalse(before == after)
        assertEquals(before.identity, nowPlayingSlide(snapshot(nowPlaying = track("corrected-id"))).identity)
    }

    @Test
    fun `preparing playback can have no start time or reported position`() {
        val slide = nowPlayingSlide(snapshot(instanceId = "play-1", positionMs = null))
        assertTrue(slide.hasTrack)
        assertNull(slide.positionMs)
    }

    @Test
    fun `older snapshot version does not overwrite`() {
        assertFalse(shouldApplyQueueSnapshot(snapshot(version = 10), snapshot(version = 9), "g1"))
    }

    @Test
    fun `REST and SignalR snapshots cannot roll back in either arrival order`() {
        val mutation = snapshot(version = 12)
        val event = snapshot(version = 11)
        assertFalse(shouldApplyQueueSnapshot(mutation, event, "g1"))
        assertTrue(shouldApplyQueueSnapshot(event, mutation, "g1"))
    }

    @Test
    fun `same version with changed playback position applies`() {
        assertTrue(shouldApplyQueueSnapshot(snapshot(positionMs = 30_000), snapshot(positionMs = 35_000), "g1"))
    }

    @Test
    fun `first and newer snapshot apply for selected guild only`() {
        assertTrue(shouldApplyQueueSnapshot(null, snapshot(version = 1), "g1"))
        assertTrue(shouldApplyQueueSnapshot(snapshot(version = 10), snapshot(version = 11), "g1"))
        assertFalse(shouldApplyQueueSnapshot(snapshot(guildId = "g1"), snapshot(guildId = "g2"), "g1"))
        assertFalse(shouldApplyQueueSnapshot(snapshot(guildId = "g1"), snapshot(guildId = "g1"), null))
    }
}
