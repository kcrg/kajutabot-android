package com.tryniecki.kajutabot.auth

import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.api.client.KajutaBotApi
import com.tryniecki.kajutabot.api.client.KajutaBotAuthApi
import com.tryniecki.kajutabot.api.model.auth.AuthSessionResponse
import com.tryniecki.kajutabot.api.model.auth.AuthUserResponse
import com.tryniecki.kajutabot.api.model.auth.DiscordOAuthExchangeRequest
import com.tryniecki.kajutabot.api.model.auth.RefreshUserSessionRequest
import com.tryniecki.kajutabot.api.model.auth.SessionType
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.api.model.favorites.AddFavoriteRequest
import com.tryniecki.kajutabot.api.model.favorites.FavoriteResponse
import com.tryniecki.kajutabot.api.model.favorites.QueueFavoritesRequest
import com.tryniecki.kajutabot.api.model.queue.EnqueueRequest
import com.tryniecki.kajutabot.api.model.queue.MoveQueueEntryRequest
import com.tryniecki.kajutabot.api.model.queue.QueueMutationRequest
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.queue.SetQueueRepeatRequest
import com.tryniecki.kajutabot.api.model.queue.SkipQueueRequest
import com.tryniecki.kajutabot.api.model.queue.SwapQueueEntriesRequest
import com.tryniecki.kajutabot.api.model.radio.EnableRadioRequest
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import com.tryniecki.kajutabot.api.model.search.SearchResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FakeSessionStore(var session: UserSession? = null) : SessionStore {
    override fun save(session: UserSession) {
        this.session = session
    }

    override fun load(): UserSession? = session

    override fun clear() {
        session = null
    }
}

class FakePendingStorage(var pending: PendingOAuth? = null) : OAuthPendingStorage {
    var saveCount = 0
    var clearCount = 0

    override fun save(state: String, codeVerifier: String) {
        saveCount++
        pending = PendingOAuth(state, codeVerifier, System.currentTimeMillis())
    }

    override fun loadValid(): PendingOAuth? = pending

    override fun clear() {
        clearCount++
        pending = null
    }
}

class FakeAuthApi(
    var refreshHandler: suspend (String) -> AuthSessionResponse = { throw IOException("no stub") },
    var exchangeHandler: suspend (DiscordOAuthExchangeRequest) -> AuthSessionResponse = { throw IOException("no stub") },
    var guestHandler: suspend () -> AuthSessionResponse = { throw IOException("no stub") },
) : KajutaBotAuthApi {
    val refreshCount = AtomicInteger(0)
    val exchangeCount = AtomicInteger(0)
    val guestCount = AtomicInteger(0)

    override suspend fun guest(): AuthSessionResponse {
        guestCount.incrementAndGet()
        return guestHandler()
    }

    override suspend fun exchange(request: DiscordOAuthExchangeRequest): AuthSessionResponse {
        exchangeCount.incrementAndGet()
        return exchangeHandler(request)
    }

    override suspend fun refresh(request: RefreshUserSessionRequest): AuthSessionResponse {
        refreshCount.incrementAndGet()
        return refreshHandler(request.refreshToken)
    }
}

private fun testUser() = AuthUserResponse("123", "kajuta", "Kajuta", null)

private fun sessionWith(
    access: String = "access-1",
    accessExp: String = "2030-01-01T00:00:00Z",
    refresh: String = "refresh-1",
    refreshExp: String = "2030-02-01T00:00:00Z",
) = UserSession(access, accessExp, refresh, refreshExp, testUser())

private fun authResponse(
    access: String = "access-2",
    refresh: String = "refresh-2",
) = AuthSessionResponse(
    accessToken = access,
    accessTokenExpiresAtUtc = "2030-01-01T00:00:00Z",
    refreshToken = refresh,
    refreshTokenExpiresAtUtc = "2030-02-01T00:00:00Z",
    user = testUser(),
)

private fun guestResponse(access: String = "guest-access", expires: String = "2030-01-01T00:00:00Z") =
    AuthSessionResponse(access, expires, null, null, AuthUserResponse("guest", "guest", "Gość"), SessionType.GUEST)

