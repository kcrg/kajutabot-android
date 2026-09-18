package com.tryniecki.kajutabot.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class PkceData(
    val verifier: String,
    val challenge: String,
    val state: String,
)

class PkceGenerator(
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    fun generate(): PkceData {
        val verifier = generateVerifier()
        return PkceData(
            verifier = verifier,
            challenge = challengeForVerifier(verifier),
            state = generateState(),
        )
    }

    fun generateVerifier(): String {
        val bytes = ByteArray(64)
        secureRandom.nextBytes(bytes)
        // 64 bytes -> 86 chars Base64Url no padding, within 43..128
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun generateState(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        fun challengeForVerifier(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(verifier.toByteArray(Charsets.UTF_8))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
        }
    }
}
