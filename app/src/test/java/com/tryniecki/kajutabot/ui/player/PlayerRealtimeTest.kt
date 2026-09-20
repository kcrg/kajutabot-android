package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.client.KajutaBotRealtimeClient
import com.tryniecki.kajutabot.api.client.RealtimeDisconnect
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerRealtimeTest {
    private fun snapshot(guildId: String, version: Long = 1) = QueueSnapshotResponse(
        guildId = guildId,
        voiceChannelId = null,
        nowPlaying = null,
        nowPlayingFromRadio = false,
        radio = RadioStateResponse(isEnabled = false),
        pendingEntries = emptyList(),
        pendingDurationMilliseconds = 0,
        version = version,
    )

    private inner class FakeConnection(
        private val emitOnSubscribe: Boolean = true,
        private val serverReportsSnapshot: Boolean = emitOnSubscribe,
    ) : KajutaBotRealtimeClient {
        private val _updates = MutableSharedFlow<QueueSnapshotResponse>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        private val _heartbeats = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        private val _closed = MutableSharedFlow<RealtimeDisconnect>(extraBufferCapacity = 1)
        override val updates: Flow<QueueSnapshotResponse> = _updates
        override val heartbeats: Flow<Unit> = _heartbeats
        override val closed: Flow<RealtimeDisconnect> = _closed
        val subscriptions = mutableListOf<String>()
        var stopped = false
        override suspend fun start() = Unit
        override suspend fun subscribeGuild(guildId: String): Boolean {
            subscriptions += guildId
            if (emitOnSubscribe) _updates.emit(snapshot(guildId))
            return serverReportsSnapshot
        }
        override suspend fun stop() { stopped = true }
        fun sendUpdate(snapshot: QueueSnapshotResponse) { _updates.tryEmit(snapshot) }
        fun heartbeat() { _heartbeats.tryEmit(Unit) }
        suspend fun disconnect() { _closed.emit(RealtimeDisconnect("IOException")) }
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
        assertEquals(2, connections.size)
        assertEquals(listOf("g2"), connections.last().subscriptions)
        realtime.close()
        runCurrent()
    }

    @Test
    fun `guest token renewal reconnects in same session and resubscribes demo guild`() = runTest {
        val identity = MutableStateFlow<Long?>(42)
        val guild = MutableStateFlow<String?>("demo-guild")
        var currentGuestToken = "guest-jwt-1"
        val tokens = mutableListOf<String>()
        val connections = mutableListOf<FakeConnection>()
        val realtime = PlayerRealtime(backgroundScope, 42, identity, guild, { currentGuestToken },
            { token -> tokens += token; FakeConnection().also(connections::add) }, {}, {})
        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()
        assertEquals(listOf("guest-jwt-1"), tokens)
        currentGuestToken = "guest-jwt-2"
        connections.single().disconnect()
        runCurrent()
        assertEquals(listOf("guest-jwt-1", "guest-jwt-2"), tokens)
        assertEquals(listOf("demo-guild"), connections.last().subscriptions)
        assertEquals(42L, identity.value)
        realtime.close()
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
        val connectionsAtLogout = connections.size
        identity.value = null
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(connectionsAtLogout, connections.size)
        assertEquals(true, connections.all { it.stopped })
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
        assertEquals(1, recoveries)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, recoveries)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(1, recoveries)
        realtime.close()
        runCurrent()
    }

    @Test
    fun `expected initial snapshot timeout triggers one REST recovery`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>("g1")
        var recoveries = 0
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { FakeConnection(emitOnSubscribe = false, serverReportsSnapshot = true) }, {}, { recoveries++ })
        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()
        assertEquals(0, recoveries)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, recoveries)
        realtime.close()
    }

    @Test
    fun `latest queue snapshot wins burst and heartbeat only updates liveness`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>("g1")
        val connection = FakeConnection()
        val received = mutableListOf<QueueSnapshotResponse>()
        var nowMs = 1_000L
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { connection }, received::add, {}, elapsedRealtimeMs = { nowMs }, jitterMs = { 0 })
        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()
        assertEquals(RealtimeConnectionState.CONNECTED_AND_SUBSCRIBED, realtime.diagnostics.value.state)
        assertEquals(1_000L, realtime.diagnostics.value.connectedAtMs)
        assertEquals(1_000L, realtime.diagnostics.value.lastQueueUpdateAtMs)

        received.clear()
        nowMs = 4_000L
        connection.heartbeat()
        runCurrent()
        assertEquals(emptyList<QueueSnapshotResponse>(), received)
        assertEquals(4_000L, realtime.diagnostics.value.lastFrameAtMs)
        assertEquals(1_000L, realtime.diagnostics.value.lastQueueUpdateAtMs)

        nowMs = 5_000L
        for (version in 2L..101L) connection.sendUpdate(snapshot("g1", version))
        runCurrent()
        assertEquals(listOf(101L), received.map { it.version })
        assertEquals(5_000L, realtime.diagnostics.value.lastQueueUpdateAtMs)
        realtime.close()
        runCurrent()
        assertEquals(RealtimeConnectionState.DISCONNECTED, realtime.diagnostics.value.state)
    }

    @Test
    fun `connection without selected guild is neutral and subscribes on selection`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>(null)
        val connection = FakeConnection()
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { connection }, {}, {})
        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()
        assertEquals(RealtimeConnectionState.CONNECTED_NO_GUILD, realtime.diagnostics.value.state)
        guild.value = "g1"
        runCurrent()
        assertEquals(listOf("g1"), connection.subscriptions)
        assertEquals(RealtimeConnectionState.CONNECTED_AND_SUBSCRIBED, realtime.diagnostics.value.state)
        realtime.close()
    }
}
