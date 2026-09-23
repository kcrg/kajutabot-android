package com.tryniecki.kajutabot.api.client

import com.microsoft.signalr.Action1
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.Subscription
import com.microsoft.signalr.TransportEnum
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Coroutine-only surface of one physical SignalR connection. */
interface KajutaBotRealtimeClient {
    val updates: Flow<QueueSnapshotResponse>
    val heartbeats: Flow<Unit>
    val closed: Flow<RealtimeDisconnect>
    suspend fun start()
    /** True when the server had an initial queue snapshot to send. */
    suspend fun subscribeGuild(guildId: String): Boolean
    suspend fun stop()
}

/** Only a safe error category crosses the transport boundary. */
data class RealtimeDisconnect(val reason: String?)

object KajutaBotRealtimeClientFactory {
    fun create(baseUrl: String, accessToken: String): KajutaBotRealtimeClient {
        val root = baseUrl.trimEnd('/')
        require(root.startsWith("https://")) { "Realtime requires an HTTPS server root." }
        require(accessToken.isNotBlank()) { "Access token is required." }
        val connection = HubConnectionBuilder.create("$root/api/v1/app/hubs/playback")
            .withTransport(TransportEnum.WEBSOCKETS)
            .withAccessTokenProvider(Single.just(accessToken))
            .build()
        return SignalRRealtimeClient(connection)
    }
}

private class SignalRRealtimeClient(
    private val connection: com.microsoft.signalr.HubConnection,
) : KajutaBotRealtimeClient {
    // A queue event is full state. Each slow collector retains only the newest snapshot.
    // No replay: SubscribeGuild must wait for a frame from the new subscription.
    private val _updates = MutableSharedFlow<QueueSnapshotResponse>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val updates: Flow<QueueSnapshotResponse> = _updates.asSharedFlow()
    private val _heartbeats = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val heartbeats: Flow<Unit> = _heartbeats.asSharedFlow()
    private val _closed = MutableSharedFlow<RealtimeDisconnect>(replay = 1)
    override val closed: Flow<RealtimeDisconnect> = _closed.asSharedFlow()
    private val queueHandler: Subscription = connection.on(
        "QueueUpdated",
        Action1<QueueSnapshotResponse> { snapshot -> _updates.tryEmit(snapshot) },
        QueueSnapshotResponse::class.java,
    )
    private val heartbeatHandler: Subscription = connection.on(
        "RealtimeHeartbeat",
        Action1<Long> { _heartbeats.tryEmit(Unit) },
        Long::class.javaObjectType,
    )

    init {
        connection.onClosed { error ->
            _closed.tryEmit(RealtimeDisconnect(error?.javaClass?.simpleName))
        }
    }

    override suspend fun start() = connection.start().awaitCompletion()

    override suspend fun subscribeGuild(guildId: String): Boolean =
        connection.invoke(Boolean::class.javaObjectType, "SubscribeGuild", guildId).awaitValue()

    override suspend fun stop() {
        queueHandler.unsubscribe()
        heartbeatHandler.unsubscribe()
        try {
            connection.stop().awaitCompletion()
        } finally {
            connection.close()
        }
    }
}

private suspend fun Completable.awaitCompletion(): Unit = suspendCancellableCoroutine { continuation ->
    val disposable = subscribe(
        { if (continuation.isActive) continuation.resume(Unit) },
        { error -> if (continuation.isActive) continuation.resumeWithException(error) },
    )
    continuation.invokeOnCancellation { disposable.dispose() }
}

private suspend fun <T : Any> Single<T>.awaitValue(): T = suspendCancellableCoroutine { continuation ->
    val disposable = subscribe(
        { value -> if (continuation.isActive) continuation.resume(value) },
        { error -> if (continuation.isActive) continuation.resumeWithException(error) },
    )
    continuation.invokeOnCancellation { disposable.dispose() }
}
