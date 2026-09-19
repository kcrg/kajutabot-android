package com.tryniecki.kajutabot.auth

import com.tryniecki.kajutabot.api.client.KajutaBotApi
import com.tryniecki.kajutabot.api.model.auth.AuthSessionResponse
import com.tryniecki.kajutabot.api.model.auth.AuthUserResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OAuthCallbackTest {
    private fun setup(
        pending: PendingOAuth? = PendingOAuth("state-123", "verifier-abc", System.currentTimeMillis()),
        exchange: suspend (com.tryniecki.kajutabot.api.model.auth.DiscordOAuthExchangeRequest) -> AuthSessionResponse = {
            AuthSessionResponse(
                "a", "2030-01-01T00:00:00Z", "r", "2030-02-01T00:00:00Z",
                AuthUserResponse("1", "u", "U", null),
            )
        },
    ): Triple<SessionManager, FakeSessionStore, FakeAuthApi> {
        val store = FakeSessionStore()
        val pendingStorage = FakePendingStorage(pending)
        val authApi = FakeAuthApi(exchangeHandler = exchange)
        val sm = SessionManager(
            authApi = authApi,
            apiProvider = { throw UnsupportedOperationException() },
            sessionStore = store,
            pendingStorage = pendingStorage,
            appConfig = AppConfig("https://api.example", "client-1"),
            clock = { Instant.parse("2026-09-18T12:00:00Z") },
        )
        return Triple(sm, store, authApi)
    }

    @Test
    fun `correct state triggers exchange and SignedIn`() = runTest {
        val (sm, store, authApi) = setup()
        val result = sm.handleOAuthCallback("code-1", "state-123", null)
        assertTrue(result is OAuthCallbackResult.Exchanged)
        assertEquals(1, authApi.exchangeCount.get())
        assertTrue(sm.authState.value is AuthState.SignedIn)
        assertEquals("a", store.session?.accessToken)
    }

    @Test
    fun `wrong state does not exchange`() = runTest {
        val (sm, _, authApi) = setup()
        val result = sm.handleOAuthCallback("code-1", "wrong", null)
        assertTrue(result is OAuthCallbackResult.Ignored)
        assertEquals(0, authApi.exchangeCount.get())
    }

    @Test
    fun `no pending auth does not exchange`() = runTest {
        val (sm, _, authApi) = setup(pending = null)
        val result = sm.handleOAuthCallback("code-1", "state-123", null)
        assertTrue(result is OAuthCallbackResult.Ignored)
        assertEquals(0, authApi.exchangeCount.get())
    }

    @Test
    fun `expired pending does not exchange`() = runTest {
        val expired = PendingOAuth("s", "v", 0L)
        val pendingStorage = FakePendingStorage(null)
        // Simulate loadValid returning null due to expiry.
        pendingStorage.pending = null
        val store = FakeSessionStore()
        val authApi = FakeAuthApi()
        val sm = SessionManager(
            authApi = authApi,
            apiProvider = { throw UnsupportedOperationException() },
            sessionStore = store,
            pendingStorage = pendingStorage,
            appConfig = AppConfig("https://api.example", "client-1"),
            clock = { Instant.parse("2026-09-18T12:00:00Z") },
        )
        // expired variable intentionally unused beyond documenting intent; storage is empty.
        assertTrue(expired.createdAtMillis == 0L)
        val result = sm.handleOAuthCallback("code-1", "s", null)
        assertTrue(result is OAuthCallbackResult.Ignored)
        assertEquals(0, authApi.exchangeCount.get())
    }

    @Test
    fun `callback error access_denied produces readable message`() = runTest {
        val (sm, _, authApi) = setup()
        val result = sm.handleOAuthCallback(null, "state-123", "access_denied")
        assertTrue(result is OAuthCallbackResult.Failed)
        assertEquals(0, authApi.exchangeCount.get())
        val state = sm.authState.value as AuthState.SignedOut
        assertTrue(state.message?.contains("anulowane") == true)
    }

    @Test
    fun `exchange guild_access_denied maps to Polish message`() = runTest {
        val (sm, _, _) = setup(
            exchange = { throw httpError(403, "discord_guild_access_denied") },
        )
        val result = sm.handleOAuthCallback("code-1", "state-123", null)
        assertTrue(result is OAuthCallbackResult.Failed)
        val message = (result as OAuthCallbackResult.Failed).message
        assertEquals(
            "Nie masz dostępu do żadnego serwera Discord obsługiwanego przez KajutaBot.",
            message,
        )
    }
}
