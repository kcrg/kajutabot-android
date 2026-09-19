package com.tryniecki.kajutabot.ui.favorites

private val youtubeVideoId = Regex("[A-Za-z0-9_-]{11}")

/** Older favorites may have no saved thumbnail even though their YouTube URL contains a video ID. */
internal fun favoriteArtworkFallbackUrl(contentUrl: String): String? {
    val identity = favoriteIdentity(contentUrl)
    if (!identity.startsWith("youtube:")) return null
    val videoId = identity.removePrefix("youtube:")
    if (!youtubeVideoId.matches(videoId)) return null
    return "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
}
