package com.tryniecki.kajutabot.api.model.queue

import com.tryniecki.kajutabot.api.model.common.ApiOperationResponse
import com.tryniecki.kajutabot.api.model.common.TrackResponse
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import kotlinx.serialization.Serializable

@Serializable
data class QueueEntryResponse(
    val entryId: String,
    val position: Int,
    val track: TrackResponse,
)

@Serializable
data class QueueSnapshotResponse(
    val guildId: String,
    val voiceChannelId: String?,
    val nowPlaying: TrackResponse?,
    val nowPlayingFromRadio: Boolean,
    val radio: RadioStateResponse,
    val pendingEntries: List<QueueEntryResponse>,
    val pendingDurationMilliseconds: Long,
    val version: Long,
    val nowPlayingStartedAt: String? = null,
    val isRepeatEnabled: Boolean = false,
)

@Serializable
data class EnqueueRequest(
    val voiceChannelId: String,
    val inputs: List<String>,
    val expectedVersion: Long? = null,
)

@Serializable
data class EnqueueResponse(
    val operation: ApiOperationResponse,
    val snapshot: QueueSnapshotResponse,
)

@Serializable
data class QueueMutationRequest(
    val expectedVersion: Long? = null,
)

@Serializable
data class MoveQueueEntryRequest(
    val entryId: String,
    val newPosition: Int,
    val expectedVersion: Long? = null,
)

@Serializable
data class SkipQueueRequest(
    val skipToPosition: Int? = null,
    val expectedVersion: Long? = null,
)

@Serializable
data class SetQueueRepeatRequest(
    val isEnabled: Boolean,
    val expectedVersion: Long? = null,
)

@Serializable
data class QueueMutationResponse(
    val operation: ApiOperationResponse,
    val snapshot: QueueSnapshotResponse,
)
