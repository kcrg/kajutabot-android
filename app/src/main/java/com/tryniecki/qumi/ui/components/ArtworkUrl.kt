package com.tryniecki.qumi.ui.components

import com.tryniecki.qumi.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Backend artwork URL normalization.
 *
 * Public provider URLs are used as-is. Root-relative URLs returned by Qumi API are
 * resolved against the configured API origin. There are intentionally no provider-specific
 * fallbacks or URL guessing.
 */
sealed interface ArtworkSource {
    data class Remote(val url: String) : ArtworkSource
    data object Missing : ArtworkSource
}

private val defaultApiRoot = BuildConfig.API_BASE_URL.trim().trimEnd('/').toHttpUrlOrNull()

fun resolveArtworkUrl(
    imageUrl: String?,
    apiBaseUrl: String = BuildConfig.API_BASE_URL,
): String? {
    val value = imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val apiRoot = if (apiBaseUrl == BuildConfig.API_BASE_URL) defaultApiRoot
        else apiBaseUrl.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
    if (apiRoot == null) return null

    // OkHttp normalizes https:///path into a host; reject the malformed authority first.
    if (value.startsWith("http://") || value.startsWith("https://")) {
        val authority = value.substringAfter("://").substringBefore('/')
        if (authority.isBlank()) return null
    }

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

fun resolveArtworkSource(imageUrl: String?): ArtworkSource =
    resolveArtworkUrl(imageUrl)?.let(ArtworkSource::Remote) ?: ArtworkSource.Missing
