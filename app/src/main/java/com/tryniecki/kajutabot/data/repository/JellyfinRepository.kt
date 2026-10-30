package com.tryniecki.kajutabot.data.repository

import com.tryniecki.kajutabot.api.model.history.EnqueueJellyfinTrackRequest
import com.tryniecki.kajutabot.api.model.jellyfin.JellyfinAudioPageResponse
import com.tryniecki.kajutabot.api.model.jellyfin.JellyfinConnectionResponse
import com.tryniecki.kajutabot.api.model.jellyfin.JellyfinQuickConnectStartResponse
import com.tryniecki.kajutabot.api.model.jellyfin.JellyfinQuickConnectStatusResponse
import com.tryniecki.kajutabot.api.model.jellyfin.StartJellyfinQuickConnectRequest
import com.tryniecki.kajutabot.auth.SessionManager
import retrofit2.HttpException

interface JellyfinRepository {
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
) : JellyfinRepository {
    private val sessionIdentity = checkNotNull(sessionManager.sessionIdentity.value)

    override suspend fun getConnection(): JellyfinConnectionResponse? = try {
        sessionManager.withApiForSession(sessionIdentity) { it.getJellyfinConnection() }
    } catch (e: HttpException) {
        if (e.code() == 404) null else throw e
    }

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
                it.enqueueJellyfinTrack(itemId, EnqueueJellyfinTrackRequest(guildId, channelId, version), key)
            } },
        )
    }
}
