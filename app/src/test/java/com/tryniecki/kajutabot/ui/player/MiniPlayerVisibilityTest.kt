package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.ui.navigation.AppDestination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerVisibilityTest {

    @Test
    fun `favorites with now playing shows mini player`() {
        assertTrue(
            shouldShowMiniPlayer(
                isAuthenticated = true,
                isBottomBarVisible = true,
                destination = AppDestination.FAVORITES,
                hasNowPlaying = true,
            ),
        )
    }

    @Test
    fun `more with now playing shows mini player`() {
        assertTrue(
            shouldShowMiniPlayer(
                isAuthenticated = true,
                isBottomBarVisible = true,
                destination = AppDestination.MORE,
                hasNowPlaying = true,
            ),
        )
    }

    @Test
    fun `my audio with now playing shows mini player`() {
        assertTrue(
            shouldShowMiniPlayer(
                isAuthenticated = true,
                isBottomBarVisible = true,
                destination = AppDestination.MY_AUDIO,
                hasNowPlaying = true,
            ),
        )
    }

    @Test
    fun `player tab never shows mini player`() {
        assertFalse(
            shouldShowMiniPlayer(
                isAuthenticated = true,
                isBottomBarVisible = true,
                destination = AppDestination.PLAYER,
                hasNowPlaying = true,
            ),
        )
    }

    @Test
    fun `fullscreen modal hides mini player`() {
        assertFalse(
            shouldShowMiniPlayer(
                isAuthenticated = true,
                isBottomBarVisible = false,
                destination = AppDestination.FAVORITES,
                hasNowPlaying = true,
            ),
        )
    }

    @Test
    fun `idle queue hides mini player`() {
        assertFalse(
            shouldShowMiniPlayer(
                isAuthenticated = true,
                isBottomBarVisible = true,
                destination = AppDestination.FAVORITES,
                hasNowPlaying = false,
            ),
        )
    }

    @Test
    fun `signed out hides mini player`() {
        assertFalse(
            shouldShowMiniPlayer(
                isAuthenticated = false,
                isBottomBarVisible = true,
                destination = AppDestination.FAVORITES,
                hasNowPlaying = true,
            ),
        )
    }
}
