package com.tryniecki.qumi.data.repository

import com.tryniecki.qumi.api.client.ApiClientFactory
import com.tryniecki.qumi.api.model.auth.AuthUserResponse
import com.tryniecki.qumi.api.model.history.PlaybackHistoryEntryResponse
import com.tryniecki.qumi.auth.AppConfig
import com.tryniecki.qumi.auth.FakeAuthApi
import com.tryniecki.qumi.auth.FakePendingStorage
import com.tryniecki.qumi.auth.FakeSessionStore
import com.tryniecki.qumi.auth.SessionManager
import com.tryniecki.qumi.auth.UserSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HistoryRepositoryTest {
    private val server = MockWebServer()
    private lateinit var manager: SessionManager
    private lateinit var repository: SessionHistoryRepository

    @Before fun setUp() = runBlocking {
        server.start()
        val baseUrl = server.url("/").toString()
        manager = SessionManager(
            authApi = FakeAuthApi(),
            apiProvider = { token -> ApiClientFactory.create(baseUrl) { token } },
            sessionStore = FakeSessionStore(UserSession(
                "user-token", "2030-01-01T00:00:00Z", "refresh", "2030-02-01T00:00:00Z",
                AuthUserResponse("123", "user", "User"),
            )),
            pendingStorage = FakePendingStorage(),
            appConfig = AppConfig(baseUrl, "test-client"),
        )
        manager.restore()
        repository = SessionHistoryRepository(manager, QueueMutationCoordinator())
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `queues track by content with queue version and idempotency key`() = runBlocking {
        server.enqueue(MockResponse().setBody(queueResponse()))
        server.enqueue(MockResponse().setBody(queueResponse()))
        repository.enqueue("guild", "channel", entry("YouTube", "https://example.test/track"))
        assertEquals("/api/v1/app/guilds/guild/queue", server.takeRequest().path)
        val request = server.takeRequest()
        assertEquals("/api/v1/app/guilds/guild/queue/items", request.path)
        assertEquals("Bearer user-token", request.getHeader("Authorization"))
        assertNotNull(request.getHeader("Idempotency-Key"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("channel", body.getValue("voiceChannelId").jsonPrimitive.content)
        assertEquals("7", body.getValue("expectedQueueVersion").jsonPrimitive.content)
        assertEquals("YouTube", body.getValue("contentType").jsonPrimitive.content)
        assertEquals("id", body.getValue("contentId").jsonPrimitive.content)
    }

    @Test fun `Jellyfin queues by content like every other source`() = runBlocking {
        server.enqueue(MockResponse().setBody(queueResponse()))
        server.enqueue(MockResponse().setBody(queueResponse()))
        repository.enqueue("guild", "channel", entry("Jellyfin", "jellyfin://connection/someone-else/items/song"))
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/api/v1/app/guilds/guild/queue/items", request.path)
        val bodyText = request.body.readUtf8()
        val body = Json.parseToJsonElement(bodyText).jsonObject
        assertEquals("Jellyfin", body.getValue("contentType").jsonPrimitive.content)
        assertEquals("id", body.getValue("contentId").jsonPrimitive.content)
        assertEquals("7", body.getValue("expectedQueueVersion").jsonPrimitive.content)
        assertTrue(!bodyText.contains("someone-else"))
    }

    @Test fun `loads history using cursor and fixed page size`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"items":[],"nextCursor":null}"""))
        repository.getPage("guild", 123)
        assertEquals("/api/v1/app/guilds/guild/history?cursor=123&pageSize=50", server.takeRequest().path)
    }

    private fun entry(type: String, input: String) = PlaybackHistoryEntryResponse(
        1, type, "id", "Track", input, "2026-10-14T12:00:00Z",
    )

    private fun queueResponse() = """{"guildId":"guild","voiceChannelId":"channel","nowPlaying":null,"nowPlayingFromRadio":false,"radio":{"isEnabled":false},"pendingEntries":[],"pendingEntriesCount":0,"pendingDurationMilliseconds":0,"version":12,"queueVersion":7}"""
}
