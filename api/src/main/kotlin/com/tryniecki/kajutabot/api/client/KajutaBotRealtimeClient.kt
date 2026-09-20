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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Coroutine-only surface of one physical SignalR connection. */
interface KajutaBotRealtimeClient {
    val updates: Flow<QueueSnapshotResponse>
    val closed: Flow<Unit>
    suspend fun start()
    suspend fun subscribeGuild(guildId: String)
    suspend fun stop()
}

object KajutaBotRealtimeClientFactory {
    fun create(baseUrl: String, accessToken: String): KajutaBotRealtimeClient {
        val root = baseUrl.trimEnd('/')
        require(root.startsWith("https://")) { "Realtime requires an HTTPS server root." }
        require(accessToken.isNotBlank()) { "Access token is required." }
        val connection = HubConnectionBuilder.create("$root/hubs/playback")
            .withTransport(TransportEnum.WEBSOCKETS)
            .withAccessTokenProvider(Single.just(accessToken))
            .build()
        return SignalRRealtimeClient(connection)
    }
}

private class SignalRRealtimeClient(
    private val connection: com.microsoft.signalr.HubConnection,
) : KajutaBotRealtimeClient {
    private val _updates = MutableSharedFlow<QueueSnapshotResponse>(extraBufferCapacity = 32)
    override val updates: Flow<QueueSnapshotResponse> = _updates.asSharedFlow()
    private val _closed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val closed: Flow<Unit> = _closed.asSharedFlow()
    private val handler: Subscription = connection.on(
        "QueueUpdated",
        { snapshot -> _updates.tryEmit(snapshot) },
        QueueSnapshotResponse::class.java,
    )

    init {
        connection.onClosed { _closed.tryEmit(Unit) }
    }

    override suspend fun start() = connection.start().awaitCompletion()

    override suspend fun subscribeGuild(guildId: String) =
        connection.invoke("SubscribeGuild", guildId).awaitCompletion()

    override suspend fun stop() {
        handler.unsubscribe()
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
