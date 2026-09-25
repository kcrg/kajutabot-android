package com.tryniecki.kajutabot.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkUrlTest {
    private val apiBaseUrl = "https://api.example.com"

    @Test
    fun `valid https url is passed through verbatim`() {
        val url = "https://cdn.example.com/art/abc123.jpg?x=1"
        val source = resolveArtworkSource(url)
        assertTrue(source is ArtworkSource.Remote)
        assertEquals(url, (source as ArtworkSource.Remote).url)
    }

    @Test
    fun `root relative backend artwork is resolved against api origin`() {
        assertEquals(
            "https://api.example.com/api/v1/app/artwork/YouTube/video-id",
            resolveArtworkUrl(
                "/api/v1/app/artwork/YouTube/video-id",
                apiBaseUrl,
            ),
        )
    }

    @Test
    fun `protocol relative artwork inherits api scheme`() {
        assertEquals(
            "https://cdn.example.com/art/video-id.jpg",
            resolveArtworkUrl("//cdn.example.com/art/video-id.jpg", apiBaseUrl),
        )
    }

    @Test
    fun `valid http url is loadable`() {
        assertTrue(isSupportedArtworkUrl("http://cdn.example.com/a.png"))
    }

    @Test
    fun `null url is missing`() {
        assertEquals(ArtworkSource.Missing, resolveArtworkSource(null))
    }

    @Test
    fun `blank url is missing`() {
        assertEquals(ArtworkSource.Missing, resolveArtworkSource(""))
        assertEquals(ArtworkSource.Missing, resolveArtworkSource("   "))
        assertFalse(isSupportedArtworkUrl("   "))
    }

    @Test
    fun `malformed url is missing`() {
        assertEquals(ArtworkSource.Missing, resolveArtworkSource("not a url at all"))
        assertEquals(ArtworkSource.Missing, resolveArtworkSource("::::"))
    }

    @Test
    fun `non-http scheme is missing`() {
        assertEquals(ArtworkSource.Missing, resolveArtworkSource("ftp://cdn.example.com/a.jpg"))
        assertEquals(ArtworkSource.Missing, resolveArtworkSource("data:image/png;base64,AAA"))
        assertFalse(isSupportedArtworkUrl("content://media/1"))
    }

    @Test
    fun `url without host is missing`() {
        assertEquals(ArtworkSource.Missing, resolveArtworkSource("https:///no-host.jpg"))
    }
}
