package com.tryniecki.kajutabot.api.client

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in: creates a guest session and reads realtime state, without mutations. */
class ProductionPlaybackTest {
    @Test fun `production guest authenticates and receives queue and heartbeat`() {
        val baseUrl = System.getProperty("playbackTestBaseUrl", "")
        assumeTrue(baseUrl.isNotBlank())
        runBlocking {
            withTimeout(45_000) {
                val session = KajutaBotApiClientFactory.createAuth(baseUrl).guest()
                val api = KajutaBotApiClientFactory.create(baseUrl) { session.accessToken }
                val guild = api.getMyGuilds().first()
                val client = KajutaBotRealtimeClientFactory.create(baseUrl) { session.accessToken }
                try {
                    val queue = async(start = CoroutineStart.UNDISPATCHED) { client.updates.first() }
                    val heartbeat = async(start = CoroutineStart.UNDISPATCHED) { client.heartbeats.first() }
                    client.start()
                    client.connected.first { it }
                    if (client.subscribeGuild(guild.id)) assertEquals(guild.id, queue.await().guildId)
                    else queue.cancel()
                    heartbeat.await()
                } finally { client.stop() }
            }
        }
    }
}
