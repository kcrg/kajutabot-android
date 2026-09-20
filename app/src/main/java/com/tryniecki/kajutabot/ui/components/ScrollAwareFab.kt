package com.tryniecki.kajutabot.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

/**
 * Keeps floating actions out of the way while the user is reading a scrolling list.
 *
 * A small movement threshold prevents the FAB from flickering when LazyColumn settles by a few
 * pixels. Reaching the top always reveals it again.
 */
@Composable
fun rememberScrollAwareFabVisible(
    listState: LazyListState,
    movementThreshold: Dp = 14.dp,
): Boolean {
    val thresholdPx = with(LocalDensity.current) { movementThreshold.roundToPx() }
    var visible by remember(listState) { mutableStateOf(true) }

    LaunchedEffect(listState, thresholdPx) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        var accumulatedDown = 0
        var accumulatedUp = 0

        snapshotFlow {
            Triple(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
                listState.isScrollInProgress,
            )
        }
            .distinctUntilChanged()
            .collect { (index, offset, isScrolling) ->
                if (index == 0 && offset <= thresholdPx) {
                    visible = true
                    accumulatedDown = 0
                    accumulatedUp = 0
                } else if (isScrolling) {
                    val direction = when {
                        index > previousIndex -> 1
                        index < previousIndex -> -1
                        offset > previousOffset -> 1
                        offset < previousOffset -> -1
                        else -> 0
                    }
                    val movement = if (index == previousIndex) {
                        abs(offset - previousOffset)
                    } else {
                        // Crossing an item boundary is already a meaningful scroll gesture.
                        thresholdPx
                    }

                    when (direction) {
                        1 -> {
                            accumulatedUp = 0
                            accumulatedDown += movement
                            if (accumulatedDown >= thresholdPx) {
                                visible = false
                                accumulatedDown = 0
                            }
                        }

                        -1 -> {
                            accumulatedDown = 0
                            accumulatedUp += movement
                            if (accumulatedUp >= thresholdPx) {
                                visible = true
                                accumulatedUp = 0
                            }
                        }
                    }
                }

                previousIndex = index
                previousOffset = offset
            }
    }

    return visible
}
