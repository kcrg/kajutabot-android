package com.tryniecki.qumi.ui.player

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Shared local playback-progress state.
 *
 * Anchored to the latest backend position and playback instance, then advanced
 * on the monotonic clock between snapshots. Read [fraction] in the draw phase so
 * a tick redraws the bar without recomposing its parent.
 */
@Stable
class PlaybackProgressState(
    private val reportedPositionMs: Long?,
    private val anchorElapsedRealtimeMs: Long,
    private val durationMs: Long,
    private val isPlaying: Boolean,
    nowElapsedRealtimeMs: Long,
) {
    var nowElapsedRealtimeMs by mutableLongStateOf(nowElapsedRealtimeMs)
        internal set

    val positionMs: Long?
        get() = playbackPositionAt(
            reportedPositionMs, anchorElapsedRealtimeMs, nowElapsedRealtimeMs, durationMs, isPlaying,
        )

    val fraction: Float
        get() = positionMs?.let { progressFraction(it, durationMs) } ?: 0f
}

@Composable
fun rememberPlaybackProgress(
    playbackKey: String,
    reportedPositionMs: Long?,
    observedAtElapsedRealtimeMs: Long?,
    durationMs: Long,
    isPlaying: Boolean,
    tickMs: Long = PROGRESS_TICK_MS,
): PlaybackProgressState {
    val progress = remember(
        playbackKey, reportedPositionMs, observedAtElapsedRealtimeMs, durationMs, isPlaying,
    ) {
        // A newly mounted screen must show the time elapsed since the snapshot
        // immediately, before the first tick.
        val now = SystemClock.elapsedRealtime()
        PlaybackProgressState(
            reportedPositionMs = reportedPositionMs,
            anchorElapsedRealtimeMs = observedAtElapsedRealtimeMs ?: now,
            durationMs = durationMs,
            isPlaying = isPlaying,
            nowElapsedRealtimeMs = now,
        )
    }
    LaunchedEffect(progress, tickMs) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            delay(tickMs)
            // The frame clock pauses while the app is stopped, which also pauses this ticker.
            withFrameMillis { progress.nowElapsedRealtimeMs = SystemClock.elapsedRealtime() }
        }
    }
    return progress
}

/** Elapsed label that changes once per second, however often [progress] ticks. */
@Composable
fun rememberPlaybackElapsedText(progress: PlaybackProgressState): String {
    val elapsedSeconds by remember(progress) {
        derivedStateOf { progress.positionMs?.let { it / 1000 } }
    }
    return remember(elapsedSeconds) { formatPlaybackElapsed(elapsedSeconds?.let { it * 1000 }) }
}

/**
 * Full Now Playing progress: wavy indicator with elapsed/total labels.
 * Linear motion only — no bounce or spring on a progress bar.
 */
@Composable
fun PlaybackProgressIndicator(
    playbackKey: String,
    reportedPositionMs: Long?,
    observedAtElapsedRealtimeMs: Long?,
    durationMs: Long,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress = rememberPlaybackProgress(
        playbackKey, reportedPositionMs, observedAtElapsedRealtimeMs, durationMs, isPlaying,
    )
    val elapsedText = rememberPlaybackElapsedText(progress)
    val totalText = remember(durationMs) {
        formatPlaybackTotal(durationMs)
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearWavyProgressIndicator(
            progress = { progress.fraction },
            // The wave redraws every frame. Its own layer limits the recording, and on devices
            // with partial updates also the damage, to the bar. Some devices always redraw the window.
            modifier = Modifier.fillMaxWidth().graphicsLayer(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = elapsedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = totalText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun formatPlaybackElapsed(positionMs: Long?): String =
    if (positionMs == null) "--:--" else formatDuration(positionMs)

fun formatPlaybackTotal(durationMs: Long): String =
    if (durationMs <= 0) "--:--" else formatDuration(durationMs)
