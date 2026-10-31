package com.tryniecki.qumi.ui.player

import com.tryniecki.qumi.api.client.RealtimeClient
import com.tryniecki.qumi.api.client.RealtimeDisconnect
import com.tryniecki.qumi.api.model.discord.VoiceChannelsUpdatedEvent
import com.tryniecki.qumi.api.model.jellyfin.JellyfinQuickConnectEvent
import com.tryniecki.qumi.api.model.queue.PlaybackStateResponse
import com.tryniecki.qumi.api.model.queue.QueueSnapshotResponse
import com.tryniecki.qumi.api.model.radio.RadioStateResponse
import com.tryniecki.qumi.api.model.playback.LocalVolumeStateDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        pendingEntriesCount = 0,
        pendingDurationMilliseconds = 0,
        version = version,
        queueVersion = version,
    )

    private inner class FakeConnection(
        private val emitOnSubscribe: Boolean = true,
        private val serverReportsSnapshot: Boolean = emitOnSubscribe,
    ) : RealtimeClient {
        private val _updates = MutableSharedFlow<QueueSnapshotResponse>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
        private val _closed = MutableSharedFlow<RealtimeDisconnect>(extraBufferCapacity = 1)
        override val connected = MutableStateFlow(true)
        override val updates: Flow<QueueSnapshotResponse> = _updates
        override val closed: Flow<RealtimeDisconnect> = _closed
        override val playbackStates = MutableSharedFlow<PlaybackStateResponse>(extraBufferCapacity = 1)
        override val voiceChannels = MutableSharedFlow<VoiceChannelsUpdatedEvent>(extraBufferCapacity = 1)
        override val quickConnectUpdates = MutableSharedFlow<JellyfinQuickConnectEvent>(extraBufferCapacity = 1)
        override val localVolumeUpdates = MutableSharedFlow<LocalVolumeStateDto>(extraBufferCapacity = 1)
        var localVolumeReads = 0
        var localVolumeSnapshot = LocalVolumeStateDto(true, true, true, 87)
        var localVolumeSnapshotGate: CompletableDeferred<LocalVolumeStateDto>? = null
        var onStart: suspend () -> Unit = {}
        override suspend fun getLocalVolumeState(): LocalVolumeStateDto {
            localVolumeReads++
            return localVolumeSnapshotGate?.await() ?: localVolumeSnapshot
        }
        override suspend fun setLocalVolume(volume: Int) = Unit
        val subscriptions = mutableListOf<String>()
        var stopped = false
        override suspend fun start() = onStart()
        override suspend fun subscribeGuild(guildId: String): Boolean {
            subscriptions += guildId
            if (emitOnSubscribe) _updates.emit(snapshot(guildId))
            return serverReportsSnapshot
        }
        override suspend fun stop() { stopped = true }
        fun sendUpdate(snapshot: QueueSnapshotResponse) { _updates.tryEmit(snapshot) }
        suspend fun disconnect() { _closed.emit(RealtimeDisconnect("IOException")) }
    }

    @Test
    fun `local volume shares connection without guild and refreshes only on reconnect with new token`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>(null)
        val connections = mutableListOf<FakeConnection>()
        val tokens = mutableListOf<String>()
        var currentToken = "discord-jwt-1"
        val volume = LocalVolumeController(backgroundScope, 7, identity, true)
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { currentToken },
            { provider -> FakeConnection().also { it.onStart = { tokens += provider() }; connections += it } },
            {}, {}, localVolume = volume)
        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()
        assertEquals(87, volume.state.value.observed?.volume)
        assertEquals(1, connections.single().localVolumeReads)
        assertEquals(emptyList<String>(), connections.single().subscriptions)
        guild.value = "g1"; runCurrent()
        guild.value = "g2"; runCurrent()
        assertEquals(1, connections.single().localVolumeReads)
        currentToken = "discord-jwt-2"
        connections.single().disconnect(); runCurrent()
        assertEquals(listOf("discord-jwt-1", "discord-jwt-2"), tokens)
        assertEquals(1, connections.last().localVolumeReads)
        assertEquals(listOf("g2"), connections.last().subscriptions)
        connections.first().localVolumeUpdates.tryEmit(LocalVolumeStateDto(true, true, true, 200))
        runCurrent()
        assertEquals(87, volume.state.value.observed?.volume)
        realtime.setOwner(RealtimeOwner.UI, false); runCurrent()
        assertEquals(false, volume.state.value.enabled)
        assertEquals(true, volume.state.value.observed?.installed)
        volume.close()
    }

    @Test
    fun `local volume event handler is active during connection start`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val connection = FakeConnection()
        val volume = LocalVolumeController(backgroundScope, 7, identity, true)
        connection.localVolumeSnapshotGate = CompletableDeferred()
        connection.onStart = {
            connection.localVolumeUpdates.tryEmit(LocalVolumeStateDto(true, true, true, 123))
        }
        val realtime = PlayerRealtime(backgroundScope, 7, identity, MutableStateFlow(null), { "jwt" },
            { connection }, {}, {}, localVolume = volume)
        realtime.setOwner(RealtimeOwner.UI, true); runCurrent()
        assertEquals(123, volume.state.value.observed?.volume)
        assertEquals(true, volume.state.value.fresh)
        assertEquals(1, connection.localVolumeReads)
        connection.localVolumeSnapshotGate!!.complete(connection.localVolumeSnapshot)
        runCurrent()
        realtime.close(); volume.close()
    }

    @Test
    fun `native recovery reuses connection refreshes volume and restores latest guild once`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>("g1")
        val connection = FakeConnection()
        val volume = LocalVolumeController(backgroundScope, 7, identity, true)
        var creations = 0
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { creations++; connection }, {}, {}, localVolume = volume)
        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()
        connection.connected.value = false
        runCurrent()
        assertEquals(RealtimeConnectionState.RECONNECTING, realtime.diagnostics.value.state)
        assertEquals(false, volume.state.value.enabled)
        guild.value = "g2"
        runCurrent()
        connection.connected.value = true
        runCurrent()
        assertEquals(1, creations)
        assertEquals(listOf("g1", "g2"), connection.subscriptions)
        assertEquals(2, connection.localVolumeReads)
        assertEquals(1, connection.localVolumeUpdates.subscriptionCount.value)
        assertEquals(RealtimeConnectionState.CONNECTED_AND_SUBSCRIBED, realtime.diagnostics.value.state)
        identity.value = 8
        runCurrent()
        assertEquals(true, connection.stopped)
        assertEquals(0, connection.localVolumeUpdates.subscriptionCount.value)
        connection.connected.value = true
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, creations)
        volume.close()
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
            { provider -> FakeConnection().also { it.onStart = { tokens += provider() }; connections += it } }, {}, {})
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
    fun `latest queue snapshot wins burst`() = runTest {
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
        assertEquals(1_000L, realtime.diagnostics.value.lastQueueUpdateAtMs)

        received.clear()

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

    @Test
    fun `hub events reach their handlers and a state event counts as an update`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>("g1")
        val connection = FakeConnection()
        val states = mutableListOf<PlaybackStateResponse>()
        val channels = mutableListOf<VoiceChannelsUpdatedEvent>()
        val links = mutableListOf<JellyfinQuickConnectEvent>()
        var nowMs = 1_000L
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { connection }, {}, {}, elapsedRealtimeMs = { nowMs }, jitterMs = { 0 },
            onPlaybackState = states::add, onVoiceChannels = channels::add, onQuickConnect = links::add)
        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()

        nowMs = 2_000L
        connection.playbackStates.emit(
            PlaybackStateResponse("g1", radio = RadioStateResponse(false), pendingEntriesCount = 0,
                pendingDurationMilliseconds = 0, version = 2, queueVersion = 1),
        )
        connection.voiceChannels.emit(VoiceChannelsUpdatedEvent("g1", emptyList()))
        connection.quickConnectUpdates.emit(JellyfinQuickConnectEvent("a", "authorized"))
        runCurrent()

        assertEquals(1, states.size)
        assertEquals(1, channels.size)
        assertEquals(1, links.size)
        assertEquals(2_000L, realtime.diagnostics.value.lastQueueUpdateAtMs)
        realtime.close()
    }

    @Test
    fun `only a later connection counts as a reconnect`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>("g1")
        val connection = FakeConnection()
        val reconnects = mutableListOf<Boolean>()
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { connection }, {}, {}, jitterMs = { 0 }, onConnected = reconnects::add)
        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()

        connection.connected.value = false
        runCurrent()
        connection.connected.value = true
        runCurrent()

        assertEquals(listOf(false, true), reconnects)
        realtime.close()
    }

    @Test
    fun `no network holds retries and a new network connects at once`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>("g1")
        val network = MutableStateFlow<Long?>(1)
        val connections = mutableListOf<FakeConnection>()
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { FakeConnection().also(connections::add) }, {}, {}, jitterMs = { 0 }, network = network)
        realtime.setOwner(RealtimeOwner.MEDIA_SERVICE, true)
        runCurrent()
        assertEquals(1, connections.size)

        network.value = null
        runCurrent()
        assertTrue(connections.single().stopped)
        assertEquals(RealtimeConnectionState.DISCONNECTED, realtime.diagnostics.value.state)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, connections.size)

        network.value = 2
        runCurrent()
        assertEquals(2, connections.size)
        assertEquals(RealtimeConnectionState.CONNECTED_AND_SUBSCRIBED, realtime.diagnostics.value.state)
        realtime.close()
    }

    @Test
    fun `switching networks replaces the connection without waiting for its timeout`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>("g1")
        val network = MutableStateFlow<Long?>(1)
        val connections = mutableListOf<FakeConnection>()
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { FakeConnection().also(connections::add) }, {}, {}, jitterMs = { 0 }, network = network)
        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()

        network.value = 2
        runCurrent()

        assertTrue(connections.first().stopped)
        assertEquals(2, connections.size)
        realtime.close()
    }

    @Test
    fun `REST fills the queue while the hub is slow to start`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>("g1")
        val gate = CompletableDeferred<Unit>()
        val connection = FakeConnection().apply { onStart = { gate.await() } }
        var recoveries = 0
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { connection }, {}, { recoveries++ }, jitterMs = { 0 })
        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()

        advanceTimeBy(4_900)
        runCurrent()
        assertEquals(0, recoveries)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, recoveries)

        gate.complete(Unit)
        runCurrent()
        assertEquals(1, recoveries)
        realtime.close()
    }

    @Test
    fun `a quick hub start makes no REST request`() = runTest {
        val identity = MutableStateFlow<Long?>(7)
        val guild = MutableStateFlow<String?>("g1")
        var recoveries = 0
        val realtime = PlayerRealtime(backgroundScope, 7, identity, guild, { "jwt" },
            { FakeConnection() }, {}, { recoveries++ }, jitterMs = { 0 })
        realtime.setOwner(RealtimeOwner.UI, true)
        runCurrent()

        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(0, recoveries)
        realtime.close()
    }
}
