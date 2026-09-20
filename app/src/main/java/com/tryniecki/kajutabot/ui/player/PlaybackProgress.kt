package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.model.common.TrackResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Pure playback-progress and queue-snapshot helpers.
 *
 * Everything here is JVM-testable: wall-clock instants and monotonic-clock readings
 * are passed in as plain [Long] values. Android-only [android.os.SystemClock] stays
 * in the Compose layer, which anchors [PlaybackProgressAnchor] once per playback
 * identity and then advances locally without network or timestamp parsing.
 */
const val PROGRESS_TICK_MS = 200L

data class PlaybackProgressAnchor(
    /** Position derived from wall clock at anchor time, or null when timing is invalid. */
    val positionAtAnchorMs: Long?,
    /** `SystemClock.elapsedRealtime()` captured together with the anchor position. */
    val elapsedRealtimeAnchorMs: Long,
)

/** Stable key of one concrete playback instance (track + its start moment). */
fun playbackIdentity(track: TrackResponse, startedAt: String?): String =
    "${track.contentType}:${track.contentId}:$startedAt"

/** Parses a backend ISO-8601 timestamp to epoch millis, or null when unusable. */
fun parseStartedAtEpochMs(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return try {
        Instant.parse(raw.trim()).toEpochMilli()
    } catch (_: Exception) {
        try {
            OffsetDateTime.parse(raw.trim()).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Initial position from wall clock: `nowUtcMs - startedAt`, clamped to
 * `0..durationMs`. Null when timing data is missing or unusable — the UI must
 * show 0 / `--:--` instead of inventing a value.
 */
fun initialPositionMs(startedAtRaw: String?, durationMs: Long, nowUtcMs: Long): Long? {
    if (durationMs <= 0) return null
    val startedAt = parseStartedAtEpochMs(startedAtRaw) ?: return null
    return (nowUtcMs - startedAt).coerceIn(0, durationMs)
}

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

/** Progress fraction clamped to `0..1`. Returns 0 when duration is unusable. */
fun progressFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

/** Milliseconds left until the expected track end, or null when timing is invalid. */
fun remainingMs(startedAtRaw: String?, durationMs: Long, nowUtcMs: Long): Long? {
    val position = initialPositionMs(startedAtRaw, durationMs, nowUtcMs) ?: return null
    return durationMs - position
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
    val hint: String,
    val thumbnailUrl: String?,
    val artworkAccentColor: String?,
    val durationMs: Long,
    val startedAt: String?,
)

/**
 * Stable UI/media representation of the currently playing track.
 *
 * The raw queue snapshot can briefly report `nowPlaying = null` while the backend
 * switches tracks. Keeping this projection separate lets the UI hold the previous
 * track until the next concrete track is known instead of flashing an idle state.
 */
data class NowPlayingPresentation(
    val track: TrackResponse,
    val startedAt: String?,
)

fun QueueSnapshotResponse.nowPlayingPresentationOrNull(): NowPlayingPresentation? =
    nowPlaying?.let { track -> NowPlayingPresentation(track, nowPlayingStartedAt) }

fun nowPlayingSlide(
    presentation: NowPlayingPresentation?,
    hasQueue: Boolean,
): NowPlayingSlide {
    val track = presentation?.track
    if (track == null) {
        return NowPlayingSlide(
            identity = if (hasQueue) "empty:idle" else "empty:no-queue",
            hasTrack = false,
            title = "Nic nie gra",
            hint = if (hasQueue) {
                "Kolejka oczekuje na utwory"
            } else {
                "Połącz aplikację z serwerem i wybierz kanał głosowy"
            },
            thumbnailUrl = null,
            artworkAccentColor = null,
            durationMs = 0,
            startedAt = null,
        )
    }

    return NowPlayingSlide(
        identity = playbackIdentity(track, presentation.startedAt),
        hasTrack = true,
        title = track.title,
        hint = formatDuration(track.durationMilliseconds),
        thumbnailUrl = track.thumbnailUrl,
        artworkAccentColor = track.artworkAccentColor,
        durationMs = track.durationMilliseconds,
        startedAt = presentation.startedAt,
    )
}

fun nowPlayingSlide(queue: QueueSnapshotResponse?): NowPlayingSlide {
    return nowPlayingSlide(
        presentation = queue?.nowPlayingPresentationOrNull(),
        hasQueue = queue != null,
    )
}
