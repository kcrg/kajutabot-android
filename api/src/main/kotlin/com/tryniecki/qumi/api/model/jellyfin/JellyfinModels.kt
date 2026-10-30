package com.tryniecki.qumi.api.model.jellyfin

import kotlinx.serialization.Serializable

@Serializable
data class StartJellyfinQuickConnectRequest(
    val serverUrl: String,
)

@Serializable
data class JellyfinQuickConnectStartResponse(
    val attemptId: String,
    val code: String,
    val expiresAtUtc: String,
)

@Serializable
data class JellyfinQuickConnectStatusResponse(
    val status: String,
    val connection: JellyfinConnectionResponse? = null,
    val message: String? = null,
)

@Serializable
data class JellyfinConnectionResponse(
    val serverUrl: String,
    val serverName: String,
    val jellyfinUserName: String,
    val createdAtUtc: String,
    val lastValidatedAtUtc: String? = null,
)

@Serializable
data class JellyfinAudioItemResponse(
    val itemId: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    /** A .NET TimeSpan: `[d.]hh:mm:ss[.fffffff]`. */
    val duration: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val artworkItemId: String? = null,
)

@Serializable
data class JellyfinAudioPageResponse(
    val totalCount: Int,
    val items: List<JellyfinAudioItemResponse>,
)
