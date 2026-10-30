package com.tryniecki.qumi.api.client

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

@OptIn(ExperimentalSerializationApi::class)
internal val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    exceptionsWithDebugInfo = false
}

object ApiClientFactory {
    const val AUTHORIZATION_HEADER = "Authorization"
    val baseHttpClient: OkHttpClient = OkHttpClient.Builder().build()

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
