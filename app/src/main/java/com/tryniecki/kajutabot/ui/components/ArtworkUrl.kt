package com.tryniecki.kajutabot.ui.components

import java.net.URI

/**
 * Backend artwork URL validation.
 *
 * The backend is the sole source of artwork: a URL is used verbatim or not at all.
 * There are intentionally no client-side fallbacks (no YouTube thumbnails, no CDN
 * guessing, no provider parsing). Only obviously unusable values are rejected and
 * surfaced as a broken-artwork state so backend data gaps stay visible.
 */
sealed interface ArtworkSource {
    data class Remote(val url: String) : ArtworkSource
    data object Missing : ArtworkSource
}

fun isSupportedArtworkUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    return try {
        val uri = URI(url.trim())
        val scheme = uri.scheme?.lowercase()
        (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }
}

fun resolveArtworkSource(imageUrl: String?): ArtworkSource {
    if (!isSupportedArtworkUrl(imageUrl)) return ArtworkSource.Missing
    return ArtworkSource.Remote(imageUrl!!.trim())
}
