package com.tryniecki.qumi.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val SideBySideMinWidth = 720.dp

// What the row of playback controls needs inside its card.
private val NowPlayingMinWidth = 392.dp

enum class PlayerPaneMode { SINGLE, SIDE_BY_SIDE, TABLETOP }

/** Decided from the pane's own width, because a supporting pane can narrow it on a wide window. */
internal fun playerPaneMode(availableWidth: Dp, isTabletop: Boolean): PlayerPaneMode = when {
    isTabletop -> PlayerPaneMode.TABLETOP
    availableWidth >= SideBySideMinWidth -> PlayerPaneMode.SIDE_BY_SIDE
    else -> PlayerPaneMode.SINGLE
}

internal fun nowPlayingPaneWidth(availableWidth: Dp): Dp =
    (availableWidth * 0.42f).coerceAtLeast(NowPlayingMinWidth)

/** The fold's bounds in window coordinates while a foldable rests half-open on a surface. */
@Composable
internal fun tabletopHinge(): Rect? {
    val posture = currentWindowAdaptiveInfoV2().windowPosture
    return if (posture.isTabletop) posture.hingeList.firstOrNull { !it.isVertical }?.bounds else null
}

/**
 * Lays out what is playing next to the queue when there is room for both. [queue] is on its own
 * in [PlayerPaneMode.SINGLE], where it also scrolls what is playing.
 */
@Composable
internal fun PlayerPanes(
    tabletopHinge: Rect?,
    nowPlaying: @Composable (PlayerPaneMode) -> Unit,
    queue: @Composable (PlayerPaneMode) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableWidth = maxWidth
        val mode = playerPaneMode(availableWidth, isTabletop = tabletopHinge != null)
        when {
            tabletopHinge != null -> TabletopPanes(
                hinge = tabletopHinge,
                upper = { nowPlaying(mode) },
                lower = { queue(mode) },
            )
            mode == PlayerPaneMode.SIDE_BY_SIDE -> Row(Modifier.fillMaxSize()) {
                Box(Modifier.width(nowPlayingPaneWidth(availableWidth)).fillMaxHeight()) { nowPlaying(mode) }
                Box(Modifier.weight(1f).fillMaxHeight()) { queue(mode) }
            }
            else -> queue(mode)
        }
    }
}

/** Keeps each pane on its own side of the fold, so nothing is lost in the crease. */
@Composable
private fun TabletopPanes(
    hinge: Rect,
    upper: @Composable () -> Unit,
    lower: @Composable () -> Unit,
) {
    var hingeTop by remember { mutableFloatStateOf(0f) }
    var hingeBottom by remember { mutableFloatStateOf(0f) }
    Layout(
        content = {
            Box { upper() }
            Box { lower() }
        },
        modifier = Modifier.fillMaxSize().onPlaced { coordinates ->
            hingeTop = coordinates.windowToLocal(Offset(0f, hinge.top)).y
            hingeBottom = coordinates.windowToLocal(Offset(0f, hinge.bottom)).y
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val upperHeight = hingeTop.roundToInt().coerceIn(0, height)
        val lowerTop = hingeBottom.roundToInt().coerceIn(upperHeight, height)
        val upperPlaceable = measurables[0].measure(Constraints.fixed(width, upperHeight))
        val lowerPlaceable = measurables[1].measure(Constraints.fixed(width, height - lowerTop))
        layout(width, height) {
            upperPlaceable.place(0, 0)
            lowerPlaceable.place(0, lowerTop)
        }
    }
}
