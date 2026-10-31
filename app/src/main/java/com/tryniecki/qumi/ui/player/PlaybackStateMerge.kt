package com.tryniecki.qumi.ui.player

import com.tryniecki.qumi.api.model.queue.PlaybackStateResponse
import com.tryniecki.qumi.api.model.queue.QueueSnapshotResponse

/** Keeps [QueueSnapshotResponse.pendingEntries], which only QueueUpdated carries. */
internal fun QueueSnapshotResponse.withPlaybackState(state: PlaybackStateResponse): QueueSnapshotResponse = copy(
    voiceChannelId = state.voiceChannelId,
    nowPlaying = state.nowPlaying,
    nowPlayingFromRadio = state.nowPlayingFromRadio,
    radio = state.radio,
    pendingEntriesCount = state.pendingEntriesCount,
    pendingDurationMilliseconds = state.pendingDurationMilliseconds,
    version = state.version,
    queueVersion = state.queueVersion,
    nowPlayingStartedAt = state.nowPlayingStartedAt,
    isRepeatEnabled = state.isRepeatEnabled,
    playbackInstanceId = state.playbackInstanceId,
    playbackPositionMilliseconds = state.playbackPositionMilliseconds,
)
