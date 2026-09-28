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
    val pendingEntriesCount: Int,
    val pendingDurationMilliseconds: Long,
    val version: Long,
    val queueVersion: Long,
    val nowPlayingStartedAt: String? = null,
    val playbackInstanceId: String? = null,
    val playbackPositionMilliseconds: Long? = null,
    val isRepeatEnabled: Boolean = false,
    val skipOutcome: SkipOutcome? = null,
    val addedTracks: List<PlaybackTrackResponse>? = null,
)

@Serializable
enum class SkipOutcome { Advanced, RestartedRepeatedTrack }

@Serializable
data class EnqueueRequest(
    val voiceChannelId: String,
    val inputs: List<String>,
    val expectedQueueVersion: Long? = null,
)

@Serializable
data class QueueMutationRequest(
    val expectedQueueVersion: Long? = null,
)

@Serializable
data class MoveQueueEntryRequest(
    val newPosition: Int,
    val expectedQueueVersion: Long? = null,
)

@Serializable
data class SwapQueueEntriesRequest(
    val firstEntryId: String,
    val secondEntryId: String,
    val expectedQueueVersion: Long? = null,
)

@Serializable
data class SkipQueueRequest(
    val skipToPosition: Int? = null,
    val expectedQueueVersion: Long? = null,
)

@Serializable
data class SetQueueRepeatRequest(
    val isEnabled: Boolean,
    val expectedQueueVersion: Long? = null,
)
