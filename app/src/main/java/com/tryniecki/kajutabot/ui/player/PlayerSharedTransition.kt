package com.tryniecki.kajutabot.ui.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private const val PLAYER_ARTWORK_SHARED_KEY_PREFIX = "player-artwork:"
private const val PLAYER_TITLE_SHARED_KEY_PREFIX = "player-title:"

/**
 * Hero transition shared by the compact MiniPlayer artwork and the full Player artwork.
 * The playback identity is part of the key so a skip during navigation can never morph
 * one track into another.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.playerArtworkSharedElement(
    playbackIdentity: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return this

    return with(sharedTransitionScope) {
        this@playerArtworkSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(
                key = PLAYER_ARTWORK_SHARED_KEY_PREFIX + playbackIdentity,
            ),
            animatedVisibilityScope = animatedVisibilityScope,
            zIndexInOverlay = 1f,
        )
    }
}

/** Smoothly carries the track title between MiniPlayer and Player despite typography changes. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.playerTitleSharedBounds(
    playbackIdentity: String,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return this

    return with(sharedTransitionScope) {
        this@playerTitleSharedBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(
                key = PLAYER_TITLE_SHARED_KEY_PREFIX + playbackIdentity,
            ),
            animatedVisibilityScope = animatedVisibilityScope,
            zIndexInOverlay = 2f,
        )
    }
}
