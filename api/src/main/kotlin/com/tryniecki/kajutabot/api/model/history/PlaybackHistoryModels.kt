package com.tryniecki.kajutabot.api.model.history

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackHistoryEntryResponse(
    val id: Long,
    val contentType: String,
    val contentId: String,
    val title: String,
    val queueInput: String,
    val startedAtUtc: String,
    val thumbnailUrl: String? = null,
)

@Serializable
data class PlaybackHistoryPageResponse(
    val items: List<PlaybackHistoryEntryResponse>,
    val nextCursor: Long? = null,
)

@Serializable
data class EnqueueJellyfinTrackRequest(
    val guildId: String,
    val voiceChannelId: String,
    val expectedQueueVersion: Long? = null,
)
