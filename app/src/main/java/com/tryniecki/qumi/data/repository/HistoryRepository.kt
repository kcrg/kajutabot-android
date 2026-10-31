package com.tryniecki.qumi.data.repository

import com.tryniecki.qumi.api.model.history.PlaybackHistoryEntryResponse
import com.tryniecki.qumi.api.model.history.PlaybackHistoryPageResponse
import com.tryniecki.qumi.api.model.queue.EnqueueRequest
import com.tryniecki.qumi.auth.SessionManager

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
            mutation = { version, key -> sessionManager.withApiForSession(sessionIdentity) {
                it.enqueue(
                    guildId,
                    EnqueueRequest(
                        channelId,
                        expectedQueueVersion = version,
                        contentType = entry.contentType,
                        contentId = entry.contentId,
                    ),
                    key,
                )
            } },
        )
    }
}
