package com.tryniecki.kajutabot.media

import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.ui.player.NowPlayingPresentation
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@androidx.annotation.OptIn(UnstableApi::class)
class RemoteQueuePlayerTest {
    @Test
    fun `track change updates metadata within the existing timeline item`() {
        val player = RemoteQueuePlayer {}
        player.update(NowPlayingPresentation(track("first", "First"), "play-1", 10_000, true))
        val originalTimelineUid = player.currentTimeline.getWindow(0, Timeline.Window()).uid
        val originalMediaId = player.currentMediaItem?.mediaId

        player.update(NowPlayingPresentation(track("second", "Second"), "play-2", 0, true))

        assertEquals(Player.STATE_READY, player.playbackState)
        assertEquals(1, player.currentTimeline.windowCount)
        assertEquals(originalTimelineUid, player.currentTimeline.getWindow(0, Timeline.Window()).uid)
        assertNotEquals(originalMediaId, player.currentMediaItem?.mediaId)
        assertEquals("Second", player.mediaMetadata.title)
        player.release()
    }

    @Test
    fun `loaded artwork belongs only to the current track`() {
        val player = RemoteQueuePlayer {}
        player.update(NowPlayingPresentation(track("first", "First", "https://example.com/first.jpg"), "play-1", 0, true))
        val artwork = byteArrayOf(1, 2, 3)
        player.updateArtwork("https://example.com/first.jpg", artwork)
        assertArrayEquals(artwork, player.mediaMetadata.artworkData)

        player.update(NowPlayingPresentation(track("second", "Second", "https://example.com/second.jpg"), "play-2", 0, true))
        player.updateArtwork("https://example.com/first.jpg", artwork)
        assertEquals(null, player.mediaMetadata.artworkData)
        player.release()
    }

    @Test
    fun `repeat restart changes media id and resets position without replacing timeline item`() {
        val player = RemoteQueuePlayer {}
        val repeatedTrack = track("first", "First")
        player.update(NowPlayingPresentation(repeatedTrack, "play-1", 110_000, true))
        val timelineUid = player.currentTimeline.getWindow(0, Timeline.Window()).uid
        val previousMediaId = player.currentMediaItem?.mediaId

        player.update(NowPlayingPresentation(repeatedTrack, "play-2", 0, true))

        assertEquals(timelineUid, player.currentTimeline.getWindow(0, Timeline.Window()).uid)
        assertNotEquals(previousMediaId, player.currentMediaItem?.mediaId)
        assertEquals(0L, player.currentPosition)
        player.release()
    }

    @Test
    fun `preparing track is buffering until backend reports playback start`() {
        val player = RemoteQueuePlayer {}
        val current = track("first", "First")
        player.update(NowPlayingPresentation(current, "play-1", 0, false))
        assertEquals(Player.STATE_BUFFERING, player.playbackState)

        player.update(NowPlayingPresentation(current, "play-1", 0, true))
        assertEquals(Player.STATE_READY, player.playbackState)
        player.release()
    }

    private fun track(id: String, title: String, artworkUrl: String? = null) = PlaybackTrackResponse(
        contentType = "YouTube",
        contentId = id,
        title = title,
        url = "https://example.com/watch?v=$id",
        durationMilliseconds = 180_000,
        artworkUrl = artworkUrl,
        playCount = 0,
    )
}
