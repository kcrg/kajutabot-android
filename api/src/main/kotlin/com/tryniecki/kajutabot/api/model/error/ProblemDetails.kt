package com.tryniecki.kajutabot.api.model.error

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class KajutaBotProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val errorCode: String? = null,
    val currentVersion: Long? = null,
)

object KajutaBotProblemDetailsParser {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun parse(rawBody: String?): KajutaBotProblemDetails? {
        if (rawBody.isNullOrBlank()) return null
        return try {
            json.decodeFromString<KajutaBotProblemDetails>(rawBody)
        } catch (_: Exception) {
            null
        }
    }
}
