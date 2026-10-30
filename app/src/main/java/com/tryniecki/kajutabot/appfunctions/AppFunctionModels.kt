package com.tryniecki.kajutabot.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/** The track that was added to the queue. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class QueuedTrack(
    /** The title of the track that was found and added. */
    val title: String,
    /** The 1-based place in the queue, or null if the track started playing right away. */
    val queuePosition: Int?,
)

/** The track that was added in order to play it immediately. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class PlayedTrack(
    /** The title of the track that was found and added. */
    val title: String,
    /**
     * True if the track replaced the current one or nothing else was playing. False if repeat
     * restarted the current track, which leaves the added track first in the queue.
     */
    val isPlayingNext: Boolean,
)

/** The outcome of skipping the current track. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class SkippedTrack(
    /** The title of the track that was playing. */
    val title: String,
    /** True if repeat restarted this track instead of moving to the next one. */
    val restartedByRepeat: Boolean,
)

/** The state of playback on the selected Discord server. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class PlaybackStatus(
    /** The title of the track being played, or null if nothing is playing. */
    val currentTrackTitle: String?,
    /** The length of the current track in seconds, or null if nothing is playing. */
    val currentTrackDurationSeconds: Long?,
    /** The number of tracks waiting in the queue. */
    val queuedTrackCount: Int,
    /** The titles of up to 5 tracks that play next, in order. */
    val nextTrackTitles: List<String>,
    /** True if the current track repeats. */
    val isRepeatEnabled: Boolean,
    /** True if radio keeps adding tracks when the queue runs out. */
    val isRadioEnabled: Boolean,
)

/** The volume of the bot in the signed-in user's Discord client. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class BotVolume(
    /** The volume as a percentage from 0 to 200, where 100 is the default level. */
    val volumePercent: Int,
)

/** The track that was added to favorites. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class FavoritedTrack(
    /** The title of the track. */
    val title: String,
)
