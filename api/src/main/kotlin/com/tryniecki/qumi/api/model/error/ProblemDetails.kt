package com.tryniecki.qumi.api.model.error

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ProblemDetails(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
    val errorCode: String? = null,
    val currentQueueVersion: Long? = null,
)

object ProblemDetailsParser {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun parse(rawBody: String?): ProblemDetails? {
        if (rawBody.isNullOrBlank()) return null
        return try {
            json.decodeFromString<ProblemDetails>(rawBody)
        } catch (_: Exception) {
            null
        }
    }
}
