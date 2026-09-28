package com.tryniecki.kajutabot.data.repository

import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One process-wide serialization point for mutations of each guild queue. */
class QueueMutationCoordinator {
    private data class Known(val version: Long, val queueVersion: Long)
    private val locks = mutableMapOf<String, Mutex>()
    private val known = mutableMapOf<String, Known>()
    private val _apiSnapshots = MutableSharedFlow<QueueSnapshotResponse>(
        extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val apiSnapshots = _apiSnapshots.asSharedFlow()

    @Synchronized fun observe(snapshot: QueueSnapshotResponse) {
        val previous = known[snapshot.guildId]
        if (previous == null || snapshot.version >= previous.version) {
            known[snapshot.guildId] = Known(snapshot.version, snapshot.queueVersion)
        }
    }

    fun publish(snapshot: QueueSnapshotResponse) {
        observe(snapshot)
        _apiSnapshots.tryEmit(snapshot)
    }

    @Synchronized private fun lockFor(guildId: String) = locks.getOrPut(guildId) { Mutex() }
    @Synchronized private fun tokenFor(guildId: String) = known[guildId]?.queueVersion
    @Synchronized private fun invalidate(guildId: String, failedToken: Long) {
        if (known[guildId]?.queueVersion == failedToken) known.remove(guildId)
    }

    @Synchronized fun clear() { known.clear() }

    suspend fun run(
        guildId: String,
        fetch: suspend () -> QueueSnapshotResponse,
        mutation: suspend (Long) -> QueueSnapshotResponse,
    ): QueueSnapshotResponse = lockFor(guildId).withLock {
        val token = tokenFor(guildId) ?: fetch().also(::publish).queueVersion
        try {
            mutation(token).also(::publish)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // The outcome of 503 and transport failures is ambiguous. Reconcile,
            // never resend a POST. A 409 is guaranteed to have made no change.
            try {
                publish(fetch())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A later local action must fetch before sending another token.
                invalidate(guildId, token)
            }
            throw error
        }
    }
}