private fun guestSession(access: String = "guest-access", expires: String = "2030-01-01T00:00:00Z") =
    UserSession(access, expires, null, null, AuthUserResponse("guest", "guest", "Gość"), SessionType.GUEST)

fun httpError(code: Int, errorCode: String?): HttpException {
    val body = if (errorCode != null) {
        """{"errorCode":"$errorCode"}"""
    } else {
        "{}"
    }
    val responseBody = body.toResponseBody("application/json".toMediaType())
    return HttpException(Response.error<Any>(code, responseBody))
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {
    private fun manager(
        store: FakeSessionStore = FakeSessionStore(),
        pending: FakePendingStorage = FakePendingStorage(),
        authApi: FakeAuthApi = FakeAuthApi(),
        apiProvider: () -> KajutaBotApi = { throw UnsupportedOperationException() },
        clientId: String = "test-client",
        refreshScope: CoroutineScope? = null,
    ): SessionManager {
        return SessionManager(
            authApi = authApi,
            apiProvider = { apiProvider() },
            sessionStore = store,
            pendingStorage = pending,
            appConfig = AppConfig("https://api.example", clientId),
            clock = { Instant.parse("2026-09-18T12:00:00Z") },
            refreshScope = refreshScope ?: CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO),
        )
    }

    @Test
    fun `guest login stores a valid session without refresh credentials`() = runTest {
        val store = FakeSessionStore()
        val auth = FakeAuthApi(guestHandler = { guestResponse() })
        val sm = manager(store = store, authApi = auth)
        sm.restore()
        assertEquals(GuestLoginResult.SignedIn, sm.continueAsGuest())
        assertEquals(SessionType.GUEST, store.session?.sessionType)
        assertNull(store.session?.refreshToken)
        assertNull(store.session?.refreshTokenExpiresAtUtc)
        assertTrue(sm.authState.value is AuthState.SignedIn)
        assertEquals(1, auth.guestCount.get())
    }

    @Test
    fun `discord session without refresh token is rejected on restore`() = runTest {
        val store = FakeSessionStore(sessionWith().copy(refreshToken = null))
        val sm = manager(store = store)
        sm.restore()
        assertTrue(sm.authState.value is AuthState.SignedOut)
        assertNull(store.session)
    }

    @Test
    fun `expired guest restore renews with guest endpoint and preserves identity`() = runTest {
        val store = FakeSessionStore(guestSession(expires = "2020-01-01T00:00:00Z"))
        val auth = FakeAuthApi(guestHandler = { guestResponse(access = "renewed") })
        val sm = manager(store = store, authApi = auth)
        sm.restore()
        assertEquals("renewed", store.session?.accessToken)
        assertEquals(SessionType.GUEST, store.session?.sessionType)
        assertEquals(1, auth.guestCount.get())
        assertEquals(0, auth.refreshCount.get())
        assertTrue(sm.authState.value is AuthState.SignedIn)
        val identity = checkNotNull(sm.sessionIdentity.value)
        assertEquals("renewed", sm.accessTokenForSession(identity))
    }

    @Test
    fun `concurrent guest renewals share one flight`() = runTest {
        val reply = CompletableDeferred<AuthSessionResponse>()
        val store = FakeSessionStore(guestSession(expires = "2020-01-01T00:00:00Z"))
        val auth = FakeAuthApi(guestHandler = { reply.await() })
        val api = object : KajutaBotApi by unsupportedApi() {
            override suspend fun getQueue(guildId: String) = emptySnapshot(guildId)
        }
        val sm = manager(store = store, authApi = auth, apiProvider = { api }, refreshScope = backgroundScope)
        val jobs = (1..8).map {
            async(start = CoroutineStart.UNDISPATCHED) { sm.withApi { it.getQueue("demo") } }
        }
        runCurrent()
        assertEquals(1, auth.guestCount.get())
        reply.complete(guestResponse("renewed"))
        jobs.awaitAll()
        assertEquals(1, auth.guestCount.get())
    }

    @Test
    fun `disabled guest access clears restored session while network error keeps it`() = runTest {
        val disabledStore = FakeSessionStore(guestSession(expires = "2020-01-01T00:00:00Z"))
        val disabled = manager(disabledStore, authApi = FakeAuthApi(guestHandler = {
            throw httpError(404, "guest_access_disabled")
        }))
        disabled.restore()
        assertTrue(disabled.authState.value is AuthState.SignedOut)
        assertNull(disabledStore.session)

        val offlineStore = FakeSessionStore(guestSession(expires = "2020-01-01T00:00:00Z"))
        val offline = manager(offlineStore, authApi = FakeAuthApi(guestHandler = { throw IOException("offline") }))
        offline.restore()
        assertTrue(offline.authState.value is AuthState.RecoverableError)
        assertEquals(SessionType.GUEST, offlineStore.session?.sessionType)
    }

    @Test
    fun `guest logout is local and Discord login gets a new session identity`() = runTest {
        val store = FakeSessionStore()
        val pending = FakePendingStorage()
        val auth = FakeAuthApi(
            guestHandler = { guestResponse() },
            exchangeHandler = { authResponse() },
        )
        val sm = manager(store = store, pending = pending, authApi = auth)
        sm.restore()
        sm.continueAsGuest()
        val guestIdentity = sm.sessionIdentity.value
        assertEquals(LogoutResult.SignedOut, sm.logout())
        assertNull(store.session)
        assertNull(sm.sessionIdentity.value)
        assertEquals(0, auth.refreshCount.get())
        pending.pending = PendingOAuth("discord-state", "verifier", System.currentTimeMillis())
        assertEquals(OAuthCallbackResult.Exchanged, sm.handleOAuthCallback("code", "discord-state", null))
        assertEquals(SessionType.DISCORD, store.session?.sessionType)
        assertTrue(guestIdentity != sm.sessionIdentity.value)
    }

    @Test
    fun `guest renewal finishing after logout cannot resurrect session`() = runTest {
        val reply = CompletableDeferred<AuthSessionResponse>()
        val store = FakeSessionStore(guestSession(expires = "2020-01-01T00:00:00Z"))
        val auth = FakeAuthApi(guestHandler = { reply.await() })
        val sm = manager(store = store, authApi = auth, refreshScope = backgroundScope)
        val renewal = async(start = CoroutineStart.UNDISPATCHED) {
            try {
                sm.withApi { it.getQueue("demo") }
                false
            } catch (_: SessionSignedOutException) {
                true
            }
        }
        runCurrent()
        assertEquals(1, auth.guestCount.get())
        assertEquals(LogoutResult.SignedOut, sm.logout())
        reply.complete(guestResponse("late-token"))
        runCurrent()
        assertTrue(renewal.await())
        assertNull(store.session)
        assertNull(sm.sessionIdentity.value)
    }

    @Test
    fun `restore with no session goes SignedOut`() = runTest {
        val sm = manager()
        sm.restore()
        assertTrue(sm.authState.value is AuthState.SignedOut)
    }

    @Test
    fun `restore with valid access goes SignedIn without refresh`() = runTest {
        val store = FakeSessionStore(sessionWith())
        val authApi = FakeAuthApi()
        val sm = manager(store = store, authApi = authApi)
        sm.restore()
        assertTrue(sm.authState.value is AuthState.SignedIn)
        assertEquals(0, authApi.refreshCount.get())
    }

    @Test
    fun `realtime token lookup is bound to current session identity`() = runTest {
        val sm = manager(store = FakeSessionStore(sessionWith()))
        sm.restore()
        val identity = checkNotNull(sm.sessionIdentity.value)
        assertEquals("access-1", sm.accessTokenForSession(identity))
        try {
            sm.accessTokenForSession(identity + 1)
            org.junit.Assert.fail("Expected stale session to be rejected")
        } catch (_: SessionSignedOutException) {
        }
    }

    @Test
    fun `restore with expired access refreshes to SignedIn`() = runTest {
        val store = FakeSessionStore(sessionWith(accessExp = "2020-01-01T00:00:00Z"))
        val authApi = FakeAuthApi(refreshHandler = { authResponse() })
        val sm = manager(store = store, authApi = authApi)
        sm.restore()
        assertTrue(sm.authState.value is AuthState.SignedIn)
        assertEquals(1, authApi.refreshCount.get())
        assertEquals("access-2", store.session?.accessToken)
        assertEquals("refresh-2", store.session?.refreshToken)
    }

    @Test
    fun `restore network error keeps session and emits RecoverableError`() = runTest {
        val store = FakeSessionStore(sessionWith(accessExp = "2020-01-01T00:00:00Z"))
        val authApi = FakeAuthApi(refreshHandler = { throw IOException("down") })
        val sm = manager(store = store, authApi = authApi)
        sm.restore()
        assertTrue(sm.authState.value is AuthState.RecoverableError)
        // Session must NOT be cleared.
        assertEquals("refresh-1", store.session?.refreshToken)
    }

    @Test
    fun `invalid refresh codes clear session`() = runTest {
        val codes = listOf(
            "invalid_refresh_token",
            "expired_refresh_token",
            "revoked_session",
            "refresh_token_reuse_detected",
        )
        for (code in codes) {
            val store = FakeSessionStore(sessionWith(accessExp = "2020-01-01T00:00:00Z"))
            val authApi = FakeAuthApi(refreshHandler = { throw httpError(401, code) })
            val sm = manager(store = store, authApi = authApi)
            sm.restore()
            assertTrue("expected SignedOut for $code", sm.authState.value is AuthState.SignedOut)
            assertNull("expected cleared for $code", store.session)
        }
    }

    @Test
    fun `HTTP 503 during refresh keeps session`() = runTest {
        val store = FakeSessionStore(sessionWith(accessExp = "2020-01-01T00:00:00Z"))
        val authApi = FakeAuthApi(refreshHandler = { throw httpError(503, null) })
        val sm = manager(store = store, authApi = authApi)
        sm.restore()
        assertTrue(sm.authState.value is AuthState.RecoverableError)
        assertEquals("refresh-1", store.session?.refreshToken)
    }

    @Test
    fun `concurrent refresh performs exactly one backend call`() = runTest {
        val store = FakeSessionStore(sessionWith(accessExp = "2020-01-01T00:00:00Z"))
        val authApi = FakeAuthApi(
            refreshHandler = {
                delay(50)
                authResponse()
            },
        )
        val fakeApi = object : KajutaBotApi by unsupportedApi() {
            override suspend fun getQueue(guildId: String): QueueSnapshotResponse {
                return emptySnapshot(guildId)
            }
        }
        val sm = manager(store = store, authApi = authApi, apiProvider = { fakeApi })
        sm.restore()
        // Force expiry again for withApi path: set stored session back to expired but keep new refresh?
        // Instead test directly: reset to expired and run concurrent withApi.
        store.session = sessionWith(accessExp = "2020-01-01T00:00:00Z")
        // Need fresh manager state: create new manager sharing same store/api to simulate app restart.
        val authApi2 = FakeAuthApi(refreshHandler = { delay(50); authResponse("a3", "r3") })
        val sm2 = manager(store = store, authApi = authApi2, apiProvider = { fakeApi })
        // Preload currentSession via restore without refresh? Use valid? Manually: call restore with valid? Simpler:
        // Load expired into sm2 by calling restore which will trigger one refresh; we want concurrent, so bypass restore:
        // Use reflection-free approach: call withApi concurrently before any restore; withApi loads from store.
        val jobs = (1..10).map {
            async {
                sm2.withApi { it.getQueue("g1") }
            }
        }
        jobs.awaitAll()
        assertEquals(1, authApi2.refreshCount.get())
    }

    @Test
    fun `401 retry succeeds once then returns data`() = runTest {
        val store = FakeSessionStore(sessionWith())
        val authApi = FakeAuthApi(refreshHandler = { authResponse("fresh-access", "fresh-refresh") })
        var calls = 0
        val fakeApi = object : KajutaBotApi by unsupportedApi() {
            override suspend fun getQueue(guildId: String): QueueSnapshotResponse {
                calls++
                if (calls == 1) throw httpError(401, null)
                return emptySnapshot(guildId)
            }
        }
        val sm = manager(store = store, authApi = authApi, apiProvider = { fakeApi })
        sm.restore()
        val snapshot = sm.withApi { it.getQueue("g1") }
        assertEquals("g1", snapshot.guildId)
        assertEquals(2, calls)
    }

    @Test
    fun `second 401 does not loop`() = runTest {
        val store = FakeSessionStore(sessionWith())
        val authApi = FakeAuthApi(refreshHandler = { authResponse("fresh-access", "fresh-refresh") })
        var calls = 0
        val fakeApi = object : KajutaBotApi by unsupportedApi() {
            override suspend fun getQueue(guildId: String): QueueSnapshotResponse {
                calls++
                throw httpError(401, null)
            }
        }
        val sm = manager(store = store, authApi = authApi, apiProvider = { fakeApi })
        sm.restore()
        try {
            sm.withApi { it.getQueue("g1") }
            assertTrue("expected HttpException", false)
        } catch (_: HttpException) {
        }
        // 1 initial + 1 retry = 2, no more.
        assertEquals(2, calls)
    }

    @Test
    fun cancelledFirstWaiterDoesNotCancelSharedRotation() = runTest {
        val release = CompletableDeferred<Unit>()
        val store = FakeSessionStore(sessionWith(accessExp = "2020-01-01T00:00:00Z"))
        val auth = FakeAuthApi(refreshHandler = { release.await(); authResponse() })
        val api = object : KajutaBotApi by unsupportedApi() {
            override suspend fun getQueue(guildId: String) = emptySnapshot(guildId)
        }
        val sm = manager(store = store, authApi = auth, apiProvider = { api }, refreshScope = backgroundScope)
        val first = async(start = CoroutineStart.UNDISPATCHED) { sm.withApi { it.getQueue("g") } }
        runCurrent()
        assertEquals(1, auth.refreshCount.get())
        first.cancel()
        val second = async(start = CoroutineStart.UNDISPATCHED) { sm.withApi { it.getQueue("g") } }
        release.complete(Unit)
        runCurrent()
        assertEquals("g", second.await().guildId)
        assertEquals(1, auth.refreshCount.get())
        assertEquals("refresh-2", store.session?.refreshToken)
    }

    @Test
    fun logoutDuringRefreshCannotRestoreOldSession() = runTest {
        val release = CompletableDeferred<Unit>()
        val store = FakeSessionStore(sessionWith(accessExp = "2020-01-01T00:00:00Z"))
        val auth = FakeAuthApi(refreshHandler = { release.await(); authResponse() })
        var logoutToken: String? = null
        lateinit var sm: SessionManager
        val api = object : KajutaBotApi by unsupportedApi() {
            override suspend fun logout() {
                logoutToken = sm.currentAccessToken()
            }
        }
        sm = manager(store = store, authApi = auth, apiProvider = { api }, refreshScope = backgroundScope)
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            try {
                sm.withApi { it.getQueue("g") }
                false
            } catch (_: SessionSignedOutException) {
                true
            }
        }
        runCurrent()
        val logout = async(start = CoroutineStart.UNDISPATCHED) { sm.logout() }
        release.complete(Unit)
        runCurrent()
        assertEquals(LogoutResult.SignedOut, logout.await())
        assertEquals("access-2", logoutToken)
        assertNull(store.session)
        assertNull(sm.currentUserSession())
        assertTrue(sm.authState.value is AuthState.SignedOut)
        assertTrue(waiter.await())
    }

    @Test
    fun olderExchangeCannotReplaceNewerLogin() = runTest {
        val firstReply = CompletableDeferred<AuthSessionResponse>()
        val secondReply = CompletableDeferred<AuthSessionResponse>()
        val pending = FakePendingStorage(PendingOAuth("first-state", "v1", System.currentTimeMillis()))
        val store = FakeSessionStore()
        val auth = FakeAuthApi(exchangeHandler = {
            if (it.code == "first") firstReply.await() else secondReply.await()
        })
        val sm = manager(store = store, pending = pending, authApi = auth)
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            sm.handleOAuthCallback("first", "first-state", null)
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            sm.handleOAuthCallback("second", "first-state", null)
        }
        secondReply.complete(authResponse("account-B", "refresh-B"))
        assertEquals(OAuthCallbackResult.Exchanged, second.await())
        firstReply.complete(authResponse("account-A", "refresh-A"))
        assertTrue(first.await() is OAuthCallbackResult.Ignored)
        assertEquals("account-B", store.session?.accessToken)
    }

    @Test
    fun malformedRefreshIsContractError() = runTest {
        val store = FakeSessionStore(sessionWith(accessExp = "2020-01-01T00:00:00Z"))
        val sm = manager(
            store = store,
            authApi = FakeAuthApi(refreshHandler = { authResponse(access = "") }),
        )
        sm.restore()
        assertTrue(sm.authState.value is AuthState.RecoverableError)
        assertEquals("refresh-1", store.session?.refreshToken)
    }

    @Test
    fun expiredAccessTokenIsRefreshedBeforeServerLogout() = runTest {
        val store = FakeSessionStore(sessionWith(accessExp = "2020-01-01T00:00:00Z"))
        lateinit var sm: SessionManager
        var tokenAtLogout: String? = null
        var refreshAttempts = 0
        val api = object : KajutaBotApi by unsupportedApi() {
            override suspend fun logout() {
                tokenAtLogout = sm.currentAccessToken()
            }
        }
        sm = manager(
            store = store,
            authApi = FakeAuthApi(refreshHandler = {
                refreshAttempts++
                if (refreshAttempts == 1) throw IOException("offline during restore")
                authResponse()
            }),
            apiProvider = { api },
        )
        sm.restore()
        assertTrue(sm.authState.value is AuthState.RecoverableError)
        assertEquals(LogoutResult.SignedOut, sm.logout())
        assertEquals("access-2", tokenAtLogout)
        assertNull(store.session)
    }

    @Test
    fun oldRestoreCannotOverwriteNewExchange() = runTest {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val old = sessionWith(access = "old")
        val storage = object : SessionStore {
            var session: UserSession? = old
            override fun load(): UserSession? {
                val snapshot = session
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return snapshot
            }
            override fun save(session: UserSession) { this.session = session }
            override fun clear() { session = null }
        }
        val sm = SessionManager(
            authApi = FakeAuthApi(exchangeHandler = { authResponse("new", "new-refresh") }),
            apiProvider = { unsupportedApi() },
            sessionStore = storage,
            pendingStorage = FakePendingStorage(PendingOAuth("state", "verifier", System.currentTimeMillis())),
            appConfig = AppConfig("https://api.example", "client"),
            clock = { Instant.parse("2026-09-18T12:00:00Z") },
        )
        val restoring = async(Dispatchers.Default) { sm.restore() }
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertEquals(OAuthCallbackResult.Exchanged, sm.handleOAuthCallback("code", "state", null))
        release.countDown()
        restoring.await()
        assertEquals("new", sm.currentAccessToken())
        assertEquals("new", storage.session?.accessToken)
    }

    @Test
    fun storageFailureAfterSuccessfulRefreshIsNotReportedAsNetworkError() = runTest {
        val old = sessionWith(accessExp = "2020-01-01T00:00:00Z")
        val storage = object : SessionStore {
            override fun load() = old
            override fun save(session: UserSession) { throw IllegalStateException("disk full") }
            override fun clear() = Unit
        }
        val sm = SessionManager(
            authApi = FakeAuthApi(refreshHandler = { authResponse() }),
            apiProvider = { unsupportedApi() },
            sessionStore = storage,
            pendingStorage = FakePendingStorage(),
            appConfig = AppConfig("https://api.example", "client"),
            clock = { Instant.parse("2026-09-18T12:00:00Z") },
        )
        sm.restore()
        val error = sm.authState.value as AuthState.RecoverableError
        assertEquals(R.string.auth_refreshed_session_save_failed, (error.message as UiText.Resource).id)
        assertEquals("access-1", sm.currentAccessToken())
    }

    @Test
    fun restoreStorageFailureIsRecoverablePersistenceError() = runTest {
        val storage = object : SessionStore {
            override fun load(): UserSession? = throw IllegalStateException("Keystore unavailable")
            override fun save(session: UserSession) = Unit
            override fun clear() = Unit
        }
        val sm = SessionManager(
            authApi = FakeAuthApi(),
            apiProvider = { unsupportedApi() },
            sessionStore = storage,
            pendingStorage = FakePendingStorage(),
            appConfig = AppConfig("https://api.example", "client"),
        )
        sm.restore()
        assertEquals(R.string.auth_secure_store_read_failed, ((sm.authState.value as AuthState.RecoverableError).message as UiText.Resource).id)
    }

    @Test
    fun expiredLogoutWithRejectedRefreshIsLocalOnly() = runTest {
        val store = FakeSessionStore(sessionWith(accessExp = "2020-01-01T00:00:00Z"))
        var attempts = 0
        val auth = FakeAuthApi(refreshHandler = {
            attempts++
            if (attempts == 1) throw IOException("offline during restore")
            throw httpError(401, "expired_refresh_token")
        })
        var serverLogoutCalls = 0
        val api = object : KajutaBotApi by unsupportedApi() {
            override suspend fun logout() { serverLogoutCalls++ }
        }
        val sm = manager(store = store, authApi = auth, apiProvider = { api })
        sm.restore()
        assertTrue(sm.logout() is LogoutResult.LocalOnly)
        assertEquals(0, serverLogoutCalls)
        assertNull(store.session)
        assertEquals(R.string.auth_refresh_revoke_not_confirmed_local, ((sm.authState.value as AuthState.SignedOut).message as UiText.Resource).id)
    }

    @Test
    fun late401FromOldRequestDoesNotRotateFreshTokenAgain() = runTest {
        val releaseFirst = CompletableDeferred<Unit>()
        val store = FakeSessionStore(sessionWith())
        val auth = FakeAuthApi(refreshHandler = { authResponse("fresh", "fresh-refresh") })
        var calls = 0
        val api = object : KajutaBotApi by unsupportedApi() {
            override suspend fun getQueue(guildId: String): QueueSnapshotResponse {
                calls++
                if (calls == 1) {
                    releaseFirst.await()
                    throw httpErrorWithToken("access-1")
                }
                if (calls == 2) throw httpErrorWithToken("access-1")
                return emptySnapshot(guildId)
            }
        }
        val sm = manager(store = store, authApi = auth, apiProvider = { api }, refreshScope = backgroundScope)
        sm.restore()
        val first = async(start = CoroutineStart.UNDISPATCHED) { sm.withApi { it.getQueue("g") } }
        val second = async(start = CoroutineStart.UNDISPATCHED) { sm.withApi { it.getQueue("g") } }
        runCurrent()
        second.await()
        releaseFirst.complete(Unit)
        runCurrent()
        first.await()
        assertEquals(1, auth.refreshCount.get())
        assertEquals(4, calls)
    }

    @Test
    fun oldAccountRequestKeepsItsTokenAndCannotPublishIntoNewSession() = runTest {
        val releaseA = CompletableDeferred<Unit>()
        val sentTokens = mutableListOf<String>()
        val sm = SessionManager(
            authApi = FakeAuthApi(exchangeHandler = { authResponse("token-B", "refresh-B") }),
            apiProvider = { token ->
                object : KajutaBotApi by unsupportedApi() {
                    override suspend fun getQueue(guildId: String): QueueSnapshotResponse {
                        sentTokens += token
                        if (token == "access-1") releaseA.await()
                        return emptySnapshot(guildId)
                    }
                }
            },
            sessionStore = FakeSessionStore(sessionWith()),
            pendingStorage = FakePendingStorage(PendingOAuth("state", "verifier", System.currentTimeMillis())),
            appConfig = AppConfig("https://api.example", "client"),
            clock = { Instant.parse("2026-09-18T12:00:00Z") },
        )
        sm.restore()
        val oldIdentity = sm.sessionIdentity.value!!
        val old = async(start = CoroutineStart.UNDISPATCHED) {
            try {
                sm.withApiForSession(oldIdentity) { it.getQueue("g") }
                false
            } catch (_: SessionSignedOutException) {
                true
            }
        }
        assertEquals(OAuthCallbackResult.Exchanged, sm.handleOAuthCallback("code", "state", null))
        releaseA.complete(Unit)
        assertTrue(old.await())
        try {
            sm.withApiForSession(oldIdentity) { it.getQueue("g") }
            assertTrue("old session should be rejected before a request", false)
        } catch (_: SessionSignedOutException) {
        }
        assertEquals("g", sm.withApi { it.getQueue("g") }.guildId)
        assertEquals(listOf("access-1", "token-B"), sentTokens)
    }

    private fun httpErrorWithToken(token: String): HttpException {
        val body = "{}".toResponseBody("application/json".toMediaType())
        val request = okhttp3.Request.Builder().url("https://api.example/queue")
            .header("Authorization", "Bearer " + token).build()
        val raw = okhttp3.Response.Builder().request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1).code(401).message("Unauthorized")
            .body(body).build()
        return HttpException(Response.error<Any>(body, raw))
    }

    private fun emptySnapshot(guildId: String) = QueueSnapshotResponse(
        guildId = guildId,
        voiceChannelId = null,
        nowPlaying = null,
        nowPlayingFromRadio = false,
        radio = RadioStateResponse(false),
        pendingEntries = emptyList(),
        pendingDurationMilliseconds = 0,
        version = 1,
    )

    private fun unsupportedApi(): KajutaBotApi = object : KajutaBotApi {
        override suspend fun logout() = throw UnsupportedOperationException()
        override suspend fun getMyGuilds(): List<DiscordGuildResponse> = throw UnsupportedOperationException()
        override suspend fun getVoiceChannels(guildId: String): List<DiscordVoiceChannelResponse> = throw UnsupportedOperationException()
        override suspend fun getQueue(guildId: String): QueueSnapshotResponse = throw UnsupportedOperationException()
        override suspend fun enqueue(guildId: String, request: EnqueueRequest): QueueSnapshotResponse = throw UnsupportedOperationException()
        override suspend fun removeQueueEntry(guildId: String, entryId: String, expectedVersion: Long?): QueueSnapshotResponse = throw UnsupportedOperationException()
        override suspend fun moveQueueEntry(guildId: String, entryId: String, request: MoveQueueEntryRequest): QueueSnapshotResponse = throw UnsupportedOperationException()
        override suspend fun swapQueueEntries(guildId: String, request: SwapQueueEntriesRequest): QueueSnapshotResponse = throw UnsupportedOperationException()
        override suspend fun clearPendingQueue(guildId: String, expectedVersion: Long?): QueueSnapshotResponse = throw UnsupportedOperationException()
        override suspend fun skip(guildId: String, request: SkipQueueRequest): QueueSnapshotResponse = throw UnsupportedOperationException()
        override suspend fun setRepeat(guildId: String, request: SetQueueRepeatRequest): QueueSnapshotResponse = throw UnsupportedOperationException()
        override suspend fun stop(guildId: String, request: QueueMutationRequest): QueueSnapshotResponse = throw UnsupportedOperationException()
        override suspend fun enableRadio(guildId: String, request: EnableRadioRequest): QueueSnapshotResponse = throw UnsupportedOperationException()
        override suspend fun disableRadio(guildId: String, expectedVersion: Long?): QueueSnapshotResponse = throw UnsupportedOperationException()
        override suspend fun search(query: String, source: String, maxResults: Int): SearchResponse = throw UnsupportedOperationException()
        override suspend fun getFavorites(): List<FavoriteResponse> = throw UnsupportedOperationException()
        override suspend fun addFavorite(request: AddFavoriteRequest): FavoriteResponse = throw UnsupportedOperationException()
        override suspend fun deleteFavorite(contentUrl: String): Unit = throw UnsupportedOperationException()
        override suspend fun queueFavorites(request: QueueFavoritesRequest): QueueSnapshotResponse = throw UnsupportedOperationException()
    }
}
