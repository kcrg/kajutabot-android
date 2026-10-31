package com.tryniecki.qumi.ui.player

import com.tryniecki.qumi.api.model.common.PlaybackTrackResponse
import com.tryniecki.qumi.api.model.queue.PlaybackStateResponse
import com.tryniecki.qumi.api.model.queue.QueueEntryResponse
import com.tryniecki.qumi.api.model.queue.QueueSnapshotResponse
import com.tryniecki.qumi.api.model.radio.RadioStateResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackStateMergeTest {
    private fun track(id: String) = PlaybackTrackResponse(
        contentId = id,
        contentType = "YouTube",
        title = id,
        url = "https://example.test/$id",
        durationMilliseconds = 1_000,
        artworkUrl = null,
        playCount = 0,
    )

    private val snapshot = QueueSnapshotResponse(
        guildId = "guild",
        voiceChannelId = "voice",
        nowPlaying = track("old"),
        nowPlayingFromRadio = false,
        radio = RadioStateResponse(isEnabled = false),
        pendingEntries = listOf(QueueEntryResponse("entry", 1, track("next"))),
        pendingEntriesCount = 1,
        pendingDurationMilliseconds = 1_000,
        version = 1,
        queueVersion = 1,
    )

    private val state = PlaybackStateResponse(
        guildId = "guild",
        voiceChannelId = "other",
        nowPlaying = track("new"),
        nowPlayingFromRadio = true,
        radio = RadioStateResponse(isEnabled = true),
        pendingEntriesCount = 4,
        pendingDurationMilliseconds = 9_000,
        version = 2,
        queueVersion = 3,
        nowPlayingStartedAt = "2026-10-14T12:00:00Z",
        isRepeatEnabled = true,
        playbackInstanceId = "instance",
        playbackPositionMilliseconds = 500,
    )

    @Test fun `playback state leaves the pending list in place`() {
        assertSame(snapshot.pendingEntries, snapshot.withPlaybackState(state).pendingEntries)
    }

    @Test fun `playback state replaces every field it carries`() {
        val merged = snapshot.withPlaybackState(state)

        assertEquals("other", merged.voiceChannelId)
        assertEquals("new", merged.nowPlaying?.contentId)
        assertEquals(true, merged.nowPlayingFromRadio)
        assertEquals(true, merged.radio.isEnabled)
        assertEquals(4, merged.pendingEntriesCount)
        assertEquals(9_000L, merged.pendingDurationMilliseconds)
        assertEquals(2L, merged.version)
        assertEquals(3L, merged.queueVersion)
        assertEquals("2026-10-14T12:00:00Z", merged.nowPlayingStartedAt)
        assertEquals(true, merged.isRepeatEnabled)
        assertEquals("instance", merged.playbackInstanceId)
        assertEquals(500L, merged.playbackPositionMilliseconds)
    }

    @Test fun `an idle state empties the player but keeps the queue`() {
        val idle = state.copy(nowPlaying = null, playbackInstanceId = null, playbackPositionMilliseconds = null)
        val merged = snapshot.withPlaybackState(idle)

        assertEquals(null, merged.nowPlaying)
        assertEquals(1, merged.pendingEntries.size)
    }
}
