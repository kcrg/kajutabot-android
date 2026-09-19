package com.tryniecki.kajutabot.ui.app

import com.tryniecki.kajutabot.ui.player.GuildAccessState
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthenticatedGateTest {
    @Test
    fun `first successful access check opens onboarding`() {
        assertEquals(
            AuthenticatedGate.ONBOARDING,
            resolveAuthenticatedGate(
                guildAccessState = GuildAccessState.AVAILABLE,
                onboardingCompleted = false,
                manualOnboardingRequested = false,
            ),
        )
    }

    @Test
    fun `completed onboarding opens app content`() {
        assertEquals(
            AuthenticatedGate.CONTENT,
            resolveAuthenticatedGate(
                guildAccessState = GuildAccessState.AVAILABLE,
                onboardingCompleted = true,
                manualOnboardingRequested = false,
            ),
        )
    }

    @Test
    fun `manual onboarding overrides completed flag only when access exists`() {
        assertEquals(
            AuthenticatedGate.ONBOARDING,
            resolveAuthenticatedGate(
                guildAccessState = GuildAccessState.AVAILABLE,
                onboardingCompleted = true,
                manualOnboardingRequested = true,
            ),
        )
        assertEquals(
            AuthenticatedGate.NO_ACCESS,
            resolveAuthenticatedGate(
                guildAccessState = GuildAccessState.NONE,
                onboardingCompleted = true,
                manualOnboardingRequested = true,
            ),
        )
    }

    @Test
    fun `network failure is not treated as no access`() {
        assertEquals(
            AuthenticatedGate.ACCESS_ERROR,
            resolveAuthenticatedGate(
                guildAccessState = GuildAccessState.ERROR,
                onboardingCompleted = false,
                manualOnboardingRequested = false,
            ),
        )
    }
}
