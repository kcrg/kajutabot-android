package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PlaybackProgressTest {

    private fun track(
        contentId: String = "vid-1",
        contentType: String = "youtube",
        durationMs: Long = 120_000L,
    ) = PlaybackTrackResponse(
        contentId = contentId,
        contentType = contentType,
        title = "Track",
        url = "https://example.com/watch?v=vid-1",
        durationMilliseconds = durationMs,
        artworkUrl = null,
        playCount = 0,
    )

    private fun snapshot(
        guildId: String = "g1",
        version: Long = 10L,
        nowPlaying: PlaybackTrackResponse? = track(),
        startedAt: String? = "2026-09-18T12:00:00Z",
    ) = QueueSnapshotResponse(
        guildId = guildId,
        voiceChannelId = "c1",
        nowPlaying = nowPlaying,
        nowPlayingFromRadio = false,
        radio = RadioStateResponse(isEnabled = false),
        pendingEntries = emptyList(),
        pendingDurationMilliseconds = 0,
        version = version,
        nowPlayingStartedAt = startedAt,
    )

    private fun epoch(raw: String) = Instant.parse(raw).toEpochMilli()

    @Test
    fun `position and progress from wall clock`() {
        val position = initialPositionMs(
            startedAtRaw = "2026-09-18T12:00:00Z",
            durationMs = 120_000L,
            nowUtcMs = epoch("2026-09-18T12:00:30Z"),
        )
        assertEquals(30_000L, position)
        assertEquals(0.25f, progressFraction(position!!, 120_000L), 0.0001f)
    }

    @Test
    fun `backend offset timestamp format parses`() {
        val position = initialPositionMs(
            startedAtRaw = "2026-09-18T12:00:00+00:00",
            durationMs = 120_000L,
            nowUtcMs = epoch("2026-09-18T12:00:30Z"),
        )
        assertEquals(30_000L, position)
    }

    @Test
    fun `position before start clamps to zero`() {
        val position = initialPositionMs(
            startedAtRaw = "2026-09-18T12:00:00Z",
            durationMs = 120_000L,
            nowUtcMs = epoch("2026-09-18T11:59:00Z"),
        )
        assertEquals(0L, position)
        assertEquals(0f, progressFraction(position!!, 120_000L), 0f)
    }

    @Test
    fun `position past end clamps to duration and full progress`() {
        val position = initialPositionMs(
            startedAtRaw = "2026-09-18T12:00:00Z",
            durationMs = 120_000L,
            nowUtcMs = epoch("2026-09-18T12:05:00Z"),
        )
        assertEquals(120_000L, position)
        assertEquals(1f, progressFraction(position!!, 120_000L), 0f)
    }

    @Test
    fun `non-positive duration means no progress`() {
        assertNull(
            initialPositionMs("2026-09-18T12:00:00Z", 0L, epoch("2026-09-18T12:00:30Z")),
        )
        assertNull(
            initialPositionMs("2026-09-18T12:00:00Z", -5L, epoch("2026-09-18T12:00:30Z")),
        )
        assertEquals(0f, progressFraction(30_000L, 0L), 0f)
    }

    @Test
    fun `missing or invalid timestamp means no progress`() {
        val now = epoch("2026-09-18T12:00:30Z")
        assertNull(initialPositionMs(null, 120_000L, now))
        assertNull(initialPositionMs("   ", 120_000L, now))
        assertNull(initialPositionMs("not-a-timestamp", 120_000L, now))
    }

    @Test
    fun `monotonic anchor advances without wall clock`() {
        assertEquals(
            15_000L,
            currentPositionMs(
                anchorPositionMs = 10_000L,
                anchorElapsedRealtimeMs = 1_000L,
                nowElapsedRealtimeMs = 6_000L,
                durationMs = 120_000L,
            ),
        )
    }

    @Test
    fun `monotonic position clamps at duration`() {
        assertEquals(
            120_000L,
            currentPositionMs(
                anchorPositionMs = 119_000L,
                anchorElapsedRealtimeMs = 1_000L,
                nowElapsedRealtimeMs = 11_000L,
                durationMs = 120_000L,
            ),
        )
    }

    @Test
    fun `same track with new start is a new playback identity`() {
        val t = track()
        val first = playbackIdentity(t, "2026-09-18T12:00:00Z")
        val repeated = playbackIdentity(t, "2026-09-18T12:04:00Z")
        assertTrue(first.isNotBlank())
        assertTrue(first != repeated)
        assertEquals(first, playbackIdentity(t, "2026-09-18T12:00:00Z"))
    }

    @Test
    fun `older snapshot version does not overwrite`() {
        assertFalse(
            shouldApplyQueueSnapshot(
                current = snapshot(version = 10L),
                incoming = snapshot(version = 9L),
                selectedGuildId = "g1",
            ),
        )
    }

    @Test
    fun `REST and SignalR snapshots cannot roll back in either arrival order`() {
        val mutation = snapshot(version = 12L)
        val event = snapshot(version = 11L)
        assertFalse(shouldApplyQueueSnapshot(mutation, event, "g1"))
        assertTrue(shouldApplyQueueSnapshot(event, mutation, "g1"))
    }

    @Test
    fun `same version with changed metadata applies`() {
        val before = snapshot(nowPlaying = track().copy(artworkUrl = "old"))
        val updated = snapshot(nowPlaying = track().copy(artworkUrl = "new"))
        assertTrue(shouldApplyQueueSnapshot(before, updated, "g1"))
    }

    @Test
    fun `newer snapshot version applies`() {
        assertTrue(
            shouldApplyQueueSnapshot(
                current = snapshot(version = 10L),
                incoming = snapshot(version = 11L),
                selectedGuildId = "g1",
            ),
        )
        assertTrue(
            shouldApplyQueueSnapshot(
                current = snapshot(version = 10L),
                incoming = snapshot(version = 10L),
                selectedGuildId = "g1",
            ),
        )
    }

    @Test
    fun `first snapshot applies`() {
        assertTrue(
            shouldApplyQueueSnapshot(
                current = null,
                incoming = snapshot(version = 1L),
                selectedGuildId = "g1",
            ),
        )
    }

    @Test
    fun `snapshot of another guild does not apply`() {
        assertFalse(
            shouldApplyQueueSnapshot(
                current = snapshot(guildId = "g1", version = 10L),
                incoming = snapshot(guildId = "g2", version = 99L),
                selectedGuildId = "g1",
            ),
        )
        assertFalse(
            shouldApplyQueueSnapshot(
                current = snapshot(guildId = "g1", version = 10L),
                incoming = snapshot(guildId = "g1", version = 11L),
                selectedGuildId = null,
            ),
        )
    }

    @Test
    fun `remaining time until expected track end`() {
        assertEquals(
            90_000L,
            remainingMs(
                startedAtRaw = "2026-09-18T12:00:00Z",
                durationMs = 120_000L,
                nowUtcMs = epoch("2026-09-18T12:00:30Z"),
            ),
        )
    }

    @Test
    fun `remaining time invalid without timing`() {
        assertNull(remainingMs(null, 120_000L, epoch("2026-09-18T12:00:30Z")))
    }
}
