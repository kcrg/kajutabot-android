package com.tryniecki.kajutabot.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceGeneratorTest {
    private val generator = PkceGenerator()

    @Test
    fun `verifier length within 43 to 128`() {
        repeat(20) {
            val verifier = generator.generateVerifier()
            assertTrue(verifier.length in 43..128)
        }
    }

    @Test
    fun `verifier is Base64Url without padding`() {
        repeat(20) {
            val verifier = generator.generateVerifier()
            assertFalse(verifier.contains("="))
            assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        }
    }

    @Test
    fun `challenge is SHA256 Base64Url without padding`() {
        val verifier = generator.generateVerifier()
        val challenge = PkceGenerator.challengeForVerifier(verifier)
        assertFalse(challenge.contains("="))
        assertTrue(challenge.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        // SHA-256 -> 32 bytes -> 43 chars Base64Url no padding
        assertEquals(43, challenge.length)
    }

    @Test
    fun `known RFC7636 vector matches`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            PkceGenerator.challengeForVerifier(verifier),
        )
    }

    @Test
    fun `state is non-empty Base64Url`() {
        repeat(20) {
            val state = generator.generateState()
            assertTrue(state.isNotBlank())
            assertFalse(state.contains("="))
            assertTrue(state.length >= 43)
        }
    }
}
