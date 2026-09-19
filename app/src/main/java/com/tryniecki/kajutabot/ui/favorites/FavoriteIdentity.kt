package com.tryniecki.kajutabot.ui.favorites

import com.tryniecki.kajutabot.api.model.common.TrackResponse
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Matches the web client's YouTube/SoundCloud identities across links and direct locators. */
internal fun favoriteIdentity(value: String?): String {
    val input = value?.trim().orEmpty()
    if (input.isEmpty()) return ""
    if (input.startsWith("yt:", true) && input.length > 3) return "youtube:${input.substring(3).trim()}"
    if (input.startsWith("sc:", true) && input.length > 3) return "soundcloud-id:${input.substring(3).trim()}"
    val uri = runCatching { URI(input) }.getOrNull() ?: return input
    val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.")?.removePrefix("m.") ?: return input
    val path = uri.path.orEmpty().trim('/')
    if (host in setOf("youtube.com", "music.youtube.com", "youtu.be")) {
        val id = when {
            host == "youtu.be" -> path.substringBefore('/')
            path.startsWith("shorts/") || path.startsWith("embed/") || path.startsWith("live/") ->
                path.substringAfter('/').substringBefore('/')
            else -> uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=", ignoreCase = true) }
                ?.substringAfter('=')?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        }
        if (!id.isNullOrBlank()) return "youtube:$id"
    }
    if (host == "soundcloud.com") return "soundcloud-url:${path.lowercase(Locale.ROOT)}"
    return input
}

internal fun TrackResponse.favoriteIdentities(): Set<String> = buildSet {
    add(favoriteIdentity(url))
    when (contentType.lowercase(Locale.ROOT)) {
        "youtube" -> add(favoriteIdentity("yt:$contentId"))
        "soundcloud" -> add(favoriteIdentity("sc:$contentId"))
    }
}
