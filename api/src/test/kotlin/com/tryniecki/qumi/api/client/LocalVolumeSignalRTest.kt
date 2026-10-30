package com.tryniecki.qumi.api.client

import com.tryniecki.qumi.api.model.playback.LocalVolumeStateDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class LocalVolumeSignalRTest {
    @Test fun `hub URL uses configured environment without duplicate API prefix`() {
        for (base in listOf("https://example.test", " https://example.test/ ", "https://example.test/api/v1/app/")) {
            assertEquals("https://example.test/api/v1/app/hubs/playback",
                RealtimeClientFactory.playbackHubUrl(base))
        }
    }

    @Test fun `DTO handles camelCase null and additional fields without revision`() {
        val json = ApiJson
        for (raw in listOf(
            """{"installed":false,"online":false,"available":false,"volume":null}""",
            """{"installed":true,"online":false,"available":false,"volume":null}""",
            """{"installed":true,"online":true,"available":false,"volume":null}""",
            """{"installed":true,"online":true,"available":true,"volume":87,"futureField":42}""",
        )) assertTrue(json.decodeFromString<LocalVolumeStateDto>(raw).isValid())
        for (volume in listOf(0, 100, 200)) assertTrue(LocalVolumeStateDto(true, true, true, volume).isValid())
        assertFalse(LocalVolumeStateDto(false, true, true, 50).isValid())
        assertFalse(LocalVolumeStateDto(true, false, true, 50).isValid())
        assertFalse(LocalVolumeStateDto(true, true, true, null).isValid())
        assertFalse(LocalVolumeStateDto(true, true, false, 50).isValid())
        assertFalse(LocalVolumeStateDto(true, true, true, -1).isValid())
        assertFalse(LocalVolumeStateDto(true, true, true, 201).isValid())
    }

    @Test fun `real SignalR sends bearer on negotiate and transport and waits for command completion`() = runBlocking {
        val server = MockWebServer()
        val setInvocation = CompletableDeferred<Pair<WebSocket, String>>()
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"connectionId":"id","connectionToken":"token","negotiateVersion":1,"availableTransports":[{"transport":"WebSockets","transferFormats":["Text","Binary"]}]}"""))
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                text.split('\u001e').filter(String::isNotBlank).forEach { frame ->
                    val message = Json.parseToJsonElement(frame).jsonObject
                    if (message["protocol"] != null) webSocket.send("{}\u001e")
                    else {
                        val id = message["invocationId"]?.jsonPrimitive?.content ?: return@forEach
                        when (message["target"]?.jsonPrimitive?.content) {
                            "GetLocalVolumeState" -> {
                                assertEquals(0, message["arguments"]!!.jsonArray.size)
                                webSocket.send("""{"type":3,"invocationId":"$id","result":{"installed":true,"online":true,"available":true,"volume":87,"extra":true}}""" + '\u001e')
                            }
                            "SetLocalVolume" -> {
                                assertEquals("[125]", message["arguments"].toString())
                                webSocket.send("""{"type":1,"target":"LocalVolumeStateChanged","arguments":[{"installed":true,"online":true,"available":true,"volume":124}]}""" + '\u001e')
                                setInvocation.complete(webSocket to id)
                            }
                        }
                    }
                }
            }
        }))
        server.start()
        val client = buildPlaybackConnection(
            server.url("/api/v1/app/hubs/playback").toString(), { "session-jwt" })
        try {
            withTimeout(10_000) {
                val event = async(start = CoroutineStart.UNDISPATCHED) { client.localVolumeUpdates.first() }
                client.start()
                val negotiate = server.takeRequest(5, TimeUnit.SECONDS)!!
                val transport = server.takeRequest(5, TimeUnit.SECONDS)!!
                assertEquals("Bearer session-jwt", negotiate.getHeader("Authorization"))
                assertEquals("Bearer session-jwt", transport.getHeader("Authorization"))
                assertTrue(negotiate.path!!.startsWith("/api/v1/app/hubs/playback/negotiate"))
                assertFalse(transport.path!!.contains("access_token"))
                assertEquals(87, client.getLocalVolumeState().volume)
                val command = async { client.setLocalVolume(125) }
                val (socket, id) = setInvocation.await()
                assertEquals(124, event.await().volume)
                assertFalse(command.isCompleted)
                socket.send("""{"type":3,"invocationId":"$id"}""" + '\u001e')
                command.await()
            }
        } finally {
            try {
                withTimeout(5_000) { client.stop() }
            } finally {
                server.shutdown()
            }
        }
    }
}
