package com.tryniecki.qumi.api.model.radio

import kotlinx.serialization.Serializable

@Serializable
data class RadioStateResponse(
    val isEnabled: Boolean,
    val minimumDurationSeconds: Int? = null,
    val maximumDurationSeconds: Int? = null,
    val availableTrackCount: Int? = null,
    val mode: String = "cache",
)

@Serializable
data class EnableRadioRequest(
    val voiceChannelId: String,
    val minimumDurationSeconds: Int,
    val maximumDurationSeconds: Int,
    val expectedQueueVersion: Long? = null,
    val mode: String = "cache",
)
