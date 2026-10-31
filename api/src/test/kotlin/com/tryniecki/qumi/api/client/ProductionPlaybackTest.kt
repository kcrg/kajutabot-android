package com.tryniecki.qumi.api.client

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
    @Test fun `production guest authenticates and receives queue`() {
        val baseUrl = System.getProperty("playbackTestBaseUrl", "")
        assumeTrue(baseUrl.isNotBlank())
        runBlocking {
            withTimeout(45_000) {
                val session = ApiClientFactory.createAuth(baseUrl).guest()
                val api = ApiClientFactory.create(baseUrl) { session.accessToken }
                val guild = api.getMyGuilds().first()
                val client = RealtimeClientFactory.create(baseUrl) { session.accessToken }
                try {
                    val queue = async(start = CoroutineStart.UNDISPATCHED) { client.updates.first() }
                    client.start()
                    client.connected.first { it }
                    if (client.subscribeGuild(guild.id)) assertEquals(guild.id, queue.await().guildId)
                    else queue.cancel()
                } finally { client.stop() }
            }
        }
    }
}
