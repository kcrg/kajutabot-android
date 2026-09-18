package com.tryniecki.kajutabot.auth

import com.tryniecki.kajutabot.api.client.KajutaBotApi
import com.tryniecki.kajutabot.api.client.KajutaBotAuthApi
import com.tryniecki.kajutabot.api.model.auth.AuthSessionResponse
import com.tryniecki.kajutabot.api.model.auth.AuthUserResponse
import com.tryniecki.kajutabot.api.model.auth.DiscordOAuthExchangeRequest
import com.tryniecki.kajutabot.api.model.auth.RefreshUserSessionRequest
import com.tryniecki.kajutabot.api.model.common.ApiOperationResponse
import com.tryniecki.kajutabot.api.model.common.HealthResponse
import com.tryniecki.kajutabot.api.model.common.TrackResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.api.model.favorites.AddFavoriteRequest
import com.tryniecki.kajutabot.api.model.favorites.FavoriteResponse
import com.tryniecki.kajutabot.api.model.favorites.QueueFavoritesRequest
import com.tryniecki.kajutabot.api.model.queue.EnqueueRequest
import com.tryniecki.kajutabot.api.model.queue.EnqueueResponse
import com.tryniecki.kajutabot.api.model.queue.MoveQueueEntryRequest
import com.tryniecki.kajutabot.api.model.queue.QueueMutationRequest
import com.tryniecki.kajutabot.api.model.queue.QueueMutationResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.queue.SetQueueRepeatRequest
import com.tryniecki.kajutabot.api.model.queue.SkipQueueRequest
import com.tryniecki.kajutabot.api.model.radio.EnableRadioRequest
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import com.tryniecki.kajutabot.api.model.search.SearchResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
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
) : KajutaBotAuthApi {
    val refreshCount = AtomicInteger(0)
    val exchangeCount = AtomicInteger(0)

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

fun httpError(code: Int, errorCode: String?): HttpException {
    val body = if (errorCode != null) {
        """{"errorCode":"$errorCode"}"""
    } else {
        "{}"
    }
    val responseBody = body.toResponseBody("application/json".toMediaType())
    return HttpException(Response.error<Any>(code, responseBody))
}

class SessionManagerTest {
    private fun manager(
        store: FakeSessionStore = FakeSessionStore(),
        pending: FakePendingStorage = FakePendingStorage(),
        authApi: FakeAuthApi = FakeAuthApi(),
        apiProvider: () -> KajutaBotApi = { throw UnsupportedOperationException() },
        clientId: String = "test-client",
    ): SessionManager {
        return SessionManager(
            authApi = authApi,
            apiProvider = apiProvider,
            sessionStore = store,
            pendingStorage = pending,
            appConfig = AppConfig("https://api.example", clientId),
            clock = { Instant.parse("2026-09-18T12:00:00Z") },
        )
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
        override suspend fun getHealth(): HealthResponse = throw UnsupportedOperationException()
        override suspend fun logout() = throw UnsupportedOperationException()
        override suspend fun getMe(): AuthUserResponse = throw UnsupportedOperationException()
        override suspend fun getMyGuilds(): List<DiscordGuildResponse> = throw UnsupportedOperationException()
        override suspend fun getVoiceChannels(guildId: String): List<DiscordVoiceChannelResponse> = throw UnsupportedOperationException()
        override suspend fun getQueue(guildId: String): QueueSnapshotResponse = throw UnsupportedOperationException()
        override suspend fun enqueue(guildId: String, request: EnqueueRequest): EnqueueResponse = throw UnsupportedOperationException()
        override suspend fun removeQueueEntry(guildId: String, entryId: String, expectedVersion: Long?): QueueMutationResponse = throw UnsupportedOperationException()
        override suspend fun moveQueueEntry(guildId: String, entryId: String, request: MoveQueueEntryRequest): QueueMutationResponse = throw UnsupportedOperationException()
        override suspend fun clearPendingQueue(guildId: String, expectedVersion: Long?): QueueMutationResponse = throw UnsupportedOperationException()
        override suspend fun skip(guildId: String, request: SkipQueueRequest): QueueMutationResponse = throw UnsupportedOperationException()
        override suspend fun setRepeat(guildId: String, request: SetQueueRepeatRequest): QueueMutationResponse = throw UnsupportedOperationException()
        override suspend fun stop(guildId: String, request: QueueMutationRequest): QueueMutationResponse = throw UnsupportedOperationException()
        override suspend fun getRadioState(guildId: String): RadioStateResponse = throw UnsupportedOperationException()
        override suspend fun enableRadio(guildId: String, request: EnableRadioRequest): QueueMutationResponse = throw UnsupportedOperationException()
        override suspend fun disableRadio(guildId: String, expectedVersion: Long?): QueueMutationResponse = throw UnsupportedOperationException()
        override suspend fun search(query: String, source: String, maxResults: Int): SearchResponse = throw UnsupportedOperationException()
        override suspend fun getFavorites(): List<FavoriteResponse> = throw UnsupportedOperationException()
        override suspend fun addFavorite(request: AddFavoriteRequest): FavoriteResponse = throw UnsupportedOperationException()
        override suspend fun deleteFavorite(contentUrl: String): Unit = throw UnsupportedOperationException()
        override suspend fun queueFavorites(request: QueueFavoritesRequest): QueueMutationResponse = throw UnsupportedOperationException()
    }
}
