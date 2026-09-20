package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.client.KajutaBotRealtimeClient
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
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
    val connectedAtMs: Long? = null,
    val lastFrameAtMs: Long? = null,
    val lastQueueUpdateAtMs: Long? = null,
    val reconnectAttempts: Int = 0,
    val lastDisconnectReason: String? = null,
)

/** One physical connection loop shared by the UI and MediaSession owners. */
internal class PlayerRealtime(
    private val scope: CoroutineScope,
    private val expectedIdentity: Long,
    private val sessionIdentity: StateFlow<Long?>,
    private val guildId: StateFlow<String?>,
    private val token: suspend () -> String,
    private val connect: (String) -> KajutaBotRealtimeClient,
    private val onSnapshot: (QueueSnapshotResponse) -> Unit,
    private val recoverQueue: suspend (String) -> Unit,
    private val elapsedRealtimeMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val jitterMs: () -> Long = { Random.nextLong(0, 251) },
) {
    private val owners = MutableStateFlow<Set<RealtimeOwner>>(emptySet())
    private val _diagnostics = MutableStateFlow(RealtimeDiagnostics())
    val diagnostics: StateFlow<RealtimeDiagnostics> = _diagnostics.asStateFlow()

    private val job = scope.launch {
        combine(owners, sessionIdentity) { interested, identity ->
            interested.isNotEmpty() && identity == expectedIdentity
        }.distinctUntilChanged().collectLatest { active ->
            if (active) runConnectionLoop()
            else _diagnostics.update { it.copy(state = RealtimeConnectionState.DISCONNECTED, connectedAtMs = null) }
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
                var client: KajutaBotRealtimeClient? = null
                var connectedAt: Long? = null
                var subscribed = false
                try {
                    _diagnostics.update {
                        it.copy(
                            state = if (attempts == 0) RealtimeConnectionState.CONNECTING
                            else RealtimeConnectionState.RECONNECTING,
                            reconnectAttempts = attempts,
                        )
                    }
                    client = connect(token())
                    coroutineScope {
                        // One serial consumer. The bounded transport flow conflates bursts.
                        val snapshots = launch(start = CoroutineStart.UNDISPATCHED) {
                            client.updates.collect { snapshot ->
                                if (sessionIdentity.value == expectedIdentity) {
                                    onSnapshot(snapshot)
                                    if (snapshot.guildId == guildId.value) recoveryAttemptedGuild = null
                                    val now = elapsedRealtimeMs()
                                    _diagnostics.update { it.copy(lastFrameAtMs = now, lastQueueUpdateAtMs = now) }
                                }
                            }
                        }
                        val heartbeats = launch(start = CoroutineStart.UNDISPATCHED) {
                            client.heartbeats.collect {
                                if (sessionIdentity.value == expectedIdentity) {
                                    val now = elapsedRealtimeMs()
                                    _diagnostics.update { it.copy(lastFrameAtMs = now) }
                                }
                            }
                        }
                        val closed = async(start = CoroutineStart.UNDISPATCHED) { client.closed.first() }
                        client.start()
                        connectedAt = elapsedRealtimeMs()
                        _diagnostics.update {
                            it.copy(
                                state = if (guildId.value == null) RealtimeConnectionState.CONNECTED_NO_GUILD
                                else RealtimeConnectionState.SUBSCRIBING,
                                connectedAtMs = connectedAt,
                            )
                        }
                        val subscriptions = launch(start = CoroutineStart.UNDISPATCHED) {
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
                        try {
                            val disconnect = closed.await()
                            _diagnostics.update { it.copy(lastDisconnectReason = disconnect.reason) }
                        } finally {
                            subscriptions.cancelAndJoin()
                            snapshots.cancelAndJoin()
                            heartbeats.cancelAndJoin()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    val reason = generateSequence(error as Throwable) { it.cause }
                        .take(4)
                        .joinToString(" > ") { it.javaClass.simpleName }
                    _diagnostics.update { it.copy(lastDisconnectReason = reason) }
                    guildId.value?.let { recoverOnce(it) }
                } finally {
                    client?.let { connection ->
                        withContext(NonCancellable) {
                            try {
                                withTimeoutOrNull(STOP_TIMEOUT_MS) { connection.stop() }
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
                _diagnostics.update { it.copy(state = RealtimeConnectionState.RECONNECTING, connectedAtMs = null) }
                val waitMs = BACKOFF_MS[backoffStep.coerceAtMost(BACKOFF_MS.lastIndex)]
                backoffStep++
                attempts++
                if (waitMs > 0) delay(waitMs + jitterMs().coerceIn(0, 250))
            }
        } finally {
            _diagnostics.update { it.copy(state = RealtimeConnectionState.DISCONNECTED, connectedAtMs = null) }
        }
    }

    companion object {
        private const val INITIAL_SNAPSHOT_TIMEOUT_MS = 3_000L
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val STABLE_CONNECTION_MS = 30_000L
        private val BACKOFF_MS = longArrayOf(0L, 1_000L, 2_000L, 5_000L, 8_000L)
    }
}
