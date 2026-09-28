package com.tryniecki.kajutabot.image

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoilSetupTest {
    private val origin = "https://api.example.test/".toHttpUrl()

    @Test fun `canonical artwork receives app authorization`() {
        assertTrue(CoilSetup.shouldAuthorizeArtwork(
            "https://api.example.test/api/v1/app/artwork/YouTube/x".toHttpUrl(), origin,
        ))
    }

    @Test fun `public provider artwork never receives app authorization`() {
        assertFalse(CoilSetup.shouldAuthorizeArtwork(
            "https://i.ytimg.com/vi/x/hqdefault.jpg".toHttpUrl(), origin,
        ))
        assertFalse(CoilSetup.shouldAuthorizeArtwork(
            "https://api.example.test.evil.invalid/api/v1/app/artwork/x".toHttpUrl(), origin,
        ))
    }
}
