package com.tryniecki.kajutabot.api.model.discord

import kotlinx.serialization.Serializable

@Serializable
data class DiscordGuildResponse(
    val id: String,
    val name: String,
    val iconUrl: String?,
    val isAvailable: Boolean,
)

@Serializable
data class DiscordVoiceChannelResponse(
    val id: String,
    val name: String,
    val position: Int,
    val userCount: Int,
    val isConnected: Boolean,
)

@Serializable
data class DiscordStatusResponse(
    val isConnected: Boolean,
    val startedAt: String?,
    val guildCount: Int,
    val cacheInitialized: Boolean,
)
