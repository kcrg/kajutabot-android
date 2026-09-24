package com.tryniecki.kajutabot.ui.components

import com.tryniecki.kajutabot.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Backend artwork URL normalization.
 *
 * Public provider URLs are used as-is. Root-relative URLs returned by KajutaBot API are
 * resolved against the configured API origin. There are intentionally no provider-specific
 * fallbacks or URL guessing.
 */
sealed interface ArtworkSource {
    data class Remote(val url: String) : ArtworkSource
    data object Missing : ArtworkSource
}

fun resolveArtworkUrl(
    imageUrl: String?,
    apiBaseUrl: String = BuildConfig.KAJUTABOT_API_BASE_URL,
): String? {
    val value = imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val apiRoot = apiBaseUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: return null

    if (value.startsWith("//")) {
        return "${apiRoot.scheme}:$value".toHttpUrlOrNull()?.toString()
    }

    value.toHttpUrlOrNull()?.let { absolute ->
        if (absolute.scheme == "http" || absolute.scheme == "https") {
            return absolute.toString()
        }
    }

    if (!value.startsWith('/')) return null
    return apiRoot.resolve(value)?.toString()
}

fun isSupportedArtworkUrl(url: String?): Boolean = resolveArtworkUrl(url) != null

fun resolveArtworkSource(imageUrl: String?): ArtworkSource =
    resolveArtworkUrl(imageUrl)?.let(ArtworkSource::Remote) ?: ArtworkSource.Missing
