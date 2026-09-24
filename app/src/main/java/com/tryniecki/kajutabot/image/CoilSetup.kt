package com.tryniecki.kajutabot.image

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.tryniecki.kajutabot.auth.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Shared Coil [ImageLoader] for backend-provided artwork.
 *
 * Public provider artwork is fetched without KajutaBot credentials. Requests to the protected
 * KajutaBot artwork endpoint receive the current Bearer token, including a refresh when needed.
 */
object CoilSetup {
    private const val APP_ARTWORK_PATH_PREFIX = "/api/v1/app/artwork/"

    fun init(
        context: Context,
        apiBaseUrl: String,
        sessionManager: SessionManager,
    ) {
        val apiOrigin = apiBaseUrl.trim().trimEnd('/').toHttpUrlOrNull()
        val httpClient = OkHttpClient.Builder()
            .addInterceptor(artworkAuthorizationInterceptor(apiOrigin, sessionManager))
            .build()

        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { httpClient }))
                }
                .build()
        }
    }

    private fun artworkAuthorizationInterceptor(
        apiOrigin: HttpUrl?,
        sessionManager: SessionManager,
    ) = Interceptor { chain ->
        val original = chain.request()
        val url = original.url
        if (apiOrigin == null ||
            !url.sameOrigin(apiOrigin) ||
            !url.encodedPath.startsWith(APP_ARTWORK_PATH_PREFIX)
        ) {
            return@Interceptor chain.proceed(original)
        }

        val token = runBlocking {
            val identity = sessionManager.sessionIdentity.value ?: return@runBlocking null
            runCatching { sessionManager.accessTokenForSession(identity) }.getOrNull()
        }

        val request = token
            ?.takeIf { it.isNotBlank() }
            ?.let {
                original.newBuilder()
                    .header("Authorization", "Bearer $it")
                    .build()
            }
            ?: original

        chain.proceed(request)
    }

    private fun HttpUrl.sameOrigin(other: HttpUrl): Boolean =
        scheme == other.scheme && host == other.host && port == other.port
}
