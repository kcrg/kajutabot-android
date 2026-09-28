package com.tryniecki.kajutabot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

const val ACTION_SPINNER_DELAY_MS = 100L

@Composable
fun rememberDelayedPending(visible: Boolean): Boolean {
    var ready by remember(visible) { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            delay(ACTION_SPINNER_DELAY_MS)
            ready = true
        }
    }
    return visible && ready
}

@Composable
fun DelayedPendingSpinner(
    visible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val show = rememberDelayedPending(visible)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (show) {
            ExpressiveLoadingIndicator(color = color, modifier = Modifier.size(size))
        }
    }
}

/** Keeps the idle icon in place for fast requests, then shows progress or the result. */
@Composable
fun ActionFeedbackIcon(
    status: SwipeActionStatus,
    @DrawableRes idleIconRes: Int,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val showSpinner = rememberDelayedPending(status == SwipeActionStatus.PENDING)
    val visualStatus = if (status == SwipeActionStatus.PENDING && !showSpinner) {
        SwipeActionStatus.IDLE
    } else status
    AnimatedContent(
        targetState = visualStatus,
        modifier = modifier.size(size),
        transitionSpec = {
            (fadeIn(tween(150)) + scaleIn(initialScale = 0.82f, animationSpec = tween(150)))
                .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.82f, animationSpec = tween(120)))
                .using(SizeTransform(clip = false))
        },
        label = "actionFeedbackIcon",
    ) { displayedStatus ->
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            if (displayedStatus == SwipeActionStatus.PENDING) {
                ExpressiveLoadingIndicator(color = tint, modifier = Modifier.size(size))
            } else {
                Icon(
                    painter = painterResource(
                        when (displayedStatus) {
                            SwipeActionStatus.SUCCESS -> com.composables.icons.tabler.outline.R.drawable.tabler_ic_check_outline
                            SwipeActionStatus.FAILURE -> com.composables.icons.tabler.outline.R.drawable.tabler_ic_x_outline
                            else -> idleIconRes
                        },
                    ),
                    contentDescription = contentDescription,
                    modifier = Modifier.size(size),
                    tint = tint,
                )
            }
        }
    }
}
