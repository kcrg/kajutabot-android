package com.tryniecki.kajutabot.ui.navigation

import com.tryniecki.kajutabot.api.model.auth.SessionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestNavigationTest {
    @Test
    fun `guest and Discord both have Favorites while guest has no My Audio entry point`() {
        assertTrue(visibleDestinations(SessionType.GUEST).contains(AppDestination.FAVORITES))
        assertTrue(visibleDestinations(SessionType.DISCORD).contains(AppDestination.FAVORITES))
        assertFalse(visibleDestinations(SessionType.GUEST).any { it.route == AppRoute.MyAudio })
        assertEquals(AppDestination.PLAYER, visibleDestinations(SessionType.GUEST).first())
        assertFalse(SessionType.GUEST.canUseUserMedia)
        assertTrue(SessionType.DISCORD.canUseUserMedia)
    }
}
