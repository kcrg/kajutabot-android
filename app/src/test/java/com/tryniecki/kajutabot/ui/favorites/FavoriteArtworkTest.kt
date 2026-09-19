package com.tryniecki.kajutabot.ui.favorites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteArtworkTest {
    @Test
    fun `builds artwork for YouTube favorite without saved thumbnail`() {
        val expected = "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg"
        assertEquals(expected, favoriteArtworkFallbackUrl("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(expected, favoriteArtworkFallbackUrl("https://youtu.be/dQw4w9WgXcQ?t=42"))
        assertEquals(expected, favoriteArtworkFallbackUrl("yt:dQw4w9WgXcQ"))
    }

    @Test
    fun `does not invent artwork for unrelated or malformed favorites`() {
        assertNull(favoriteArtworkFallbackUrl("https://soundcloud.com/artist/track"))
        assertNull(favoriteArtworkFallbackUrl("https://youtube.com.evil.test/watch?v=dQw4w9WgXcQ"))
        assertNull(favoriteArtworkFallbackUrl("https://youtube.com/watch?v=bad"))
    }
}
