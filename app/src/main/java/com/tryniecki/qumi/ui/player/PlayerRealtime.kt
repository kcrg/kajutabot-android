package com.tryniecki.qumi.ui.player

import com.tryniecki.qumi.api.client.RealtimeClient
import com.tryniecki.qumi.api.model.discord.VoiceChannelsUpdatedEvent
import com.tryniecki.qumi.api.model.jellyfin.JellyfinQuickConnectEvent
import com.tryniecki.qumi.api.model.queue.PlaybackStateResponse
import com.tryniecki.qumi.api.model.queue.QueueSnapshotResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

enum class RealtimeOwner { UI, MEDIA_SERVICE }

enum class RealtimeConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED_NO_GUILD,
    SUBSCRIBING,
    CONNECTED_AND_SUBSCRIBED,
    RECONNECTING,
}

/** Timestamps use the same monotonic clock as Android elapsedRealtime. */
data class RealtimeDiagnostics(
    val state: RealtimeConnectionState = RealtimeConnectionState.DISCONNECTED,
    val lastQueueUpdateAtMs: Long? = null,
)

/** One connection shared by UI/MediaSession; native recovery owns transport retries. */
internal class PlayerRealtime(
    private val scope: CoroutineScope,
    private val expectedIdentity: Long,
    private val sessionIdentity: StateFlow<Long?>,
    private val guildId: StateFlow<String?>,
    private val token: suspend () -> String,
    private val connect: (suspend () -> String) -> RealtimeClient,
    private val onSnapshot: (QueueSnapshotResponse) -> Unit,
    private val recoverQueue: suspend (String) -> Unit,
    private val elapsedRealtimeMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val jitterMs: () -> Long = { Random.nextLong(0, 251) },
    private val localVolume: LocalVolumeController? = null,
    private val onPlaybackState: (PlaybackStateResponse) -> Unit = {},
    private val onVoiceChannels: (VoiceChannelsUpdatedEvent) -> Unit = {},
    private val onQuickConnect: (JellyfinQuickConnectEvent) -> Unit = {},
    private val onConnected: (isReconnect: Boolean) -> Unit = {},
    private val network: StateFlow<Long?> = MutableStateFlow(0L),
) {
    private val owners = MutableStateFlow<Set<RealtimeOwner>>(emptySet())
    private val _diagnostics = MutableStateFlow(RealtimeDiagnostics())
    val diagnostics: StateFlow<RealtimeDiagnostics> = _diagnostics.asStateFlow()
    private var hasConnected = false

    private val job = scope.launch {
        combine(owners, sessionIdentity, network) { interested, identity, current ->
            // A socket dies with its network, so each new default network gets a new connection.
            current?.takeIf { interested.isNotEmpty() && identity == expectedIdentity }
        }.distinctUntilChanged().collectLatest { current ->
            if (current != null) runConnectionLoop()
            else _diagnostics.update { it.copy(state = RealtimeConnectionState.DISCONNECTED) }
        }
    }

    fun setOwner(owner: RealtimeOwner, active: Boolean) {
        owners.update { if (active) it + owner else it - owner }
    }

    fun close() {
        owners.value = emptySet()
        job.cancel()
    }

    private suspend fun runConnectionLoop() {
        var backoffStep = 0
        var attempts = 0
        var recoveryAttemptedGuild: String? = null
        suspend fun recoverOnce(selected: String) {
            if (recoveryAttemptedGuild == selected || sessionIdentity.value != expectedIdentity ||
                guildId.value != selected
            ) return
            recoveryAttemptedGuild = selected
            recoverQueue(selected)
        }

        try {
            while (currentCoroutineContext().isActive && sessionIdentity.value == expectedIdentity) {
                var client: RealtimeClient? = null
                var connectedAt: Long? = null
                var subscribed = false
                try {
                    _diagnostics.update {
                        it.copy(
                            state = if (attempts == 0) RealtimeConnectionState.CONNECTING
                            else RealtimeConnectionState.RECONNECTING,
                        )
                    }
                    // The provider checks this login's identity and refreshes credentials
                    // on every native connection attempt, without capturing an old JWT.
                    client = connect(token)
                    localVolume?.bind(client)
                    coroutineScope {
                        // REST fills the screen while a slow hub keeps connecting.
                        val startRecovery = launch {
                            delay(HUB_START_RECOVERY_MS)
                            guildId.value?.let { recoverOnce(it) }
                        }
                        // One serial consumer preserves hub snapshot ordering.
                        val snapshots = launch(start = CoroutineStart.UNDISPATCHED) {
                            client.updates.collect { snapshot ->
                                if (sessionIdentity.value == expectedIdentity) {
                                    onSnapshot(snapshot)
                                    startRecovery.cancel()
                                    if (snapshot.guildId == guildId.value) recoveryAttemptedGuild = null
                                    val now = elapsedRealtimeMs()
                                    _diagnostics.update { it.copy(lastQueueUpdateAtMs = now) }
                                }
                            }
                        }
                        val playbackStates = launch(start = CoroutineStart.UNDISPATCHED) {
                            client.playbackStates.collect { state ->
                                if (sessionIdentity.value == expectedIdentity) {
                                    onPlaybackState(state)
                                    if (state.guildId == guildId.value) recoveryAttemptedGuild = null
                                    val now = elapsedRealtimeMs()
                                    _diagnostics.update { it.copy(lastQueueUpdateAtMs = now) }
                                }
                            }
                        }
                        val voiceChannels = launch(start = CoroutineStart.UNDISPATCHED) {
                            client.voiceChannels.collect { update ->
                                if (sessionIdentity.value == expectedIdentity) onVoiceChannels(update)
                            }
                        }
                        val quickConnect = launch(start = CoroutineStart.UNDISPATCHED) {
                            client.quickConnectUpdates.collect { update ->
                                if (sessionIdentity.value == expectedIdentity) onQuickConnect(update)
                            }
                        }
                        val closed = async(start = CoroutineStart.UNDISPATCHED) { client.closed.first() }
                        client.start()
                        val availability = launch(start = CoroutineStart.UNDISPATCHED) {
                            client.connected.collectLatest { connected ->
                                if (!connected) {
                                    // Rebind immediately so events during the next handshake are observed.
                                    localVolume?.bind(client)
                                    _diagnostics.update { it.copy(state = RealtimeConnectionState.RECONNECTING) }
                                    guildId.value?.let { recoverOnce(it) }
                                    return@collectLatest
                                }
                                localVolume?.connected()
                                onConnected(hasConnected)
                                hasConnected = true
                                connectedAt = elapsedRealtimeMs()
                                _diagnostics.update {
                                    it.copy(
                                        state = if (guildId.value == null) RealtimeConnectionState.CONNECTED_NO_GUILD
                                        else RealtimeConnectionState.SUBSCRIBING,
                                    )
                                }
                                var recoveryJob: Job? = null
                                try {
                                    // Sequential invocations avoid an old SubscribeGuild completing after a new one.
                                    guildId.collect { selected ->
                                        recoveryJob?.cancel()
                                        if (selected == null) {
                                            _diagnostics.update { it.copy(state = RealtimeConnectionState.CONNECTED_NO_GUILD) }
                                            return@collect
                                        }
                                        _diagnostics.update { it.copy(state = RealtimeConnectionState.SUBSCRIBING) }
                                        val firstSnapshot = async(start = CoroutineStart.UNDISPATCHED) {
                                            client.updates.filter { it.guildId == selected }.first()
                                        }
                                        var handedToRecovery = false
                                        try {
                                            val initialSnapshotAvailable = client.subscribeGuild(selected)
                                            if (guildId.value == selected) {
                                                subscribed = true
                                                _diagnostics.update {
                                                    it.copy(state = RealtimeConnectionState.CONNECTED_AND_SUBSCRIBED)
                                                }
                                                recoveryJob = launch {
                                                    try {
                                                        if (!initialSnapshotAvailable ||
                                                            withTimeoutOrNull(INITIAL_SNAPSHOT_TIMEOUT_MS) {
                                                                firstSnapshot.await()
                                                            } == null
                                                        ) recoverOnce(selected)
                                                    } finally {
                                                        firstSnapshot.cancel()
                                                    }
                                                }
                                                handedToRecovery = true
                                            }
                                        } finally {
                                            if (!handedToRecovery) firstSnapshot.cancel()
                                        }
                                    }
                                } finally {
                                    recoveryJob?.cancel()
                                }
                            }
                        }
                        try {
                            closed.await()
                            localVolume?.disconnect()
                        } finally {
                            startRecovery.cancelAndJoin()
                            availability.cancelAndJoin()
                            snapshots.cancelAndJoin()
                            playbackStates.cancelAndJoin()
                            voiceChannels.cancelAndJoin()
                            quickConnect.cancelAndJoin()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    guildId.value?.let { recoverOnce(it) }
                } finally {
                    localVolume?.disconnect()
                    client?.let { connection ->
                        withContext(NonCancellable) {
                            try {
                                withTimeoutOrNull(STOP_TIMEOUT_MS) { connection.stop() }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                // Shutdown is best effort; the reconnect loop owns the next attempt.
                            }
                        }
                    }
                }
                if (sessionIdentity.value != expectedIdentity) break
                if (subscribed && connectedAt != null &&
                    elapsedRealtimeMs() - connectedAt >= STABLE_CONNECTION_MS
                ) backoffStep = 0
                _diagnostics.update { it.copy(state = RealtimeConnectionState.RECONNECTING) }
                val waitMs = BACKOFF_MS[backoffStep.coerceAtMost(BACKOFF_MS.lastIndex)]
                backoffStep++
                attempts++
                if (waitMs > 0) delay(waitMs + jitterMs().coerceIn(0, 250))
            }
        } finally {
            localVolume?.disconnect()
            _diagnostics.update { it.copy(state = RealtimeConnectionState.DISCONNECTED) }
        }
    }

    companion object {
        private const val HUB_START_RECOVERY_MS = 5_000L
        private const val INITIAL_SNAPSHOT_TIMEOUT_MS = 3_000L
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val STABLE_CONNECTION_MS = 30_000L
        private val BACKOFF_MS = longArrayOf(0L, 1_000L, 2_000L, 5_000L, 8_000L)
    }
}
