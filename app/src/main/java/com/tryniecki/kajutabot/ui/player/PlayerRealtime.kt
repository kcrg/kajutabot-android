package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.client.KajutaBotRealtimeClient
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import kotlinx.coroutines.CoroutineStart

enum class RealtimeOwner { UI, MEDIA_SERVICE }

/** One connection loop for all consumers of this session-scoped player. */
internal class PlayerRealtime(
    private val scope: CoroutineScope,
    private val expectedIdentity: Long,
    private val sessionIdentity: StateFlow<Long?>,
    private val guildId: StateFlow<String?>,
    private val token: suspend () -> String,
    private val connect: (String) -> KajutaBotRealtimeClient,
    private val onSnapshot: (QueueSnapshotResponse) -> Unit,
    private val recoverQueue: suspend (String) -> Unit,
) {
    private val owners = MutableStateFlow<Set<RealtimeOwner>>(emptySet())
    private val job = scope.launch {
        combine(owners, sessionIdentity) { interested, identity ->
            interested.isNotEmpty() && identity == expectedIdentity
        }.distinctUntilChanged().collectLatest { active ->
            if (active) runConnectionLoop()
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
        var failures = 0
        var recoveryAttemptedGuild: String? = null
        suspend fun recoverOnce(selected: String) {
            if (recoveryAttemptedGuild == selected || sessionIdentity.value != expectedIdentity ||
                guildId.value != selected
            ) return
            recoveryAttemptedGuild = selected
            recoverQueue(selected)
        }
        while (currentCoroutineContext().isActive && sessionIdentity.value == expectedIdentity) {
            var client: KajutaBotRealtimeClient? = null
            try {
                client = connect(token())
                coroutineScope {
                    val snapshots = launch(start = CoroutineStart.UNDISPATCHED) {
                        client.updates.collect { snapshot ->
                            if (sessionIdentity.value == expectedIdentity) onSnapshot(snapshot)
                        }
                    }
                    val closed = async(start = CoroutineStart.UNDISPATCHED) { client.closed.first() }
                    client.start()
                    failures = 0
                    val subscriptions = launch {
                        guildId.collectLatest { selected ->
                            if (selected == null) return@collectLatest
                            val firstSnapshot = async(start = CoroutineStart.UNDISPATCHED) {
                                client.updates.filter { it.guildId == selected }.first()
                            }
                            try {
                                client.subscribeGuild(selected)
                                if (withTimeoutOrNull(INITIAL_SNAPSHOT_TIMEOUT_MS) { firstSnapshot.await() } == null &&
                                    sessionIdentity.value == expectedIdentity && guildId.value == selected
                                ) {
                                    recoverOnce(selected)
                                }
                            } finally {
                                firstSnapshot.cancel()
                            }
                        }
                    }
                    try {
                        closed.await()
                    } finally {
                        subscriptions.cancelAndJoin()
                        snapshots.cancelAndJoin()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Reconnect quietly; REST commands remain available.
                guildId.value?.let { selected -> recoverOnce(selected) }
            } finally {
                client?.let { connection ->
                    withContext(NonCancellable) {
                        withTimeoutOrNull(STOP_TIMEOUT_MS) { connection.stop() }
                    }
                }
            }
            if (sessionIdentity.value != expectedIdentity) break
            delay(RECONNECT_DELAYS_MS[failures.coerceAtMost(RECONNECT_DELAYS_MS.lastIndex)])
            failures++
        }
    }

    companion object {
        private const val INITIAL_SNAPSHOT_TIMEOUT_MS = 3_000L
        private const val STOP_TIMEOUT_MS = 5_000L
        private val RECONNECT_DELAYS_MS = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 20_000L)
    }
}
