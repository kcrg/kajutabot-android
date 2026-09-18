package com.tryniecki.kajutabot.api.model.common

import kotlinx.serialization.Serializable

@Serializable
data class TrackResponse(
    val contentId: String,
    val contentType: String,
    val title: String,
    val url: String,
    val durationMilliseconds: Long,
    val thumbnailUrl: String?,
    val playCount: Long,
    val cachedAt: String?,
    val lastPlayedAt: String?,
    val hasCachedThumbnail: Boolean = false,
    val artworkReference: String? = null,
    val artworkAccentColor: String? = null,
    val thumbnailVersion: String? = null,
)

@Serializable
data class ApiOperationResponse(
    val succeeded: Boolean,
    val errorCode: String? = null,
    val message: String? = null,
    val version: Long? = null,
)

@Serializable
data class HealthResponse(
    val isHealthy: Boolean,
    val isCacheInitialized: Boolean,
    val isDiscordConnected: Boolean,
    val guildCount: Int,
    val startedAt: String,
    val applicationVersion: String? = null,
    val workerId: String? = null,
    val mainWorker: Boolean? = null,
    val totalShardCount: Int? = null,
    val shardIds: List<Int>? = null,
)
