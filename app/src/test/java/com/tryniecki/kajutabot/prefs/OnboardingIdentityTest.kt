package com.tryniecki.kajutabot.prefs

import com.tryniecki.kajutabot.api.model.auth.SessionType
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingIdentityTest {
    @Test
    fun `guest onboarding uses stable mode key while Discord remains per user`() {
        assertEquals("guest", onboardingIdentityKey(SessionType.GUEST, "changing-token-subject"))
        assertEquals("guest", onboardingIdentityKey(SessionType.GUEST, "another-token-subject"))
        assertEquals("123", onboardingIdentityKey(SessionType.DISCORD, "123"))
        assertEquals("456", onboardingIdentityKey(SessionType.DISCORD, "456"))
    }
}
