package com.tryniecki.kajutabot.api.client

import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.playback.LocalVolumeStateDto
import eu.lepicekmichal.signalrkore.AutomaticReconnect
import eu.lepicekmichal.signalrkore.HubConnection
import eu.lepicekmichal.signalrkore.HubConnectionBuilder
import eu.lepicekmichal.signalrkore.HubConnectionState
import eu.lepicekmichal.signalrkore.HubValue
import eu.lepicekmichal.signalrkore.JsonHubProtocol
import eu.lepicekmichal.signalrkore.TransportEnum
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

/** Coroutine-only surface of one physical SignalR connection. */
interface KajutaBotRealtimeClient {
    val updates: Flow<QueueSnapshotResponse>
    val heartbeats: Flow<Unit>
    val closed: Flow<RealtimeDisconnect>
    /** Emits again after every successful transport recovery. */
    val connected: Flow<Boolean> get() = flowOf(true)
    val localVolumeUpdates: Flow<LocalVolumeStateDto>
    suspend fun getLocalVolumeState(): LocalVolumeStateDto
    /** Invocation completion acknowledges the command, never an observed volume. */
    suspend fun setLocalVolume(volume: Int)
    suspend fun start()
    /** True when the server had an initial queue snapshot to send. */
    suspend fun subscribeGuild(guildId: String): Boolean
    suspend fun stop()
}

/** Only a safe error category crosses the transport boundary. */
data class RealtimeDisconnect(val reason: String?)

object KajutaBotRealtimeClientFactory {
    internal fun playbackHubUrl(baseUrl: String): String {
        val root = baseUrl.trim().trimEnd('/').removeSuffix("/api/v1/app")
        require(root.startsWith("https://")) { "Realtime requires an HTTPS server root." }
        return "$root/api/v1/app/hubs/playback"
    }

    fun create(
        baseUrl: String,
        httpClient: OkHttpClient = KajutaBotApiClientFactory.baseHttpClient,
        accessTokenProvider: suspend () -> String,
    ): KajutaBotRealtimeClient =
        buildPlaybackConnection(playbackHubUrl(baseUrl), accessTokenProvider, baseHttpClient = httpClient)
}

