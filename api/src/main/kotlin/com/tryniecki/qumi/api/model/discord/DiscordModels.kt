package com.tryniecki.qumi.api.model.discord

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
data class VoiceChannelsUpdatedEvent(
    val guildId: String,
    val channels: List<DiscordVoiceChannelResponse>,
)
