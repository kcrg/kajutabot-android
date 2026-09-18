package com.tryniecki.kajutabot.api.model.radio

import kotlinx.serialization.Serializable

@Serializable
data class RadioStateResponse(
    val isEnabled: Boolean,
    val minimumDurationSeconds: Int? = null,
    val maximumDurationSeconds: Int? = null,
    val availableTrackCount: Int? = null,
)

@Serializable
data class EnableRadioRequest(
    val voiceChannelId: String,
    val minimumDurationSeconds: Int,
    val maximumDurationSeconds: Int,
    val expectedQueueVersion: Long? = null,
)
