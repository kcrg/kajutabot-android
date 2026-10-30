package com.tryniecki.qumi.ui

import com.tryniecki.qumi.R
import com.tryniecki.qumi.ui.text.UiText
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException

class ErrorMessagesTest {
    @Test
    fun `ownership and idempotency errors have specific localized messages`() {
        val cases = listOf(
            Triple(403, "voice_channel_access_denied", R.string.error_voice_channel_access),
            Triple(409, "voice_channel_changed", R.string.error_voice_channel_changed),
            Triple(503, "worker_ownership_lost", R.string.error_server_unavailable),
            Triple(503, "worker_not_ready", R.string.error_server_unavailable),
            Triple(409, "idempotency_in_progress", R.string.error_operation_in_progress),
            Triple(409, "idempotency_key_conflict", R.string.error_operation_key_conflict),
        )
        cases.forEach { (status, code, message) ->
            val error = HttpException(Response.error<Any>(
                status,
                """{"errorCode":"$code"}""".toResponseBody("application/problem+json".toMediaType()),
            ))
            assertEquals(code, UiText.Resource(message), userMessageForError(error))
        }
    }

    @Test
    fun `queue full is distinct from queue version conflict`() {
        val error = HttpException(Response.error<Any>(
            409,
            """{"errorCode":"queue_full"}""".toResponseBody("application/problem+json".toMediaType()),
        ))
        assertEquals(UiText.Resource(R.string.error_queue_full), userMessageForError(error))
    }

    @Test
    fun `rate limit and timeout have distinct messages`() {
        val rateLimit = HttpException(Response.error<Any>(
            429,
            """{"errorCode":"rate_limit_exceeded"}""".toResponseBody("application/problem+json".toMediaType()),
        ))
        assertEquals(UiText.Resource(R.string.error_rate_limit_wait), userMessageForError(rateLimit))
        assertEquals(UiText.Resource(R.string.error_timeout), userMessageForError(SocketTimeoutException()))
    }
}
