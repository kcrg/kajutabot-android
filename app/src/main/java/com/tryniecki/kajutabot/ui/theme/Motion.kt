package com.tryniecki.kajutabot.ui.theme

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * App-wide motion language, mapped onto [androidx.compose.material3.MaterialTheme.motionScheme].
 *
 * Rules used across screens with the expressive Material motion scheme:
 * - effects (alpha/color) come from `*EffectsSpec`,
 * - spatial (position/size) comes from `*SpatialSpec`,
 * - `fast*` for frequent interactions,
 * - `default*` spatial motion for hierarchical page navigation,
 * - `slow*` only for deliberately prominent motion.
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

    /**
     * Hierarchical navigation uses a restrained fixed travel distance. Keeping this
     * fixed in dp makes motion feel consistent across phones, tablets and foldables
     * without making page changes visually dominant.
     */
    val HIERARCHY_SLIDE_DISTANCE = 64.dp

    /** Fade is a secondary cue for hierarchical navigation. */
    const val HIERARCHY_INITIAL_ALPHA = 0.93f
}

/** Peer destinations have no hierarchy: fade out, then fade and gently scale in. */
fun tabFadeThroughMotion(): ContentTransform =
    (fadeIn(
        animationSpec = tween(durationMillis = 120, delayMillis = 90, easing = FastOutSlowInEasing),
    ) + scaleIn(
        animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing),
        initialScale = 0.96f,
    )) togetherWith fadeOut(
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
    )

/**
 * Material Shared Axis X-style app navigation. Position is the primary cue and
 * alpha is the secondary cue. The outgoing content always reaches alpha 0 before
 * NavDisplay disposes it; a non-zero fadeOut target causes a visible one-frame snap.
 * There is intentionally no scale: scale belongs to
 * Shared Axis Z and makes hierarchical X navigation feel less like Android Settings.
 */
fun directionalSharedAxisXMotion(
    spatialSpec: FiniteAnimationSpec<IntOffset>,
    effectsSpec: FiniteAnimationSpec<Float>,
    direction: Int,
    slideDistancePx: Int,
    initialAlpha: Float,
): ContentTransform {
    val normalizedDirection = if (direction >= 0) 1 else -1
    val distance = slideDistancePx * normalizedDirection

    val enter =
        slideInHorizontally(animationSpec = spatialSpec) { distance } +
            fadeIn(animationSpec = effectsSpec, initialAlpha = initialAlpha)
    val exit =
        slideOutHorizontally(animationSpec = spatialSpec) { -distance } +
            fadeOut(animationSpec = effectsSpec, targetAlpha = 0f)

    return enter togetherWith exit
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
