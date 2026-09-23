package com.tryniecki.kajutabot.api.model.favorites

import kotlinx.serialization.Serializable

@Serializable
data class FavoriteResponse(
    val contentUrl: String,
    val title: String,
    val addedAt: String,
    val thumbnailUrl: String? = null,
)

@Serializable
data class AddFavoriteRequest(
    val contentUrl: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
)

@Serializable
data class QueueFavoritesRequest(
    val guildId: String,
    val voiceChannelId: String,
    val expectedQueueVersion: Long? = null,
    val shuffle: Boolean = false,
)
