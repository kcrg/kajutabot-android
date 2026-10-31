package com.tryniecki.qumi.api.client

import com.tryniecki.qumi.api.model.favorites.AddFavoriteRequest
import com.tryniecki.qumi.api.model.favorites.QueueFavoritesRequest
import com.tryniecki.qumi.api.model.queue.EnqueueRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

class ApiClientFactoryTest {
    private val readTimeouts = mutableListOf<Int>()

    private val api: ControlApi = Retrofit.Builder()
        .baseUrl("https://example.test/")
        .client(
            ApiClientFactory.baseHttpClient.newBuilder()
                .addInterceptor(ApiClientFactory.slowRequestInterceptor)
                .addInterceptor(Interceptor { chain ->
                    readTimeouts += chain.readTimeoutMillis()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(500)
                        .message("stop")
                        .body("".toResponseBody())
                        .build()
                })
                .build(),
        )
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create()

    private fun readTimeoutOf(call: suspend ControlApi.() -> Unit): Int = runBlocking {
        runCatching { api.call() }
        readTimeouts.removeLast()
    }

    @Test fun `ordinary calls use the shared read limit`() {
        assertEquals(15_000, readTimeoutOf { getFavorites() })
    }

    @Test fun `calls that resolve content get the longer read limit`() {
        val request = EnqueueRequest("voice", expectedQueueVersion = 1, contentType = "YouTube", contentId = "a")
        assertEquals(25_000, readTimeoutOf { enqueue("guild", request, "key") })
        assertEquals(25_000, readTimeoutOf { addFavorite(AddFavoriteRequest("YouTube", "a")) })
        assertEquals(25_000, readTimeoutOf { queueFavorites(QueueFavoritesRequest("guild", "voice"), "key") })
    }

    @Test fun `artwork cannot starve API calls of the per host limit`() {
        val client: OkHttpClient = ApiClientFactory.baseHttpClient
        assertEquals(20, client.dispatcher.maxRequestsPerHost)
    }
}
