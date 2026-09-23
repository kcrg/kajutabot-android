package com.tryniecki.kajutabot.ui.favorites

import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteIdentityTest {
    @Test
    fun `YouTube link variants and direct locator identify the same track`() {
        assertEquals("youtube:abc", favoriteIdentity("yt:abc"))
        assertEquals("youtube:abc", favoriteIdentity("https://youtu.be/abc?t=12"))
        assertEquals("youtube:abc", favoriteIdentity("https://www.youtube.com/watch?v=abc&list=one"))
        assertEquals("youtube:abc", favoriteIdentity("https://m.youtube.com/shorts/abc"))
    }

    @Test
    fun `SoundCloud track accepts URL and direct ID favorites`() {
        val track = PlaybackTrackResponse(
            contentId = "42",
            contentType = "SoundCloud",
            title = "Track",
            url = "https://soundcloud.com/Artist/Track",
            durationMilliseconds = 10_000,
            artworkUrl = null,
            playCount = 0,
        )
        assertTrue(favoriteIdentity("sc:42") in track.favoriteIdentities())
        assertTrue(favoriteIdentity("https://www.soundcloud.com/artist/track") in track.favoriteIdentities())
    }
}
