package com.tryniecki.qumi.ui.jellyfin

import java.net.URLEncoder

private val timeSpanRegex = Regex("""^(?:(\d+)\.)?(\d{1,2}):(\d{2}):(\d{2})(?:\.\d+)?$""")

/** Parses a .NET TimeSpan, which is how the backend sends track durations. */
internal fun parseTimeSpanMillis(value: String?): Long? {
    val (days, hours, minutes, seconds) = timeSpanRegex.matchEntire(value?.trim().orEmpty())?.destructured
        ?: return null
    val totalSeconds = (days.toLongOrNull() ?: 0L) * 86_400L +
        hours.toLong() * 3_600L + minutes.toLong() * 60L + seconds.toLong()
    return totalSeconds * 1_000L
}

/** Goes through the artwork path that CoilSetup authorizes; Jellyfin artwork is private. */
internal fun jellyfinArtworkPath(artworkItemId: String?): String? = artworkItemId
    ?.takeIf { it.isNotBlank() }
    ?.let { "/api/v1/app/artwork/Jellyfin/" + URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20") }
