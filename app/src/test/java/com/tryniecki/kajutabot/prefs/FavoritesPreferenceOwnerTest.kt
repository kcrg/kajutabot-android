package com.tryniecki.kajutabot.prefs

import com.tryniecki.kajutabot.api.model.auth.AuthUserResponse
import com.tryniecki.kajutabot.api.model.auth.SessionType
import com.tryniecki.kajutabot.auth.UserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FavoritesPreferenceOwnerTest {
    private fun session(type: SessionType, token: String, userId: String) = UserSession(
        accessToken = token,
        accessTokenExpiresAtUtc = "2030-01-01T00:00:00Z",
        refreshToken = if (type == SessionType.DISCORD) "refresh" else null,
        refreshTokenExpiresAtUtc = if (type == SessionType.DISCORD) "2030-02-01T00:00:00Z" else null,
        user = AuthUserResponse(userId, "name", "Name"),
        sessionType = type,
    )

    @Test
    fun `guest preference owner ignores JWT rotation and Discord user ID`() {
        val original = session(SessionType.GUEST, "guest-jwt-1", "")
        val renewed = session(SessionType.GUEST, "guest-jwt-2", "different-backend-subject")
        assertEquals("guest", favoritesPreferenceOwnerKey(original))
        assertEquals(favoritesPreferenceOwnerKey(original), favoritesPreferenceOwnerKey(renewed))
    }

    @Test
    fun `Discord retains previous per-user preference keys`() {
        assertEquals("123", favoritesPreferenceOwnerKey(session(SessionType.DISCORD, "jwt", "123")))
        assertNotEquals("123", favoritesPreferenceOwnerKey(session(SessionType.DISCORD, "jwt", "456")))
    }
}
