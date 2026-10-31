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
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val durationMilliseconds: Long? = null,
    val artworkUrl: String? = null,
    val artworkThumbnailUrl: String? = null,
)

@Serializable
data class JellyfinAudioPageResponse(
    val totalCount: Int,
    val items: List<JellyfinAudioItemResponse>,
)

@Serializable
data class JellyfinQuickConnectEvent(
    val attemptId: String,
    val status: String,
    val connection: JellyfinConnectionResponse? = null,
    val message: String? = null,
)

/** The server fields are absent when [connected] is false. */
@Serializable
data class JellyfinConnectionStateResponse(
    val connected: Boolean = true,
    val serverUrl: String? = null,
    val serverName: String? = null,
    val jellyfinUserName: String? = null,
    val createdAtUtc: String? = null,
    val lastValidatedAtUtc: String? = null,
) {
    fun connectionOrNull(): JellyfinConnectionResponse? {
        if (!connected) return null
        return JellyfinConnectionResponse(
            serverUrl = serverUrl ?: return null,
            serverName = serverName ?: return null,
            jellyfinUserName = jellyfinUserName ?: return null,
            createdAtUtc = createdAtUtc ?: return null,
            lastValidatedAtUtc = lastValidatedAtUtc,
        )
    }
}
