package com.tryniecki.kajutabot.api.client

import com.tryniecki.kajutabot.api.model.auth.AuthSessionResponse
import com.tryniecki.kajutabot.api.model.error.KajutaBotProblemDetailsParser
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KajutaBotApiClientTest {
    private lateinit var server: MockWebServer

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `normalizeBaseUrl appends api v1`() {
        assertEquals(
            "https://api.kajuta.tryniecki.eu/api/v1/",
            KajutaBotApiClientFactory.normalizeBaseUrl("https://api.kajuta.tryniecki.eu"),
        )
        assertEquals(
            "https://api.kajuta.tryniecki.eu/api/v1/",
            KajutaBotApiClientFactory.normalizeBaseUrl("https://api.kajuta.tryniecki.eu/"),
        )
        assertEquals(
            "http://localhost:5000/api/v1/",
            KajutaBotApiClientFactory.normalizeBaseUrl("  http://localhost:5000/  "),
        )
    }

    @Test
    fun `Bearer interceptor adds Authorization header`() {
        server.enqueue(MockResponse().setBody("[]"))
        val api = KajutaBotApiClientFactory.create(server.url("/").toString()) { "token-123" }
        kotlinx.coroutines.runBlocking { api.getMyGuilds() }

        val recorded = server.takeRequest()
        assertEquals("Bearer token-123", recorded.getHeader("Authorization"))
    }

    @Test
    fun `no Authorization header when token null or blank`() {
        server.enqueue(MockResponse().setBody("[]"))
        val apiNull = KajutaBotApiClientFactory.create(server.url("/").toString()) { null }
        kotlinx.coroutines.runBlocking { apiNull.getMyGuilds() }
        assertNull(server.takeRequest().getHeader("Authorization"))

        server.enqueue(MockResponse().setBody("[]"))
        val apiBlank = KajutaBotApiClientFactory.create(server.url("/").toString()) { "   " }
        kotlinx.coroutines.runBlocking { apiBlank.getMyGuilds() }
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `never sends X-KajutaBot-Api-Key`() {
        server.enqueue(MockResponse().setBody("[]"))
        val api = KajutaBotApiClientFactory.create(server.url("/").toString()) { "token-123" }
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
        val authApi = KajutaBotApiClientFactory.createAuth(server.url("/").toString())
        kotlinx.coroutines.runBlocking {
            authApi.refresh(com.tryniecki.kajutabot.api.model.auth.RefreshUserSessionRequest("r"))
        }
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `user routes use users me paths`() {
        server.enqueue(MockResponse().setBody("[]"))
        val api = KajutaBotApiClientFactory.create(server.url("/").toString()) { "t" }
        kotlinx.coroutines.runBlocking { api.getMyGuilds() }
        assertEquals("/api/v1/users/me/guilds", server.takeRequest().path)

        server.enqueue(MockResponse().setBody("[]"))
        kotlinx.coroutines.runBlocking { api.getFavorites() }
        assertEquals("/api/v1/users/me/favorites", server.takeRequest().path)

        server.enqueue(
            MockResponse().setBody(
                """{"discordUserId":"1","contentUrl":"https://x","title":"t","addedAt":"2026-09-18T12:00:00+00:00"}""",
            ),
        )
        kotlinx.coroutines.runBlocking {
            api.addFavorite(com.tryniecki.kajutabot.api.model.favorites.AddFavoriteRequest("https://x"))
        }
        assertEquals("/api/v1/users/me/favorites", server.takeRequest().path)

        server.enqueue(MockResponse().setResponseCode(204))
        kotlinx.coroutines.runBlocking { api.deleteFavorite("https://youtu.be/abc") }
        val delete = server.takeRequest()
        assertTrue(delete.path!!.startsWith("/api/v1/users/me/favorites?"))
        assertTrue(delete.path!!.contains("contentUrl="))
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
            "username": "kajuta",
            "displayName": "Kajuta",
            "avatarUrl": "https://cdn/x.png"
          }
        }
        """.trimIndent()
        val parsed = json.decodeFromString<AuthSessionResponse>(raw)
        assertEquals("access-abc", parsed.accessToken)
        assertEquals("refresh-def", parsed.refreshToken)
        assertEquals("123", parsed.user.discordUserId)
        assertEquals("kajuta", parsed.user.username)
    }

    @Test
    fun `ProblemDetails parser reads errorCode and currentVersion`() {
        val raw = """{"type":"https://x","title":"Conflict","status":409,"detail":"stale","errorCode":"queue_version_conflict","currentVersion":42}"""
        val parsed = KajutaBotProblemDetailsParser.parse(raw)
        assertEquals("queue_version_conflict", parsed?.errorCode)
        assertEquals(42L, parsed?.currentVersion)
        assertEquals(409, parsed?.status)
    }
}
