package com.tryniecki.qumi.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
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
    listState: LazyGridState,
    movementThreshold: Dp = 14.dp,
): Boolean = rememberScrollAwareFabVisible(
    key = listState,
    movementThreshold = movementThreshold,
    firstVisibleItemIndex = { listState.firstVisibleItemIndex },
    firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
    isScrollInProgress = { listState.isScrollInProgress },
)

@Composable
fun rememberScrollAwareFabVisible(
    listState: LazyListState,
    movementThreshold: Dp = 14.dp,
): Boolean = rememberScrollAwareFabVisible(
    key = listState,
    movementThreshold = movementThreshold,
    firstVisibleItemIndex = { listState.firstVisibleItemIndex },
    firstVisibleItemScrollOffset = { listState.firstVisibleItemScrollOffset },
    isScrollInProgress = { listState.isScrollInProgress },
)

@Composable
private fun rememberScrollAwareFabVisible(
    key: Any,
    movementThreshold: Dp = 14.dp,
    firstVisibleItemIndex: () -> Int,
    firstVisibleItemScrollOffset: () -> Int,
    isScrollInProgress: () -> Boolean,
): Boolean {
    val thresholdPx = with(LocalDensity.current) { movementThreshold.roundToPx() }
    var visible by remember(key) { mutableStateOf(true) }

    LaunchedEffect(key, thresholdPx) {
        var previousIndex = firstVisibleItemIndex()
        var previousOffset = firstVisibleItemScrollOffset()
        var accumulatedDown = 0
        var accumulatedUp = 0

        snapshotFlow {
            Triple(
                firstVisibleItemIndex(),
                firstVisibleItemScrollOffset(),
                isScrollInProgress(),
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
