package com.tryniecki.kajutabot.api.client

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create

internal val KajutaBotJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

object KajutaBotApiClientFactory {
    const val AUTHORIZATION_HEADER = "Authorization"

    /**
     * Creates the authenticated user API. Adds `Authorization: Bearer <token>` when available.
     *
     * [accessTokenProvider] is a plain lambda so this module stays independent from Android.
     */
    fun create(
        baseUrl: String,
        accessTokenProvider: () -> String? = { null },
    ): KajutaBotApi {
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

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(bearerInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(httpClient)
            .addConverterFactory(
                KajutaBotJson.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create<KajutaBotApi>()
    }

    /**
     * Creates the anonymous auth API (exchange + refresh) without any Bearer interceptor,
     * so refresh can never attach a stale token or recurse.
     */
    fun createAuth(baseUrl: String): KajutaBotAuthApi {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val httpClient = OkHttpClient.Builder().build()

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(httpClient)
            .addConverterFactory(
                KajutaBotJson.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create<KajutaBotAuthApi>()
    }

    internal fun normalizeBaseUrl(baseUrl: String): String {
        val root = baseUrl.trim().trimEnd('/')
        require(root.isNotEmpty()) { "baseUrl cannot be blank." }
        return "$root/api/v1/"
    }
}