/** Owns Ktor; the OkHttp engine shares the REST client's pool and TLS configuration. */
internal fun buildPlaybackConnection(
    hubUrl: String,
    accessTokenProvider: suspend () -> String,
    reconnect: AutomaticReconnect = AutomaticReconnect.Custom { count, _ ->
        // Preserve recovery through prolonged outages with a bounded retry delay.
        val delays = longArrayOf(0L, 1_000L, 2_000L, 5_000L, 8_000L)
        delays[count.coerceAtMost(delays.lastIndex)] + Random.nextLong(0, 251)
    },
    baseHttpClient: OkHttpClient = KajutaBotApiClientFactory.baseHttpClient,
): PlaybackRealtimeClient {
    val stopped = AtomicBoolean(false)
    val httpClient = HttpClient(OkHttp) {
        engine {
            preconfigured = baseHttpClient
            config {
                dispatcher(okhttp3.Dispatcher())
            }
        }
        install(WebSockets)
        install(SSE)
        install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        install(ContentNegotiation) { json(KajutaBotJson) }
    }
    val connection = HubConnectionBuilder.create(hubUrl) {
        this.httpClient = httpClient
        transports = setOf(TransportEnum.WebSockets)
        this.accessTokenProvider = {
            check(!stopped.get()) { "Connection has been disposed." }
            accessTokenProvider().also {
                check(!stopped.get()) { "Connection has been disposed." }
                require(it.isNotBlank()) { "Access token is required." }
            }
        }
        automaticReconnect = AutomaticReconnect.Custom { count, elapsed ->
            // stop() may see native DISCONNECTED while an older transport is still
            // closing. Fence both policy and credentials against a late retry.
            if (stopped.get()) null else when (reconnect) {
                is AutomaticReconnect.Custom -> reconnect.invoke(count, elapsed)
                AutomaticReconnect.Inactive -> null
            }
        }
        protocol = JsonHubProtocol(KajutaBotJson)
        // Leave the library's payload logger disabled. PlayerRealtime exposes safe
        // lifecycle diagnostics without URLs, credentials or hub payloads.
    }
    return PlaybackRealtimeClient(connection, httpClient, stopped)
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class PlaybackRealtimeClient(
    private val connection: HubConnection,
    private val httpClient: HttpClient,
    private val stopped: AtomicBoolean,
) : KajutaBotRealtimeClient {
    private val lifecycle = Mutex()
    override val connected: Flow<Boolean> = combine(connection.connectionState, connection.connectionId) { state, id ->
        if (state == HubConnectionState.CONNECTED) id else null
    }.distinctUntilChanged().map { it != null }
    // Keep the native connection ID in the distinct key: even if StateFlow conflates
    // a very fast CONNECTED -> RECONNECTING -> CONNECTED transition, a new ID still
    // restores server group membership and refreshes local volume.

    // Native cold flows register synchronously in UNDISTPATCHED collectors before
    // start(). Cancellation unregisters them; no independent forwarding jobs exist.
    override val updates = event("QueueUpdated", QueueSnapshotResponse.serializer())
    override val heartbeats = event("RealtimeHeartbeat", Long.serializer()).map { Unit }
    override val localVolumeUpdates = event("LocalVolumeStateChanged", LocalVolumeStateDto.serializer())

    // Emits DISCONNECTED while closing the old transport, before RECONNECTING.
    // This watchdog reports a terminal close only if native recovery never takes over.
    // Ordinary network retries remain entirely inside the library.
    override val closed: Flow<RealtimeDisconnect> = connection.connectionState
        .dropWhile { it == HubConnectionState.DISCONNECTED }
        .transformLatest { state ->
            if (state == HubConnectionState.DISCONNECTED) {
                delay(TERMINAL_CLOSE_TIMEOUT_MS)
                emit(RealtimeDisconnect("TransportClosed"))
            }
        }

    private fun <T : Any> event(method: String, serializer: KSerializer<T>): Flow<T> =
        connection.on(method, hasResult = false).map { invocation ->
            // The convenience overload silently catches malformed contracts. Propagate
            // failures and cancellation to the owner instead.
            (invocation.arguments.single() as HubValue.Decodable).decode(serializer)
        }

    override suspend fun start() = lifecycle.withLock {
        check(!stopped.get()) { "Connection has been disposed." }
        withTimeoutOrNull(30_000L) { connection.start(); true }
            ?: throw SocketTimeoutException("Connection startup timed out.")
        Unit
    }

    override suspend fun subscribeGuild(guildId: String): Boolean =
        whileConnected { connection.invoke("SubscribeGuild", guildId, String.serializer(), Boolean.serializer()) }

    override suspend fun getLocalVolumeState(): LocalVolumeStateDto =
        whileConnected { connection.invoke("GetLocalVolumeState", LocalVolumeStateDto.serializer()) }

    override suspend fun setLocalVolume(volume: Int) {
        require(volume in 0..200) { "invalid_volume" }
        whileConnected { connection.invoke("SetLocalVolume", volume, Int.serializer()) }
    }

    // Native invoke() otherwise waits indefinitely for completions lost on disconnect.
    // Never retry a mutation here: the server may already have applied it.
    private suspend fun <T : Any> whileConnected(action: suspend () -> T): T = withTimeoutOrNull(30_000L) {
        coroutineScope {
            val result = async(start = CoroutineStart.UNDISPATCHED) { action() }
            val disconnected = launch(start = CoroutineStart.UNDISPATCHED) {
                connection.connectionState.first { it != HubConnectionState.CONNECTED }
                throw IOException("Transport disconnected during invocation.")
            }
            try {
                result.await()
            } finally {
                disconnected.cancel()
            }
        }
    } ?: throw SocketTimeoutException("Hub invocation timed out.")

    override suspend fun stop() = lifecycle.withLock {
        if (stopped.getAndSet(true)) return@withLock
        try {
            connection.stop()
        } finally {
            // HubConnection.stop() does not close a supplied HttpClient, even when
            // already disconnected. We own this facade and release stalled HTTP work.
            httpClient.close()
            httpClient.coroutineContext.cancel()
        }
    }

    companion object {
        private const val TERMINAL_CLOSE_TIMEOUT_MS = 5_000L
    }
}
