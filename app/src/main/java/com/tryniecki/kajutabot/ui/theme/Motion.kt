package com.tryniecki.kajutabot.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.ui.unit.IntOffset

/**
 * App-wide motion language, mapped onto [androidx.compose.material3.MaterialTheme.motionScheme].
 *
 * Rules used across screens with the expressive Material motion scheme:
 * - effects (alpha/color) come from `*EffectsSpec`,
 * - spatial (position/size) comes from `*SpatialSpec`,
 * - `fast*` for frequent or bottom-navigation changes, `default*` for
 *   hierarchical and content transitions, `slow*` for prominent onboarding motion.
 *
 * Call sites read `MaterialTheme.motionScheme` once and pass specs in; the
 * choreography itself lives here so durations/directions aren't scattered.
 * Extension receivers bind the track type automatically — no explicit type
 * arguments needed at call sites.
 */
object KbMotion {
    /** Shared-axis travel for the full Now Playing card. */
    const val TRACK_SLIDE_FRACTION = 0.08f

    /** Subtler shared-axis travel for the MiniPlayer. */
    const val MINI_TRACK_SLIDE_FRACTION = 0.06f

    /** Shared-axis travel for in-tab hierarchical navigation. */
    const val HIERARCHY_SLIDE_FRACTION = 0.12f

    /** Shared-axis travel between adjacent tabs. */
    const val TAB_SLIDE_FRACTION = 0.08f
}

/**
 * Directional shared-axis X with a fixed forward direction (next track).
 * Enter slides in from the right, exit leaves to the left. Interruptible.
 */
fun <S> AnimatedContentTransitionScope<S>.forwardSharedAxisX(
    fadeSpec: FiniteAnimationSpec<Float>,
    slideSpec: FiniteAnimationSpec<IntOffset>,
    distanceFraction: Float = KbMotion.TRACK_SLIDE_FRACTION,
): ContentTransform =
    (fadeIn(fadeSpec) +
        slideInHorizontally(slideSpec) { (it * distanceFraction).toInt() }) togetherWith
        (fadeOut(fadeSpec) +
            slideOutHorizontally(slideSpec) { -(it * distanceFraction).toInt() })

/**
 * Directional shared-axis Y with a fixed forward direction (next track).
 * Enter slides in from the bottom, exit leaves through the top. Interruptible.
 */
fun <S> AnimatedContentTransitionScope<S>.forwardSharedAxisY(
    fadeSpec: FiniteAnimationSpec<Float>,
    slideSpec: FiniteAnimationSpec<IntOffset>,
    distanceFraction: Float = KbMotion.TRACK_SLIDE_FRACTION,
): ContentTransform =
    (fadeIn(fadeSpec) +
        slideInVertically(slideSpec) { (it * distanceFraction).toInt() }) togetherWith
        (fadeOut(fadeSpec) +
            slideOutVertically(slideSpec) { -(it * distanceFraction).toInt() })

/**
 * Fade between peer states without a directional slide (loading/content,
 * idle/playing). The caller chooses separate enter and exit effects specs.
 */
fun <S> AnimatedContentTransitionScope<S>.fadeThrough(
    enterSpec: FiniteAnimationSpec<Float>,
    exitSpec: FiniteAnimationSpec<Float>,
): ContentTransform =
    fadeIn(enterSpec) togetherWith fadeOut(exitSpec)
