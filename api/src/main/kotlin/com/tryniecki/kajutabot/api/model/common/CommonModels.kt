package com.tryniecki.kajutabot.api.model.common

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
)

@Serializable
data class SearchTrackResponse(
    val contentId: String,
    val contentType: String,
    val title: String,
    val url: String,
    val durationMilliseconds: Long,
    val artworkUrl: String?,
)