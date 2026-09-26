package com.tryniecki.kajutabot.api.model.queue

import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import kotlinx.serialization.Serializable

@Serializable
data class QueueEntryResponse(
    val entryId: String,
    val position: Int,
    val track: PlaybackTrackResponse,
)

@Serializable
data class QueueSnapshotResponse(
    val guildId: String,
    val voiceChannelId: String?,
    val nowPlaying: PlaybackTrackResponse?,
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
data class QueueMutationRequest(
    val expectedVersion: Long? = null,
)

@Serializable
data class MoveQueueEntryRequest(
    val newPosition: Int,
    val expectedVersion: Long? = null,
)

@Serializable
data class SwapQueueEntriesRequest(
    val firstEntryId: String,
    val secondEntryId: String,
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
