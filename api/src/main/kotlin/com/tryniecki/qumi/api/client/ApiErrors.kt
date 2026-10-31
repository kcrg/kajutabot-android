package com.tryniecki.qumi.api.client

import com.tryniecki.qumi.api.model.error.ProblemDetailsParser
import com.tryniecki.qumi.api.model.error.ProblemDetails
import com.tryniecki.qumi.api.model.queue.QueueSnapshotResponse
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.WeakHashMap
import retrofit2.HttpException

object ApiErrors {
    const val QUEUE_VERSION_CONFLICT = "queue_version_conflict"

    private val invalidSessionCodes = setOf(
        "invalid_refresh_token",
        "expired_refresh_token",
        "revoked_session",
        "refresh_token_reuse_detected",
    )

    // An error body can be read only once, and a failed mutation is inspected at several levels.
    private val parsedProblems = WeakHashMap<HttpException, ProblemDetails?>()

    fun problemDetailsOf(throwable: Throwable?): ProblemDetails? {
        val http = throwable as? HttpException ?: return null
        synchronized(parsedProblems) {
            if (http in parsedProblems) return parsedProblems[http]
            val raw = try {
                http.response()?.errorBody()?.string()
            } catch (_: Exception) {
                null
            }
            return ProblemDetailsParser.parse(raw).also { parsedProblems[http] = it }
        }
    }

    /** The current queue a conflict response can carry, which saves fetching it. */
    fun conflictSnapshotOf(problem: ProblemDetails?): QueueSnapshotResponse? {
        if (problem?.errorCode != QUEUE_VERSION_CONFLICT) return null
        val snapshot = problem.snapshot ?: return null
        return runCatching { ApiJson.decodeFromJsonElement(QueueSnapshotResponse.serializer(), snapshot) }.getOrNull()
    }

    fun errorCodeOf(throwable: Throwable?): String? = problemDetailsOf(throwable)?.errorCode

    fun currentQueueVersionOf(throwable: Throwable?): Long? {
        return problemDetailsOf(throwable)?.currentQueueVersion
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
        return header.trim().toLongOrNull()?.coerceAtLeast(0) ?: runCatching {
            val deadline = ZonedDateTime.parse(header.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            ChronoUnit.SECONDS.between(Instant.now(), deadline).coerceAtLeast(0)
        }.getOrNull()
    }
}
