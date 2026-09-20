package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.client.KajutaBotRealtimeClient
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerRealtimeTest {
    private fun snapshot(guildId: String) = QueueSnapshotResponse(
        guildId = guildId,
        voiceChannelId = null,
        nowPlaying = null,
        nowPlayingFromRadio = false,
        radio = RadioStateResponse(isEnabled = false),
        pendingEntries = emptyList(),
        pendingDurationMilliseconds = 0,
        version = 1,
    )

    private inner class FakeConnection(private val emitOnSubscribe: Boolean = true) : KajutaBotRealtimeClient {
        private val _updates = MutableSharedFlow<QueueSnapshotResponse>(extraBufferCapacity = 1)
        private val _closed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        override val updates: Flow<QueueSnapshotResponse> = _updates
        override val closed: Flow<Unit> = _closed
        val subscriptions = mutableListOf<String>()
        var stopped = false
        override suspend fun start() = Unit
        override suspend fun subscribeGuild(guildId: String) {
            subscriptions += guildId
            if (emitOnSubscribe) _updates.emit(snapshot(guildId))
        }
        override suspend fun stop() { stopped = true }
        suspend fun disconnect() { _closed.emit(Unit) }
    }

    @Test
    fun `owners share connection and only last release closes it`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>("g1")
        val connections = mutableListOf<FakeConnection>()
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { FakeConnection().also(connections::add) }, {}, {})

        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()
        assertEquals(1, connections.size)
        realtime.setOwner(RealtimeOwner.MEDIA_SERVICE, true)
        runCurrent()
        assertEquals(1, connections.size)
        realtime.setOwner(RealtimeOwner.UI, false)
        runCurrent()
        assertEquals(false, connections.single().stopped)
        realtime.setOwner(RealtimeOwner.MEDIA_SERVICE, false)
        runCurrent()
        assertEquals(true, connections.single().stopped)
    }

    @Test
    fun `guild switch reuses connection and reconnect resubscribes`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>("g1")
        val connections = mutableListOf<FakeConnection>()
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { FakeConnection().also(connections::add) }, {}, {})
        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()
        assertEquals(listOf("g1"), connections.single().subscriptions)
        guild.value = "g2"
        runCurrent()
        assertEquals(1, connections.size)
        assertEquals(listOf("g1", "g2"), connections.single().subscriptions)
        connections.single().disconnect()
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, connections.size)
        assertEquals(listOf("g2"), connections.last().subscriptions)
        realtime.close()
        runCurrent()
    }

    @Test
    fun `logout cancels reconnect backoff`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>("g1")
        val connections = mutableListOf<FakeConnection>()
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { FakeConnection().also(connections::add) }, {}, {})
        realtime.setOwner(RealtimeOwner.MEDIA_SERVICE, true)
        runCurrent()
        connections.single().disconnect()
        runCurrent()
        identity.value = null
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, connections.size)
        assertEquals(true, connections.single().stopped)
    }

    @Test
    fun `failed connection makes one REST recovery attempt while retrying`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>("g1")
        var attempts = 0
        var recoveries = 0
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { attempts++; throw IllegalStateException("offline") }, {}, { recoveries++ })
        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(true, attempts > 1)
        assertEquals(1, recoveries)
        identity.value = null
        runCurrent()
        val attemptsAtLogout = attempts
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(attemptsAtLogout, attempts)
    }

    @Test
    fun `missing initial hub snapshot triggers one REST recovery`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>("g1")
        var recoveries = 0
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { FakeConnection(emitOnSubscribe = false) }, {}, { recoveries++ })
        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, recoveries)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, recoveries)
        realtime.close()
        runCurrent()
    }
}
