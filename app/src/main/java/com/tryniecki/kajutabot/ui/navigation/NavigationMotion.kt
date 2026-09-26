package com.tryniecki.kajutabot.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import com.tryniecki.kajutabot.ui.theme.KbMotion
import com.tryniecki.kajutabot.ui.theme.directionalSharedAxisXMotion
import com.tryniecki.kajutabot.ui.theme.tabFadeThroughMotion

private const val MOTION_OWNER_KEY = "kajutabot.navigation.motionOwner"

/**
 * Tags every Navigation 3 entry with the bottom-navigation destination that owns it.
 * Detail destinations inherit their parent's owner, which lets one transition policy
 * distinguish tab switches from hierarchical push/pop navigation.
 */
internal fun navigationMotionMetadata(owner: AppDestination): Map<String, Any> =
    mapOf(MOTION_OWNER_KEY to owner.name)

/**
 * Peer tabs use Material fade through; hierarchical push/pop uses Shared Axis X.
 */
internal fun AnimatedContentTransitionScope<Scene<NavKey>>.kajutaForwardTransition(
    hierarchySpatialSpec: FiniteAnimationSpec<IntOffset>,
    hierarchyEffectsSpec: FiniteAnimationSpec<Float>,
    hierarchySlideDistancePx: Int,
): ContentTransform {
    val from = initialState.motionOwner()
    val to = targetState.motionOwner()
    val isTabSwitch = from != null && to != null && from != to

    if (isTabSwitch) return tabFadeThroughMotion()

    return directionalSharedAxisXMotion(
        spatialSpec = hierarchySpatialSpec,
        effectsSpec = hierarchyEffectsSpec,
        direction = FORWARD,
        slideDistancePx = hierarchySlideDistancePx,
        initialAlpha = KbMotion.HIERARCHY_INITIAL_ALPHA,
    )
}

/**
 * Back / predictive-back mirrors the selected transition. Hierarchical pages
 * return from left to right; peer destinations fade through in either direction.
 */
internal fun AnimatedContentTransitionScope<Scene<NavKey>>.kajutaPopTransition(
    hierarchySpatialSpec: FiniteAnimationSpec<IntOffset>,
    hierarchyEffectsSpec: FiniteAnimationSpec<Float>,
    hierarchySlideDistancePx: Int,
): ContentTransform {
    val from = initialState.motionOwner()
    val to = targetState.motionOwner()
    val isTabSwitch = from != null && to != null && from != to

    if (isTabSwitch) return tabFadeThroughMotion()

    return directionalSharedAxisXMotion(
        spatialSpec = hierarchySpatialSpec,
        effectsSpec = hierarchyEffectsSpec,
        direction = BACKWARD,
        slideDistancePx = hierarchySlideDistancePx,
        initialAlpha = KbMotion.HIERARCHY_INITIAL_ALPHA,
    )
}

private fun Scene<NavKey>.motionOwner(): AppDestination? {
    val ownerName = entries.lastOrNull()?.metadata?.get(MOTION_OWNER_KEY) as? String ?: return null
    return AppDestination.entries.firstOrNull { it.name == ownerName }
}

private const val FORWARD = 1
private const val BACKWARD = -1
