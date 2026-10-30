package com.tryniecki.qumi.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import com.tryniecki.qumi.ui.theme.KbMotion
import com.tryniecki.qumi.ui.theme.directionalSharedAxisXMotion
import com.tryniecki.qumi.ui.theme.tabFadeThroughMotion

private const val MOTION_OWNER_KEY = "navigation.motionOwner"

/** Motion scheme values for the transitions below, read once by the composable shell. */
internal class NavigationMotionSpecs(
    val hierarchySpatial: FiniteAnimationSpec<IntOffset>,
    val hierarchyEffects: FiniteAnimationSpec<Float>,
    val hierarchySlideDistancePx: Int,
    val tabEnter: FiniteAnimationSpec<Float>,
    val tabExit: FiniteAnimationSpec<Float>,
    val tabSpatial: FiniteAnimationSpec<Float>,
)

/**
 * Tags every Navigation 3 entry with the bottom-navigation destination that owns it.
 * Detail destinations inherit their parent's owner, which lets one transition policy
 * distinguish tab switches from hierarchical push/pop navigation.
 */
internal fun navigationMotionMetadata(owner: AppDestination): Map<String, Any> =
    mapOf(MOTION_OWNER_KEY to owner)

/**
 * Peer tabs use Material fade through; hierarchical push/pop uses Shared Axis X.
 */
internal fun AnimatedContentTransitionScope<Scene<NavKey>>.forwardTransition(
    specs: NavigationMotionSpecs,
): ContentTransform {
    val from = initialState.motionOwner()
    val to = targetState.motionOwner()
    val isTabSwitch = from != null && to != null && from != to

    if (isTabSwitch) return tabFadeThroughMotion(specs.tabEnter, specs.tabExit, specs.tabSpatial)

    return directionalSharedAxisXMotion(
        spatialSpec = specs.hierarchySpatial,
        effectsSpec = specs.hierarchyEffects,
        direction = FORWARD,
        slideDistancePx = specs.hierarchySlideDistancePx,
        initialAlpha = KbMotion.HIERARCHY_INITIAL_ALPHA,
    )
}

/**
 * Back / predictive-back mirrors the selected transition. Hierarchical pages
 * return from left to right; peer destinations fade through in either direction.
 */
internal fun AnimatedContentTransitionScope<Scene<NavKey>>.popTransition(
    specs: NavigationMotionSpecs,
): ContentTransform {
    val from = initialState.motionOwner()
    val to = targetState.motionOwner()
    val isTabSwitch = from != null && to != null && from != to

    if (isTabSwitch) return tabFadeThroughMotion(specs.tabEnter, specs.tabExit, specs.tabSpatial)

    return directionalSharedAxisXMotion(
        spatialSpec = specs.hierarchySpatial,
        effectsSpec = specs.hierarchyEffects,
        direction = BACKWARD,
        slideDistancePx = specs.hierarchySlideDistancePx,
        initialAlpha = KbMotion.HIERARCHY_INITIAL_ALPHA,
    )
}

private fun Scene<NavKey>.motionOwner(): AppDestination? =
    entries.lastOrNull()?.metadata?.get(MOTION_OWNER_KEY) as? AppDestination

private const val FORWARD = 1
private const val BACKWARD = -1
