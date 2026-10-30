package com.tryniecki.qumi.data.repository

import com.tryniecki.qumi.api.client.ApiClientFactory
import com.tryniecki.qumi.api.model.auth.AuthSessionResponse
import com.tryniecki.qumi.api.model.auth.AuthUserResponse
import com.tryniecki.qumi.api.model.queue.EnqueueRequest
import com.tryniecki.qumi.api.model.queue.QueueSnapshotResponse
import com.tryniecki.qumi.api.model.radio.EnableRadioRequest
import com.tryniecki.qumi.api.model.radio.RadioStateResponse
import com.tryniecki.qumi.auth.AppConfig
import com.tryniecki.qumi.auth.FakeAuthApi
import com.tryniecki.qumi.auth.FakePendingStorage
import com.tryniecki.qumi.auth.FakeSessionStore
import com.tryniecki.qumi.auth.SessionManager
import com.tryniecki.qumi.auth.UserSession
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class ClientIdempotencyTest {
    private val response = """{"guildId":"guild","voiceChannelId":"voice","nowPlaying":null,"nowPlayingFromRadio":false,"radio":{"isEnabled":false},"pendingEntries":[],"pendingEntriesCount":0,"pendingDurationMilliseconds":0,"version":20,"queueVersion":10}"""
    private fun snapshot() = QueueSnapshotResponse(
        guildId = "guild", voiceChannelId = "voice", nowPlaying = null,
        nowPlayingFromRadio = false, radio = RadioStateResponse(false), pendingEntries = emptyList(),
        pendingEntriesCount = 0, pendingDurationMilliseconds = 0, version = 20, queueVersion = 10,
    )

    private suspend fun session(server: MockWebServer, auth: FakeAuthApi = FakeAuthApi()): SessionManager {
        val base = server.url("/").toString()
        return SessionManager(auth, { token -> ApiClientFactory.create(base) { token } },
            FakeSessionStore(UserSession("access-1", "2030-01-01T00:00:00Z", "refresh-1", "2030-02-01T00:00:00Z",
                AuthUserResponse("123", "user", "User"))), FakePendingStorage(), AppConfig(base, "client"))
            .also { it.restore() }
    }

    @Test fun `all repository queue mutations use distinct UUIDs including intentional duplicate tracks`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val manager = session(server)
            val coordinator = QueueMutationCoordinator().also { it.observe(snapshot()) }
            val player = PlayerRepository(manager, server.url("/").toString(), coordinator)
            val favorites = FavoritesRepository(manager, coordinator)
            val identity = requireNotNull(manager.sessionIdentity.value)
            repeat(13) { server.enqueue(MockResponse().setBody(response)) }
            repeat(2) { player.enqueue(identity, "guild", EnqueueRequest("voice", listOf("https://example.test/track"))) }
            repeat(2) { player.skip(identity, "guild") }
            player.stop(identity, "guild")
            player.setRepeat(identity, "guild", true)
            player.enableRadio(identity, "guild", EnableRadioRequest("voice", 60, 600))
            player.disableRadio(identity, "guild")
            player.removeQueueEntry(identity, "guild", "entry")
            player.swapQueueEntries(identity, "guild", "a", "b")
            player.clearQueue(identity, "guild")
            favorites.queueFavorites(identity, "guild", "voice", true)
            favorites.playSingle(identity, "guild", "voice", "https://example.test/track")
            val keys = List(13) { requireNotNull(server.takeRequest().getHeader("Idempotency-Key")) }
            keys.forEach { assertEquals(it, UUID.fromString(it).toString()) }
            assertEquals(13, keys.toSet().size)
        } finally { server.shutdown() }
    }

    @Test fun `401 refresh repeats enqueue with the same key payload and queue token`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val auth = FakeAuthApi(refreshHandler = { AuthSessionResponse("access-2", "2030-01-01T00:00:00Z",
                "refresh-2", "2030-02-01T00:00:00Z", AuthUserResponse("123", "user", "User")) })
            val manager = session(server, auth)
            val coordinator = QueueMutationCoordinator().also { it.observe(snapshot()) }
            val player = PlayerRepository(manager, server.url("/").toString(), coordinator)
            val identity = requireNotNull(manager.sessionIdentity.value)
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            server.enqueue(MockResponse().setBody(response))
            server.enqueue(MockResponse().setBody(response))
            val request = EnqueueRequest("voice", listOf("https://example.test/track"))
            player.enqueue(identity, "guild", request)
            val first = server.takeRequest()
            val retried = server.takeRequest()
            assertEquals(first.getHeader("Idempotency-Key"), retried.getHeader("Idempotency-Key"))
            assertEquals(first.body.readUtf8(), retried.body.readUtf8())
            assertEquals("Bearer access-1", first.getHeader("Authorization"))
            assertEquals("Bearer access-2", retried.getHeader("Authorization"))
            assertEquals(1, auth.refreshCount.get())
            player.enqueue(identity, "guild", request)
            assertNotEquals(first.getHeader("Idempotency-Key"), server.takeRequest().getHeader("Idempotency-Key"))
        } finally { server.shutdown() }
    }

    @Test fun `failure reconciles through GET without attaching the mutation key or repeating POST`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val manager = session(server)
            val coordinator = QueueMutationCoordinator().also { it.observe(snapshot()) }
            val player = PlayerRepository(manager, server.url("/").toString(), coordinator)
            server.enqueue(MockResponse().setResponseCode(503).setBody("""{"errorCode":"worker_ownership_lost"}"""))
            server.enqueue(MockResponse().setBody(response))
            val failed = runCatching { player.skip(requireNotNull(manager.sessionIdentity.value), "guild") }
            assertEquals(true, failed.isFailure)
            val mutation = server.takeRequest()
            val reconciliation = server.takeRequest()
            assertEquals("POST", mutation.method)
            UUID.fromString(requireNotNull(mutation.getHeader("Idempotency-Key")))
            assertEquals("GET", reconciliation.method)
            assertNull(reconciliation.getHeader("Idempotency-Key"))
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }
}
