package com.tryniecki.qumi.api.client

import com.tryniecki.qumi.api.model.auth.AuthSessionResponse
import com.tryniecki.qumi.api.model.auth.SessionType
import com.tryniecki.qumi.api.model.error.ProblemDetailsParser
import com.tryniecki.qumi.api.model.queue.SwapQueueEntriesRequest
import com.tryniecki.qumi.api.model.queue.EnqueueRequest
import com.tryniecki.qumi.api.model.queue.QueueMutationRequest
import com.tryniecki.qumi.api.model.queue.SkipQueueRequest
import com.tryniecki.qumi.api.model.queue.SetQueueRepeatRequest
import com.tryniecki.qumi.api.model.queue.MoveQueueEntryRequest
import com.tryniecki.qumi.api.model.radio.EnableRadioRequest
import com.tryniecki.qumi.api.model.favorites.QueueFavoritesRequest
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private lateinit var server: MockWebServer

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `every queue mutation sends expectedQueueVersion and never expectedVersion`() {
        val api = ApiClientFactory.create(server.url("/").toString()) { "token" }
        val response = """{"guildId":"g","voiceChannelId":"c","nowPlaying":null,"nowPlayingFromRadio":false,"radio":{"isEnabled":false},"pendingEntries":[],"pendingEntriesCount":0,"pendingDurationMilliseconds":0,"version":20,"queueVersion":11}"""
        repeat(11) { server.enqueue(MockResponse().setBody(response)) }
        kotlinx.coroutines.runBlocking {
            api.enqueue("g", EnqueueRequest("c", listOf("https://example.test/x"), 10), "operation-key")
            api.skip("g", SkipQueueRequest(expectedQueueVersion = 10), "operation-key")
            api.stop("g", QueueMutationRequest(10), "operation-key")
            api.setRepeat("g", SetQueueRepeatRequest(true, 10), "operation-key")
            api.enableRadio("g", EnableRadioRequest("c", 60, 600, 10), "operation-key")
            api.removeQueueEntry("g", "entry", 10, "operation-key")
            api.clearPendingQueue("g", 10, "operation-key")
            api.disableRadio("g", 10, "operation-key")
            api.moveQueueEntry("g", "entry", MoveQueueEntryRequest(2, 10), "operation-key")
            api.swapQueueEntries("g", SwapQueueEntriesRequest("a", "b", 10), "operation-key")
            api.queueFavorites(QueueFavoritesRequest("g", "c", 10), "operation-key")
        }
        repeat(11) {
            val request = server.takeRequest()
            val sent = request.path.orEmpty() + request.body.readUtf8()
            assertEquals("operation-key", request.getHeader("Idempotency-Key"))
            assertTrue(sent.contains("expectedQueueVersion"))
            assertTrue(sent.contains("10"))
            assertTrue(!sent.contains("expectedVersion"))
        }
    }

    @Test
    fun `favorites radio round trips mode through existing radio endpoint`() {
        val api = ApiClientFactory.create(server.url("/").toString()) { "guest-token" }
        server.enqueue(MockResponse().setBody("""{"guildId":"g","voiceChannelId":"c","nowPlaying":null,"nowPlayingFromRadio":false,"radio":{"isEnabled":true,"mode":"favorites"},"pendingEntries":[],"pendingEntriesCount":0,"pendingDurationMilliseconds":0,"version":20,"queueVersion":11}"""))
        val result = kotlinx.coroutines.runBlocking {
            api.enableRadio("g", EnableRadioRequest("c", 60, 600, 10, mode = "favorites"), "operation-key")
        }
        assertEquals("favorites", result.radio.mode)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertTrue(request.body.readUtf8().contains("\"mode\":\"favorites\""))
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @Test
    fun `history uses guild endpoint and pages of 50 with long cursors`() {
        val api = ApiClientFactory.create(server.url("/").toString()) { "token" }
        server.enqueue(MockResponse().setBody("""{"items":[{"id":9007199254740993,"contentType":"YouTube","contentId":"track","title":"Song","queueInput":"https://example.test/track","thumbnailUrl":null,"startedAtUtc":"2026-10-14T12:00:00+00:00","requestedBy":null,"fromRadio":false,"playCount":2}],"nextCursor":9007199254740993}"""))
        server.enqueue(MockResponse().setBody("""{"items":[],"nextCursor":null}"""))
        kotlinx.coroutines.runBlocking {
            val first = api.getHistory("123")
            assertEquals(9007199254740993L, first.items.single().id)
            assertEquals(9007199254740993L, first.nextCursor)
            assertEquals("2026-10-14T12:00:00+00:00", first.items.single().startedAtUtc)
            assertNull(api.getHistory("123", first.nextCursor).nextCursor)
        }
        val firstRequest = server.takeRequest()
        assertEquals("/api/v1/app/guilds/123/history?pageSize=50", firstRequest.path)
        assertEquals("Bearer token", firstRequest.getHeader("Authorization"))
        assertEquals("/api/v1/app/guilds/123/history?cursor=9007199254740993&pageSize=50", server.takeRequest().path)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `normalizeBaseUrl appends api v1`() {
        assertEquals(
            "https://api.kajuta.tryniecki.eu/api/v1/app/",
            ApiClientFactory.normalizeBaseUrl("https://api.kajuta.tryniecki.eu"),
        )
        assertEquals(
            "https://api.kajuta.tryniecki.eu/api/v1/app/",
            ApiClientFactory.normalizeBaseUrl("https://api.kajuta.tryniecki.eu/"),
        )
        assertEquals(
            "http://localhost:5000/api/v1/app/",
            ApiClientFactory.normalizeBaseUrl("  http://localhost:5000/  "),
        )
    }

    @Test
    fun `Bearer interceptor adds Authorization header`() {
        server.enqueue(MockResponse().setBody("[]"))
        val api = ApiClientFactory.create(server.url("/").toString()) { "token-123" }
        kotlinx.coroutines.runBlocking { api.getMyGuilds() }

        val recorded = server.takeRequest()
        assertEquals("Bearer token-123", recorded.getHeader("Authorization"))
    }

    @Test
    fun `no Authorization header when token null or blank`() {
        server.enqueue(MockResponse().setBody("[]"))
        val apiNull = ApiClientFactory.create(server.url("/").toString()) { null }
        kotlinx.coroutines.runBlocking { apiNull.getMyGuilds() }
        assertNull(server.takeRequest().getHeader("Authorization"))

        server.enqueue(MockResponse().setBody("[]"))
        val apiBlank = ApiClientFactory.create(server.url("/").toString()) { "   " }
        kotlinx.coroutines.runBlocking { apiBlank.getMyGuilds() }
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `never sends X-KajutaBot-Api-Key`() {
        server.enqueue(MockResponse().setBody("[]"))
        val api = ApiClientFactory.create(server.url("/").toString()) { "token-123" }
        kotlinx.coroutines.runBlocking { api.getMyGuilds() }
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("X-KajutaBot-Api-Key"))
    }

    @Test
    fun `auth api sends no Authorization header`() {
        server.enqueue(
            MockResponse().setBody(
                """{"accessToken":"a","accessTokenExpiresAtUtc":"2026-09-18T12:15:00+00:00","refreshToken":"r","refreshTokenExpiresAtUtc":"2026-10-18T12:00:00+00:00","user":{"discordUserId":"1","username":"u","displayName":"d"}}""",
            ),
        )
        val authApi = ApiClientFactory.createAuth(server.url("/").toString())
        kotlinx.coroutines.runBlocking {
            authApi.refresh(com.tryniecki.qumi.api.model.auth.RefreshUserSessionRequest("r"))
        }
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `guest auth uses anonymous POST and parses nullable refresh fields`() {
        server.enqueue(MockResponse().setBody(
            """{"accessToken":"guest-jwt","accessTokenExpiresAtUtc":"2030-01-01T00:00:00Z","refreshToken":null,"refreshTokenExpiresAtUtc":null,"user":{"discordUserId":"guest","username":"guest","displayName":"Gość"},"sessionType":"guest"}""",
        ))
        val authApi = ApiClientFactory.createAuth(server.url("/").toString())
        val result = kotlinx.coroutines.runBlocking { authApi.guest() }
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/app/auth/guest", request.path)
        assertNull(request.getHeader("Authorization"))
        assertEquals(0L, request.body.size)
        assertEquals(SessionType.GUEST, result.sessionType)
        assertNull(result.refreshToken)
        assertNull(result.refreshTokenExpiresAtUtc)
    }

    @Test
    fun `user routes use users me paths`() {
        server.enqueue(MockResponse().setBody("[]"))
        val api = ApiClientFactory.create(server.url("/").toString()) { "t" }
        kotlinx.coroutines.runBlocking { api.getMyGuilds() }
        assertEquals("/api/v1/app/users/me/guilds", server.takeRequest().path)

        server.enqueue(MockResponse().setBody("[]"))
        kotlinx.coroutines.runBlocking { api.getFavorites() }
        assertEquals("/api/v1/app/users/me/favorites", server.takeRequest().path)

        server.enqueue(
            MockResponse().setBody(
                """{"discordUserId":"1","contentUrl":"https://x","contentType":"YouTube","contentId":"abc","title":"t","addedAt":"2026-09-18T12:00:00+00:00"}""",
            ),
        )
        kotlinx.coroutines.runBlocking {
            api.addFavorite(com.tryniecki.qumi.api.model.favorites.AddFavoriteRequest("YouTube", "x"))
        }
        val addFavorite = server.takeRequest()
        assertEquals("/api/v1/app/users/me/favorites", addFavorite.path)
        assertEquals("""{"contentType":"YouTube","contentId":"x"}""", addFavorite.body.readUtf8())

        server.enqueue(MockResponse().setResponseCode(204))
        kotlinx.coroutines.runBlocking { api.deleteFavorite("YouTube", "abc") }
        val delete = server.takeRequest()
        assertTrue(delete.path!!.startsWith("/api/v1/app/users/me/favorites?"))
        assertTrue(delete.path!!.endsWith("?contentType=YouTube&contentId=abc"))
    }

    @Test
    fun `swap queue entries posts contract body and reads direct snapshot`() {
        server.enqueue(MockResponse().setBody("""
            {
              "guildId":"123456789", "voiceChannelId":"987654321", "nowPlaying":null,
              "nowPlayingFromRadio":false, "radio":{"isEnabled":false},
              "pendingEntries":[{"entryId":"33333333-3333-3333-3333-333333333333", "position":1,
                "track":{"contentId":"C","contentType":"YouTube","title":"C",
                  "url":"https://example.test/C","durationMilliseconds":120000,
                  "thumbnailUrl":null,"playCount":0}}],
              "pendingEntriesCount":1, "pendingDurationMilliseconds":120000, "version":124, "queueVersion":123
            }
        """.trimIndent()))
        val api = ApiClientFactory.create(server.url("/").toString()) { "token-123" }
        val snapshot = kotlinx.coroutines.runBlocking {
            api.swapQueueEntries("123456789", SwapQueueEntriesRequest(
                "11111111-1111-1111-1111-111111111111",
                "33333333-3333-3333-3333-333333333333",
                123,
            ), "operation-key")
        }

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/app/guilds/123456789/queue/items/swap", request.path)
        assertEquals("Bearer token-123", request.getHeader("Authorization"))
        val body = json.parseToJsonElement(request.body.readUtf8()).toString()
        assertTrue(body.contains("\"firstEntryId\":\"11111111-1111-1111-1111-111111111111\""))
        assertTrue(body.contains("\"secondEntryId\":\"33333333-3333-3333-3333-333333333333\""))
        assertTrue(body.contains("\"expectedQueueVersion\":123"))
        assertEquals(124L, snapshot.version)
        assertEquals(1, snapshot.pendingEntriesCount)
        assertEquals("C", snapshot.pendingEntries.single().track.title)
    }

    @Test
    fun `auth DTO parses example exchange response`() {
        val raw = """
        {
          "accessToken": "access-abc",
          "accessTokenExpiresAtUtc": "2026-09-18T12:15:00+00:00",
          "refreshToken": "refresh-def",
          "refreshTokenExpiresAtUtc": "2026-10-18T12:00:00+00:00",
          "user": {
            "discordUserId": "123",
            "username": "qumi",
            "displayName": "Qumi",
            "avatarUrl": "https://cdn/x.png"
          }
        }
        """.trimIndent()
        val parsed = json.decodeFromString<AuthSessionResponse>(raw)
        assertEquals("access-abc", parsed.accessToken)
        assertEquals("refresh-def", parsed.refreshToken)
        assertEquals("123", parsed.user.discordUserId)
        assertEquals("qumi", parsed.user.username)
    }

    @Test
    fun `ProblemDetails parser reads errorCode and currentQueueVersion`() {
        val raw = """{"type":"https://x","title":"Conflict","status":409,"detail":"stale","errorCode":"queue_version_conflict","currentQueueVersion":42}"""
        val parsed = ProblemDetailsParser.parse(raw)
        assertEquals("queue_version_conflict", parsed?.errorCode)
        assertEquals(42L, parsed?.currentQueueVersion)
        assertEquals(409, parsed?.status)
    }

    @Test
    fun `persistence conflict stays a separate 503 problem`() {
        val parsed = ProblemDetailsParser.parse(
            """{"status":503,"errorCode":"queue_persistence_conflict"}""",
        )
        assertEquals(503, parsed?.status)
        assertEquals("queue_persistence_conflict", parsed?.errorCode)
        assertNull(parsed?.currentQueueVersion)
    }
}
