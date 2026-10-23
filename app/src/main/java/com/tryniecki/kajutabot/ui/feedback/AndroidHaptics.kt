package com.tryniecki.kajutabot.ui.feedback

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

internal fun FeedbackEffect.composeType(): HapticFeedbackType = when (this) {
    FeedbackEffect.Confirm -> HapticFeedbackType.Confirm
    FeedbackEffect.Reject -> HapticFeedbackType.Reject
    FeedbackEffect.ToggleOn -> HapticFeedbackType.ToggleOn
    FeedbackEffect.ToggleOff -> HapticFeedbackType.ToggleOff
    FeedbackEffect.Selection, FeedbackEffect.DragTick -> HapticFeedbackType.SegmentTick
    FeedbackEffect.DragStart -> HapticFeedbackType.LongPress
    FeedbackEffect.SwipeThreshold -> HapticFeedbackType.GestureThresholdActivate
    FeedbackEffect.GestureEnd -> HapticFeedbackType.GestureEnd
}

@Composable
fun rememberFeedbackPerformer(): (FeedbackEffect) -> Unit {
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    return remember(haptics, view) {
        { effect ->
            // No ignore-setting flags, permissions or manual vibrations. Unsupported effects
            // may be silent. Do not retry with a second effect when the system declines one.
            performNativeFeedback(effect, haptics, view)
        }
    }
}

internal fun performNativeFeedback(effect: FeedbackEffect, haptics: HapticFeedback, view: View) {
    if (effect == FeedbackEffect.DragStart && Build.VERSION.SDK_INT >= 34) {
        view.performHapticFeedback(HapticFeedbackConstants.DRAG_START)
    } else {
        haptics.performHapticFeedback(effect.composeType())
    }
}

/** Exactly one collector per owner, outside reusable controls and navigation animations. */
@Composable
fun ObserveUserFeedback(events: Flow<FeedbackEffect>) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val perform = rememberFeedbackPerformer()
    LaunchedEffect(events, lifecycle, perform) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            events.collect { perform(it) }
        }
    }
}
