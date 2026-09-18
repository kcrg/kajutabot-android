package com.tryniecki.kajutabot.api.client

import com.tryniecki.kajutabot.api.model.error.KajutaBotProblemDetailsParser
import retrofit2.HttpException

object KajutaBotApiErrors {
    const val QUEUE_VERSION_CONFLICT = "queue_version_conflict"

    private val invalidSessionCodes = setOf(
        "invalid_refresh_token",
        "expired_refresh_token",
        "revoked_session",
        "refresh_token_reuse_detected",
    )

    fun problemDetailsOf(throwable: Throwable?): com.tryniecki.kajutabot.api.model.error.KajutaBotProblemDetails? {
        val http = throwable as? HttpException ?: return null
        val raw = try {
            http.response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        return KajutaBotProblemDetailsParser.parse(raw)
    }

    fun errorCodeOf(throwable: Throwable?): String? = problemDetailsOf(throwable)?.errorCode

    fun currentVersionOf(throwable: Throwable?): Long? {
        // Note: errorBody can be consumed only once per HttpException instance.
        // If you need both errorCode and currentVersion, call problemDetailsOf() once.
        return problemDetailsOf(throwable)?.currentVersion
    }

    fun httpStatusOf(throwable: Throwable?): Int? =
        (throwable as? HttpException)?.code()

    fun isInvalidSessionCode(errorCode: String?): Boolean =
        errorCode != null && invalidSessionCodes.contains(errorCode)

    fun isInvalidSession(throwable: Throwable?): Boolean =
        isInvalidSessionCode(errorCodeOf(throwable))

    fun retryAfterSeconds(throwable: Throwable?): Long? {
        val http = throwable as? HttpException ?: return null
        if (http.code() != 429) return null
        val header = http.response()?.headers()?.get("Retry-After") ?: return null
        return header.trim().toLongOrNull()
    }
}
