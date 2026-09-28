package com.tryniecki.kajutabot.ui.player

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Shared local playback-progress state.
 *
 * Anchored to the latest backend position and playback instance, then advanced
 * on the monotonic clock between snapshots. Only the reading subtree recomposes.
 */
data class PlaybackProgress(
    val positionMs: Long?,
    val fraction: Float,
)

@Composable
fun rememberPlaybackProgress(
    playbackKey: String,
    reportedPositionMs: Long?,
    observedAtElapsedRealtimeMs: Long?,
    durationMs: Long,
    isPlaying: Boolean,
    tickMs: Long = PROGRESS_TICK_MS,
): PlaybackProgress {
    val anchorElapsedRealtimeMs = remember(
        playbackKey, reportedPositionMs, observedAtElapsedRealtimeMs, durationMs, isPlaying,
    ) {
        observedAtElapsedRealtimeMs ?: SystemClock.elapsedRealtime()
    }
    var nowElapsedRealtime by remember(
        playbackKey, reportedPositionMs, observedAtElapsedRealtimeMs, durationMs, isPlaying,
    ) {
        // A newly mounted screen must show the time elapsed since the snapshot
        // immediately, before the first 200 ms tick.
        mutableLongStateOf(SystemClock.elapsedRealtime())
    }
    LaunchedEffect(playbackKey, reportedPositionMs, observedAtElapsedRealtimeMs, durationMs, isPlaying, tickMs) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            delay(tickMs)
            nowElapsedRealtime = SystemClock.elapsedRealtime()
        }
    }
    val positionMs = playbackPositionAt(
        reportedPositionMs, anchorElapsedRealtimeMs, nowElapsedRealtime, durationMs, isPlaying,
    )
    return PlaybackProgress(
        positionMs = positionMs,
        fraction = if (positionMs == null) 0f else progressFraction(positionMs, durationMs),
    )
}

/** Progress represents elapsed time, so its interpolation stays linear. */
@Composable
fun rememberSmoothPlaybackFraction(playbackKey: String, fraction: Float): Float {
    return key(playbackKey) {
        val animatedFraction by animateFloatAsState(
            targetValue = fraction,
            animationSpec = tween(durationMillis = PROGRESS_TICK_MS.toInt(), easing = LinearEasing),
            label = "playbackProgress",
        )
        animatedFraction
    }
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
    val animatedFraction = rememberSmoothPlaybackFraction(playbackKey, progress.fraction)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearWavyProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,) {
            Text(
                text = formatPlaybackElapsed(progress.positionMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatPlaybackTotal(durationMs),
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
