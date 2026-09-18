package com.tryniecki.kajutabot.image

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

/**
 * Shared Coil [ImageLoader] for backend-provided artwork.
 *
 * Uses OkHttp networking with Coil's default memory + disk caches.
 * Artwork is loaded directly in UI via AsyncImage; ViewModels never fetch images.
 * URLs always come verbatim from user-facing backend DTOs — no client-side fallbacks.
 */
object CoilSetup {
    fun init(context: Context) {
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() }))
                }
                .build()
        }
    }
}
