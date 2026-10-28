package com.tryniecki.kajutabot

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tryniecki.kajutabot.api.client.KajutaBotApiClientFactory
import com.tryniecki.kajutabot.api.client.KajutaBotRealtimeClientFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import javax.net.SocketFactory

/** Opt-in read-only production smoke test; never changes playback or volume. */
@RunWith(AndroidJUnit4::class)
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

    @Test fun productionGuestReceivesEventsAndReconnectsWithNewCredentials() {
        val baseUrl = InstrumentationRegistry.getArguments().getString("playbackBaseUrl")
        assumeNotNull(baseUrl)
        runBlocking {
            withTimeout(60_000) {
                val auth = KajutaBotApiClientFactory.createAuth(baseUrl!!)
                val token = AtomicReference(auth.guest().accessToken)
                val api = KajutaBotApiClientFactory.create(baseUrl) { token.get() }
                val guild = api.getMyGuilds().first()
                val sockets = TrackedSockets()
                val http = OkHttpClient.Builder().socketFactory(sockets).build()
                val refreshed = CompletableDeferred<Unit>()
                var reconnectToken: String? = null
                val client = KajutaBotRealtimeClientFactory.create(baseUrl, http) {
                    token.get().also { if (it == reconnectToken) refreshed.complete(Unit) }
                }
                try {
                    val queue = async(start = CoroutineStart.UNDISPATCHED) { client.updates.first() }
                    val heartbeat = async(start = CoroutineStart.UNDISPATCHED) { client.heartbeats.first() }
                    client.start()
                    client.connected.first { it }
                    if (client.subscribeGuild(guild.id)) assertEquals(guild.id, queue.await().guildId)
                    else queue.cancel()
                    heartbeat.await()
                    reconnectToken = auth.guest().accessToken
                    token.set(reconnectToken!!)
                    // Break only this test's TCP connection. Normal TLS verification stays enabled.
                    sockets.sockets.last().close()
                    refreshed.await()
                    client.connected.first { it }
                    client.subscribeGuild(guild.id)
                    client.stop()
                    assertFalse(client.connected.first())
                } finally {
                    client.stop()
                    http.dispatcher.executorService.shutdown()
                    http.connectionPool.evictAll()
                }
            }
        }
    }
}
