package com.tryniecki.kajutabot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.tryniecki.kajutabot.R
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.delay

enum class SwipeActionStatus { IDLE, PENDING, SUCCESS, FAILURE }

/** Shared, bounded swipe actions for Favorites and pending queue cards. */
@Composable
fun SwipeActionCard(
    addLabel: String,
    removeLabel: String,
    enabled: Boolean,
    addStatus: SwipeActionStatus = SwipeActionStatus.IDLE,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val revealWidth = with(LocalDensity.current) { (48.dp + 32.dp).toPx() }
    val swipeState = remember(revealWidth) {
        AnchoredDraggableState(
            initialValue = SwipeToDismissBoxValue.Settled,
            anchors = DraggableAnchors {
                SwipeToDismissBoxValue.EndToStart at -revealWidth
                SwipeToDismissBoxValue.Settled at 0f
                SwipeToDismissBoxValue.StartToEnd at revealWidth
            },
        )
    }
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = swipeState,
        positionalThreshold = { revealWidth / 2f },
    )
    val currentEnabled = rememberUpdatedState(enabled)
    val currentAdd = rememberUpdatedState(onAdd)
    val currentRemove = rememberUpdatedState(onRemove)
    var addTriggered by remember { mutableStateOf(false) }
    val operationDescription = when {
        addTriggered && addStatus == SwipeActionStatus.SUCCESS -> stringResource(R.string.swipe_add_success)
        addTriggered && addStatus == SwipeActionStatus.FAILURE -> stringResource(R.string.swipe_add_failure)
        addTriggered -> stringResource(R.string.swipe_add_pending)
        else -> null
    }

    LaunchedEffect(addStatus, addTriggered) {
        if (addTriggered && (addStatus == SwipeActionStatus.SUCCESS || addStatus == SwipeActionStatus.FAILURE)) {
            delay(900)
            swipeState.animateTo(SwipeToDismissBoxValue.Settled)
            addTriggered = false
        }
    }

    LaunchedEffect(swipeState) {
        snapshotFlow { swipeState.settledValue }
            .filter { it != SwipeToDismissBoxValue.Settled }
            .collect { direction ->
                if (currentEnabled.value) when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        addTriggered = true
                        currentAdd.value()
                    }
                    SwipeToDismissBoxValue.EndToStart -> currentRemove.value()
                    SwipeToDismissBoxValue.Settled -> Unit
                }
                if (direction != SwipeToDismissBoxValue.StartToEnd || !currentEnabled.value) {
                    swipeState.animateTo(SwipeToDismissBoxValue.Settled)
                }
            }
    }

    Box(
        modifier = modifier.testTag("swipeActionCard").semantics {
            if (operationDescription != null) stateDescription = operationDescription
        }.clip(MaterialTheme.shapes.medium).anchoredDraggable(
            state = swipeState,
            orientation = Orientation.Horizontal,
            enabled = enabled && !addTriggered && swipeState.settledValue == SwipeToDismissBoxValue.Settled,
            flingBehavior = flingBehavior,
        ),
    ) {
        val removing = swipeState.requireOffset() < 0f
        val failedAdd = addTriggered && addStatus == SwipeActionStatus.FAILURE
        Row(
            modifier = Modifier.matchParentSize()
                .background(
                    if (removing || failedAdd) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.secondaryContainer,
                )
                .padding(horizontal = 16.dp),
            horizontalArrangement = if (removing) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (removing || failedAdd) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondary,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (!removing && addTriggered && (addStatus == SwipeActionStatus.IDLE || addStatus == SwipeActionStatus.PENDING)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSecondary)
                } else {
                    Icon(
                        painter = painterResource(
                            when {
                                removing -> com.composables.icons.tabler.outline.R.drawable.tabler_ic_trash_outline
                                addTriggered && addStatus == SwipeActionStatus.SUCCESS -> com.composables.icons.tabler.outline.R.drawable.tabler_ic_check_outline
                                addTriggered && addStatus == SwipeActionStatus.FAILURE -> com.composables.icons.tabler.outline.R.drawable.tabler_ic_x_outline
                                else -> com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_add_outline
                            },
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (removing || failedAdd) MaterialTheme.colorScheme.onError
                            else MaterialTheme.colorScheme.onSecondary,
                    )
                }
            }
        }
        content(
            Modifier.offset { IntOffset(swipeState.requireOffset().roundToInt(), 0) }
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(addLabel) {
                            if (!enabled) false else { onAdd(); true }
                        },
                        CustomAccessibilityAction(removeLabel) {
                            if (!enabled) false else { onRemove(); true }
                        },
                    )
                },
        )
    }
}
