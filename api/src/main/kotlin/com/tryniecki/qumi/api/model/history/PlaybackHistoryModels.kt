package com.tryniecki.qumi.api.model.history

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackHistoryEntryResponse(
    val id: Long,
    val contentType: String,
    val contentId: String,
    val title: String,
    val queueInput: String,
    val startedAtUtc: String,
    val artworkUrl: String? = null,
    val artworkThumbnailUrl: String? = null,
    val durationMilliseconds: Long? = null,
) {
    val listArtworkUrl: String? get() = artworkThumbnailUrl ?: artworkUrl
}

@Serializable
data class PlaybackHistoryPageResponse(
    val items: List<PlaybackHistoryEntryResponse>,
    val nextCursor: Long? = null,
)
