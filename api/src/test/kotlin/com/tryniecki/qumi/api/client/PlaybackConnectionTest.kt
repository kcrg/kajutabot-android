package com.tryniecki.qumi.api.client

import eu.lepicekmichal.signalrkore.AutomaticReconnect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList
import java.net.Socket
import java.net.InetAddress
import javax.net.SocketFactory

/** Real negotiate, WebSocket, handshake and hub protocol; no transport mocks. */
class PlaybackConnectionTest {
    private class TrackedSockets : SocketFactory() {
        val sockets = CopyOnWriteArrayList<Socket>()
        private fun track(socket: Socket) = socket.also { sockets += it }
        override fun createSocket() = track(Socket())
        override fun createSocket(host: String, port: Int) = track(Socket(host, port))
        override fun createSocket(host: InetAddress, port: Int) = track(Socket(host, port))
        override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int) =
            track(Socket(host, port, local, localPort))
        override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int) =
            track(Socket(host, port, local, localPort))
    }
    private val snapshot = """{"guildId":"g","nowPlayingFromRadio":false,"radio":{"isEnabled":true,"mode":"favorites","future":1},"pendingEntries":[{"entryId":"e","position":0,"track":{"contentId":"x","contentType":"YouTube","title":"Song","url":"https://example.test/x","durationMilliseconds":123,"playCount":2}}],"pendingEntriesCount":1,"pendingDurationMilliseconds":123,"version":8,"queueVersion":7,"nowPlayingStartedAt":"2026-10-24T12:00:00.1234567+00:00","playbackInstanceId":"c95a67b2-a247-456b-b33e-fc9cd03f79d1","skipOutcome":"RestartedRepeatedTrack","extra":true}"""

    private fun MockWebServer.enqueueConnection(
        socket: CompletableDeferred<WebSocket> = CompletableDeferred(),
        onInvocation: (WebSocket, String, String, String) -> Unit = { _, _, _, _ -> },
    ) {
        enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"connectionId":"id","connectionToken":"token","negotiateVersion":1,"availableTransports":[{"transport":"WebSockets","transferFormats":["Text","Binary"]}]}"""))
        enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = onMessage(webSocket, bytes.utf8())
            override fun onMessage(webSocket: WebSocket, text: String) {
                text.split('\u001e').filter(String::isNotBlank).forEach { frame ->
                    val message = Json.parseToJsonElement(frame).jsonObject
                    if (message["protocol"] != null) {
                        webSocket.send("{}\u001e")
                        socket.complete(webSocket)
                    } else {
                        val id = message["invocationId"]?.jsonPrimitive?.content ?: return@forEach
                        onInvocation(webSocket, id, message["target"]!!.jsonPrimitive.content, message["arguments"]!!.jsonArray.toString())
                    }
                }
            }
        }))
    }

    @Test fun `all events have independent simultaneous subscribers and guild completion uses boolean`() = runBlocking {
        val server = MockWebServer()
        server.enqueueConnection { socket, id, method, args ->
            assertEquals("SubscribeGuildState", method)
            assertEquals("[\"g\"]", args)
            socket.send("""{"type":1,"target":"QueueUpdated","arguments":[$snapshot]}""" + '\u001e')
            socket.send("""{"type":1,"target":"PlaybackStateUpdated","arguments":[{"guildId":"g","radio":{"isEnabled":false},"pendingEntriesCount":2,"pendingDurationMilliseconds":9,"version":5,"queueVersion":4}]}""" + '\u001e')
            socket.send("""{"type":1,"target":"VoiceChannelsUpdated","arguments":[{"guildId":"g","channels":[{"id":"c","name":"Voice","position":0,"userCount":2,"isConnected":true}]}]}""" + '\u001e')
            socket.send("""{"type":1,"target":"JellyfinQuickConnectUpdated","arguments":[{"attemptId":"a","status":"authorized"}]}""" + '\u001e')
            socket.send("""{"type":1,"target":"LocalVolumeStateChanged","arguments":[{"installed":true,"online":false,"available":false}]}""" + '\u001e')
            socket.send("""{"type":3,"invocationId":"$id","result":true}""" + '\u001e')
        }
        server.start()
        val client = buildPlaybackConnection(server.url("/hub").toString(), { "jwt" })
        try {
            withTimeout(10_000) {
                val queue = async(start = CoroutineStart.UNDISPATCHED) { client.updates.first() }
                val secondQueue = async(start = CoroutineStart.UNDISPATCHED) { client.updates.first() }
                val state = async(start = CoroutineStart.UNDISPATCHED) { client.playbackStates.first() }
                val channels = async(start = CoroutineStart.UNDISPATCHED) { client.voiceChannels.first() }
                val quickConnect = async(start = CoroutineStart.UNDISPATCHED) { client.quickConnectUpdates.first() }
                val volume = async(start = CoroutineStart.UNDISPATCHED) { client.localVolumeUpdates.first() }
                client.start()
                assertTrue(client.connected.first())
                assertTrue(client.subscribeGuild("g"))
                val received = queue.await()
                assertEquals(received, secondQueue.await())
                assertEquals("Song", received.pendingEntries.single().track.title)
                assertNull(received.pendingEntries.single().track.artworkUrl)
                assertNull(received.voiceChannelId)
                assertFalse(received.isRepeatEnabled)
                assertEquals("favorites", received.radio.mode)
                assertEquals("2026-10-24T12:00:00.1234567+00:00", received.nowPlayingStartedAt)
                assertEquals("RestartedRepeatedTrack", received.skipOutcome!!.name)
                assertEquals(4L, state.await().queueVersion)
                assertEquals(2, channels.await().channels.single().userCount)
                assertEquals("a", quickConnect.await().attemptId)
                assertNull(volume.await().volume)
            }
        } finally { client.stop(); server.shutdown() }
    }

    @Test fun `network loss reconnects with refreshed token and existing event handlers`() = runBlocking {
        val server = MockWebServer()
        val firstSocket = CompletableDeferred<WebSocket>()
        val secondSocket = CompletableDeferred<WebSocket>()
        server.enqueueConnection(firstSocket)
        server.enqueueConnection(secondSocket)
        server.start()
        val token = AtomicReference("old-jwt")
        val sockets = TrackedSockets()
        val http = OkHttpClient.Builder().socketFactory(sockets).build()
        val client = buildPlaybackConnection(server.url("/hub").toString(), { token.get() },
            AutomaticReconnect.Custom { count, _ -> if (count < 3) 0L else null }, http)
        try {
            withTimeout(10_000) {
                val event = async(start = CoroutineStart.UNDISPATCHED) { client.playbackStates.first() }
                client.start()
                assertTrue(client.connected.first())
                repeat(2) { assertEquals("Bearer old-jwt", server.takeRequest(3, TimeUnit.SECONDS)!!.getHeader("Authorization")) }
                val disconnected = async(start = CoroutineStart.UNDISPATCHED) { client.connected.first { !it } }
                token.set("new-jwt")
                firstSocket.await()
                // Break the actual TCP transport without a SignalR Close message.
                sockets.sockets.last().close()
                disconnected.await()
                secondSocket.await()
                client.connected.first { it }
                repeat(2) { assertEquals("Bearer new-jwt", server.takeRequest(3, TimeUnit.SECONDS)!!.getHeader("Authorization")) }
                secondSocket.await().send("""{"type":1,"target":"PlaybackStateUpdated","arguments":[{"guildId":"g","radio":{"isEnabled":false},"pendingEntriesCount":0,"pendingDurationMilliseconds":0,"version":1,"queueVersion":1}]}""" + '\u001e')
                event.await()
                client.stop()
                assertFalse(client.connected.first())
                assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
            }
        } finally {
            client.stop(); server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    @Test fun `initial negotiate failure throws and leaves client disconnected`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(503))
        server.start()
        val client = buildPlaybackConnection(server.url("/hub").toString(), { "jwt" },
            AutomaticReconnect.Custom { count, _ -> if (count < 2) 0L else null })
        try {
            assertThrows(Exception::class.java) {
                runBlocking { client.start() }
            }
            assertFalse(client.connected.first())
            assertEquals(1, server.requestCount)
        } finally { client.stop(); server.shutdown() }
    }

    @Test fun `concurrent starts negotiate once and explicit stop cannot restart disposed client`() = runBlocking {
        val server = MockWebServer()
        server.enqueueConnection()
        server.start()
        val client = buildPlaybackConnection(server.url("/hub").toString(), { "jwt" })
        try {
            withTimeout(10_000) {
                val first = async { client.start() }
                val second = async { client.start() }
                first.await(); second.await()
                assertEquals(2, server.requestCount)
                client.stop()
                assertTrue(runCatching { client.start() }.exceptionOrNull() is IllegalStateException)
            }
        } finally { client.stop(); server.shutdown() }
    }

    @Test fun `cancelling pending invocation removes waiter and permits subsequent calls`() = runBlocking {
        val server = MockWebServer()
        val pending = CompletableDeferred<Unit>()
        server.enqueueConnection { socket, id, method, _ ->
            if (method == "SetLocalVolume") pending.complete(Unit)
            else socket.send("""{"type":3,"invocationId":"$id","result":{"installed":false,"online":false,"available":false,"volume":null}}""" + '\u001e')
        }
        server.start()
        val client = buildPlaybackConnection(server.url("/hub").toString(), { "jwt" })
        try {
            withTimeout(10_000) {
                client.start()
                val command = launch { client.setLocalVolume(100) }
                pending.await()
                command.cancelAndJoin()
                assertTrue(command.isCancelled)
                assertFalse(client.getLocalVolumeState().installed)
            }
        } finally { client.stop(); server.shutdown() }
    }

    @Test fun `token cancellation is propagated and never starts transport`() = runBlocking {
        val client = buildPlaybackConnection("http://localhost/hub", { throw kotlinx.coroutines.CancellationException("cancelled") })
        try {
            assertTrue(runCatching { client.start() }.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        } finally { client.stop() }
    }

    @Test fun `stop during native token refresh cancels refresh and fences subsequent attempts`() = runBlocking {
        val server = MockWebServer()
        val socket = CompletableDeferred<WebSocket>()
        val refreshing = CompletableDeferred<Unit>()
        val refreshCancelled = CompletableDeferred<Unit>()
        server.enqueueConnection(socket)
        server.start()
        var tokenCalls = 0
        val client = buildPlaybackConnection(server.url("/hub").toString(), {
            if (++tokenCalls == 1) "jwt" else {
                refreshing.complete(Unit)
                try { kotlinx.coroutines.awaitCancellation() }
                finally { refreshCancelled.complete(Unit) }
            }
        })
        try {
            withTimeout(10_000) {
                client.start()
                socket.await().send("""{"type":7,"allowReconnect":true}""" + '\u001e')
                refreshing.await()
                client.stop()
                refreshCancelled.await()
                assertEquals(2, tokenCalls)
                assertEquals(2, server.requestCount)
                assertFalse(client.connected.first())
            }
        } finally { client.stop(); server.shutdown() }
    }
}

