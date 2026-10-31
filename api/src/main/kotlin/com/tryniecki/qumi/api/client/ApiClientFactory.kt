package com.tryniecki.qumi.api.client

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Invocation
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalSerializationApi::class)
internal val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    exceptionsWithDebugInfo = false
}

/** Marks a call the server may spend longer on, such as resolving what it adds to the queue. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class SlowRequest

object ApiClientFactory {
    const val AUTHORIZATION_HEADER = "Authorization"
    // Each read limit stays above the server's own deadline, so that the server reports a timeout
    // and the app never shows a failure for an operation that later succeeds.
    internal const val READ_TIMEOUT_SECONDS = 15L
    internal const val SLOW_READ_TIMEOUT_SECONDS = 25L
    internal const val MAX_REQUESTS_PER_HOST = 20

    val baseHttpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // API calls and artwork share this limit, and HTTP/2 does not lift it.
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
        .build()

    internal val slowRequestInterceptor = Interceptor { chain ->
        val request = chain.request()
        val slow = request.tag(Invocation::class.java)?.method()?.isAnnotationPresent(SlowRequest::class.java) == true
        if (slow) {
            chain.withReadTimeout(SLOW_READ_TIMEOUT_SECONDS.toInt(), TimeUnit.SECONDS).proceed(request)
        } else {
            chain.proceed(request)
        }
    }

    /**
     * Creates the authenticated user API. Adds `Authorization: Bearer <token>` when available.
     *
     * [accessTokenProvider] is a plain lambda so this module stays independent from Android.
     */
    fun create(
        baseUrl: String,
        accessTokenProvider: () -> String? = { null },
    ): ControlApi {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)

        val bearerInterceptor = Interceptor { chain ->
            val token = accessTokenProvider()?.takeIf { it.isNotBlank() }
            val request = if (token == null) {
                chain.request()
            } else {
                chain.request()
                    .newBuilder()
                    .header(AUTHORIZATION_HEADER, "Bearer $token")
                    .build()
            }
            chain.proceed(request)
        }

        val httpClient = baseHttpClient.newBuilder()
            .addInterceptor(bearerInterceptor)
            .addInterceptor(slowRequestInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(httpClient)
            .addConverterFactory(
                ApiJson.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create<ControlApi>()
    }

    /**
     * Creates the anonymous auth API (exchange + refresh) without any Bearer interceptor,
     * so refresh can never attach a stale token or recurse.
     */
    fun createAuth(baseUrl: String): AuthApi {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val httpClient = baseHttpClient

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(httpClient)
            .addConverterFactory(
                ApiJson.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create<AuthApi>()
    }

    internal fun normalizeBaseUrl(baseUrl: String): String {
        val root = baseUrl.trim().trimEnd('/')
        require(root.isNotEmpty()) { "baseUrl cannot be blank." }
        return "$root/api/v1/app/"
    }
}
