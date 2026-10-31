package com.tryniecki.qumi.data.repository

import com.tryniecki.qumi.api.model.jellyfin.JellyfinAudioPageResponse
import com.tryniecki.qumi.api.model.jellyfin.JellyfinConnectionResponse
import com.tryniecki.qumi.api.model.jellyfin.JellyfinQuickConnectStartResponse
import com.tryniecki.qumi.api.model.jellyfin.JellyfinQuickConnectEvent
import com.tryniecki.qumi.api.model.jellyfin.JellyfinQuickConnectStatusResponse
import com.tryniecki.qumi.api.model.jellyfin.StartJellyfinQuickConnectRequest
import com.tryniecki.qumi.api.model.queue.EnqueueRequest
import com.tryniecki.qumi.auth.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface JellyfinRepository {
    val quickConnectUpdates: Flow<JellyfinQuickConnectEvent> get() = emptyFlow()
    val hubConnected: Flow<Unit> get() = emptyFlow()

    /** The linked server, or null if this account has none. */
    suspend fun getConnection(): JellyfinConnectionResponse?
    suspend fun startQuickConnect(serverUrl: String): JellyfinQuickConnectStartResponse
    suspend fun getQuickConnectStatus(attemptId: String): JellyfinQuickConnectStatusResponse
    suspend fun disconnect()
    suspend fun getAudioPage(startIndex: Int, limit: Int, searchTerm: String?): JellyfinAudioPageResponse
    suspend fun enqueue(guildId: String, channelId: String, itemId: String)
}

/** Bound to one authenticated session; the backend resolves Jellyfin from that user's link. */
class SessionJellyfinRepository(
    private val sessionManager: SessionManager,
    private val coordinator: QueueMutationCoordinator,
    private val linkEvents: JellyfinLinkEvents,
) : JellyfinRepository {
    private val sessionIdentity = checkNotNull(sessionManager.sessionIdentity.value)
    override val quickConnectUpdates = linkEvents.quickConnectUpdates
    override val hubConnected = linkEvents.hubConnected

    override suspend fun getConnection(): JellyfinConnectionResponse? =
        sessionManager.withApiForSession(sessionIdentity) { it.getJellyfinConnection() }.connectionOrNull()

    override suspend fun startQuickConnect(serverUrl: String) =
        sessionManager.withApiForSession(sessionIdentity) {
            it.startJellyfinQuickConnect(StartJellyfinQuickConnectRequest(serverUrl))
        }

    override suspend fun getQuickConnectStatus(attemptId: String) =
        sessionManager.withApiForSession(sessionIdentity) { it.getJellyfinQuickConnectStatus(attemptId) }

    override suspend fun disconnect() {
        sessionManager.withApiForSession(sessionIdentity) { it.disconnectJellyfin() }
    }

    override suspend fun getAudioPage(startIndex: Int, limit: Int, searchTerm: String?) =
        sessionManager.withApiForSession(sessionIdentity) { it.getJellyfinAudio(startIndex, limit, searchTerm) }

    override suspend fun enqueue(guildId: String, channelId: String, itemId: String) {
        coordinator.run(
            guildId,
            fetch = { sessionManager.withApiForSession(sessionIdentity) { it.getQueue(guildId) } },
            mutation = { version, key -> sessionManager.withApiForSession(sessionIdentity) {
                it.enqueue(
                    guildId,
                    EnqueueRequest(channelId, expectedQueueVersion = version, contentType = "Jellyfin", contentId = itemId),
                    key,
                )
            } },
        )
    }
}
