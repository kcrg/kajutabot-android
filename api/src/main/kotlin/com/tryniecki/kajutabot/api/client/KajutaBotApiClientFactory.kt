package com.tryniecki.kajutabot.api.client

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object KajutaBotApiClientFactory {
    const val API_KEY_HEADER = "X-KajutaBot-Api-Key"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /**
     * Creates the Retrofit API implementation.
     *
     * [apiKeyProvider] is intentionally injected instead of reading Android storage here so this
     * module stays independent from Android. Do not ship the Control API full-access key in the APK.
     */
    fun create(
        baseUrl: String,
        apiKeyProvider: () -> String? = { null },
    ): KajutaBotApi {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)

        val authInterceptor = Interceptor { chain ->
            val apiKey = apiKeyProvider()
            val request = if (apiKey.isNullOrBlank()) {
                chain.request()
            } else {
                chain.request()
                    .newBuilder()
                    .header(API_KEY_HEADER, apiKey)
                    .build()
            }

            chain.proceed(request)
        }

        val httpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        return Retrofit.Builder()
            .baseUrl(normalizedBaseUrl)
            .client(httpClient)
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(KajutaBotApi::class.java)
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val root = baseUrl.trim().trimEnd('/')
        require(root.isNotEmpty()) { "baseUrl cannot be blank." }
        return "$root/api/v1/"
    }
}
