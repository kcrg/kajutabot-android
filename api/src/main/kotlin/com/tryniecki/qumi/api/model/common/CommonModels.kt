package com.tryniecki.qumi.api.model.common

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackTrackResponse(
    val contentId: String,
    val contentType: String,
    val title: String,
    val url: String,
    val durationMilliseconds: Long,
    val artworkUrl: String?,
    val playCount: Long,
    val artworkAccentColor: String? = null,
    val artworkThumbnailUrl: String? = null,
) {
    val listArtworkUrl: String? get() = artworkThumbnailUrl ?: artworkUrl
}

@Serializable
data class SearchTrackResponse(
    val contentId: String,
    val contentType: String,
    val title: String,
    val url: String,
    val durationMilliseconds: Long,
    val artworkUrl: String?,
    val artworkThumbnailUrl: String? = null,
) {
    val listArtworkUrl: String? get() = artworkThumbnailUrl ?: artworkUrl
}
