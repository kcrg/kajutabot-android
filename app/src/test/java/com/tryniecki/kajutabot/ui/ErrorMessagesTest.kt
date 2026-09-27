package com.tryniecki.kajutabot.ui

import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.ui.text.UiText
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.SocketTimeoutException

class ErrorMessagesTest {
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
