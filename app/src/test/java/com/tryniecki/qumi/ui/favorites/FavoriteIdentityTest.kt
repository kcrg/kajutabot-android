package com.tryniecki.qumi.ui.favorites

import com.tryniecki.qumi.api.model.common.PlaybackTrackResponse
import com.tryniecki.qumi.api.model.favorites.FavoriteResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteIdentityTest {
    private fun favorite(type: String, id: String) = FavoriteResponse(
        contentUrl = "https://example.test/any",
        title = "Track",
        addedAt = "2026-10-14T12:00:00Z",
        contentType = type,
        contentId = id,
    )

    private fun track(type: String, id: String) = PlaybackTrackResponse(
        contentId = id,
        contentType = type,
        title = "Track",
        url = "https://example.test/other",
        durationMilliseconds = 10_000,
        artworkUrl = null,
        playCount = 0,
    )

    @Test fun `a favorite matches its track whatever the links look like`() {
        assertEquals(track("YouTube", "abc").favoriteIdentity, favorite("YouTube", "abc").favoriteIdentity)
    }

    @Test fun `the same id from another source is another track`() {
        assertNotEquals(track("SoundCloud", "42").favoriteIdentity, favorite("YouTube", "42").favoriteIdentity)
    }

    @Test fun `only Jellyfin tracks have no favorites`() {
        assertFalse(track("Jellyfin", "a").supportsFavorites)
        assertTrue(track("YouTube", "a").supportsFavorites)
        assertTrue(track("SoundCloud", "a").supportsFavorites)
    }
}
