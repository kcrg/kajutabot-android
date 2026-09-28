package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse

/**
 * Pure playback-progress and queue-snapshot helpers.
 *
 * The backend supplies the position; the UI advances it using a monotonic clock
 * between snapshots. The snapshot observation time is captured by the ViewModel.
 */
const val PROGRESS_TICK_MS = 200L

/** Stable key of the backend's concrete playback instance. */
fun playbackIdentity(track: PlaybackTrackResponse, instanceId: String?): String =
    instanceId ?: "preparing:${track.contentType}:${track.contentId}"

fun backendPositionMs(positionMs: Long?, durationMs: Long): Long? =
    if (positionMs == null || durationMs <= 0) null else positionMs.coerceIn(0, durationMs)

/** Monotonic position from a previously computed anchor. Clamped to `0..durationMs`. */
fun currentPositionMs(
    anchorPositionMs: Long,
    anchorElapsedRealtimeMs: Long,
    nowElapsedRealtimeMs: Long,
    durationMs: Long,
): Long {
    if (durationMs <= 0) return 0
    return (anchorPositionMs + (nowElapsedRealtimeMs - anchorElapsedRealtimeMs))
        .coerceIn(0, durationMs)
}

/** Rebuilds progress from the time the snapshot was accepted, even after UI recreation. */
fun playbackPositionAt(
    reportedPositionMs: Long?,
    observedAtElapsedRealtimeMs: Long,
    nowElapsedRealtimeMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
): Long? = backendPositionMs(reportedPositionMs, durationMs)?.let { position ->
    if (isPlaying) currentPositionMs(position, observedAtElapsedRealtimeMs, nowElapsedRealtimeMs, durationMs)
    else position
}

/** Progress fraction clamped to `0..1`. Returns 0 when duration is unusable. */
fun progressFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

/**
 * Guards against stale queue snapshots overwriting fresher UI state, e.g. a
 * SignalR event arriving after a mutation applied a newer version.
 *
 * Applies only snapshots of the currently selected guild with a version not older
 * than the one already shown.
 */
fun shouldApplyQueueSnapshot(
    current: QueueSnapshotResponse?,
    incoming: QueueSnapshotResponse,
    selectedGuildId: String?,
): Boolean {
    if (selectedGuildId == null || incoming.guildId != selectedGuildId) return false
    val currentVersion = current?.version ?: return true
    return incoming.version >= currentVersion
}

/** Frozen Now Playing slide animated as one unit keyed by playback identity. */
data class NowPlayingSlide(
    val identity: String,
    val hasTrack: Boolean,
    val title: String,
    val thumbnailUrl: String?,
    val artworkAccentColor: String?,
    val durationMs: Long,
    val positionMs: Long?,
    val isPlaying: Boolean,
    val positionObservedAtElapsedRealtimeMs: Long? = null,
)

/** Stable UI/media representation derived from the latest backend snapshot. */
data class NowPlayingPresentation(
    val track: PlaybackTrackResponse,
    val playbackInstanceId: String?,
    val positionMs: Long?,
    val isPlaying: Boolean,
)

fun QueueSnapshotResponse.nowPlayingPresentationOrNull(): NowPlayingPresentation? =
    nowPlaying?.let { track ->
        NowPlayingPresentation(track, playbackInstanceId, playbackPositionMilliseconds, nowPlayingStartedAt != null)
    }

fun nowPlayingSlide(
    presentation: NowPlayingPresentation?,
    hasQueue: Boolean,
    positionObservedAtElapsedRealtimeMs: Long? = null,
): NowPlayingSlide {
    val track = presentation?.track
    if (track == null) {
        return NowPlayingSlide(
            identity = if (hasQueue) "empty:idle" else "empty:no-queue",
            hasTrack = false,
            title = "",
            thumbnailUrl = null,
            artworkAccentColor = null,
            durationMs = 0,
            positionMs = null,
            isPlaying = false,
        )
    }

    return NowPlayingSlide(
        identity = playbackIdentity(track, presentation.playbackInstanceId),
        hasTrack = true,
        title = track.title,
        thumbnailUrl = track.artworkUrl,
        artworkAccentColor = track.artworkAccentColor,
        durationMs = track.durationMilliseconds,
        positionMs = presentation.positionMs,
        isPlaying = presentation.isPlaying,
        positionObservedAtElapsedRealtimeMs = positionObservedAtElapsedRealtimeMs,
    )
}

fun nowPlayingSlide(queue: QueueSnapshotResponse?): NowPlayingSlide {
    return nowPlayingSlide(
        presentation = queue?.nowPlayingPresentationOrNull(),
        hasQueue = queue != null,
    )
}
