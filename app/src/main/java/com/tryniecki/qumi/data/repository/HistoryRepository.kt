package com.tryniecki.qumi.data.repository

import com.tryniecki.qumi.api.model.history.EnqueueJellyfinTrackRequest
import com.tryniecki.qumi.api.model.history.PlaybackHistoryEntryResponse
import com.tryniecki.qumi.api.model.history.PlaybackHistoryPageResponse
import com.tryniecki.qumi.api.model.queue.EnqueueRequest
import com.tryniecki.qumi.auth.SessionManager
import java.net.URI
import java.net.URLDecoder

interface HistoryRepository {
    suspend fun getPage(guildId: String, cursor: Long?): PlaybackHistoryPageResponse
    suspend fun enqueue(guildId: String, channelId: String, entry: PlaybackHistoryEntryResponse)
}

/** Bound to one authenticated session; Jellyfin is resolved using that user's connection. */
class SessionHistoryRepository(
    private val sessionManager: SessionManager,
    private val coordinator: QueueMutationCoordinator,
) : HistoryRepository {
    private val sessionIdentity = checkNotNull(sessionManager.sessionIdentity.value)

    override suspend fun getPage(guildId: String, cursor: Long?) =
        sessionManager.withApiForSession(sessionIdentity) { it.getHistory(guildId, cursor, pageSize = 50) }

    override suspend fun enqueue(guildId: String, channelId: String, entry: PlaybackHistoryEntryResponse) {
        coordinator.run(
            guildId,
            fetch = { sessionManager.withApiForSession(sessionIdentity) { it.getQueue(guildId) } },
            mutation = { version, key -> sessionManager.withApiForSession(sessionIdentity) { api ->
                if (entry.contentType == "Jellyfin") {
                    val rawItemId = URI(entry.queueInput).rawPath.substringAfterLast('/')
                    val itemId = URLDecoder.decode(rawItemId.replace("+", "%2B"), Charsets.UTF_8.name())
                    require(itemId.isNotBlank())
                    api.enqueueJellyfinTrack(itemId, EnqueueJellyfinTrackRequest(guildId, channelId, version), key)
                } else {
                    api.enqueue(guildId, EnqueueRequest(channelId, listOf(entry.queueInput), version), key)
                }
            } },
        )
    }
}
