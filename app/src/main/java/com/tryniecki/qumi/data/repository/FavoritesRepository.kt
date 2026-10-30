package com.tryniecki.qumi.data.repository

import com.tryniecki.qumi.api.model.favorites.AddFavoriteRequest
import com.tryniecki.qumi.api.model.favorites.FavoriteResponse
import com.tryniecki.qumi.api.model.favorites.QueueFavoritesRequest
import com.tryniecki.qumi.api.model.queue.EnqueueRequest
import com.tryniecki.qumi.api.model.queue.QueueSnapshotResponse
import com.tryniecki.qumi.auth.SessionManager
import com.tryniecki.qumi.auth.UserSession
import kotlinx.coroutines.flow.StateFlow

class FavoritesRepository(
    private val sessionManager: SessionManager,
    private val coordinator: QueueMutationCoordinator = QueueMutationCoordinator(),
) {
    val sessionIdentity: StateFlow<Long?> = sessionManager.sessionIdentity

    fun currentSession(): UserSession? = sessionManager.currentUserSession()

    suspend fun getFavorites(expectedIdentity: Long): List<FavoriteResponse> =
        sessionManager.withApiForSession(expectedIdentity) { it.getFavorites() }

    suspend fun addFavorite(
        expectedIdentity: Long,
        contentType: String,
        contentId: String,
    ): FavoriteResponse = sessionManager.withApiForSession(expectedIdentity) {
        it.addFavorite(AddFavoriteRequest(contentType, contentId))
    }

    suspend fun deleteFavorite(expectedIdentity: Long, contentUrl: String) {
        sessionManager.withApiForSession(expectedIdentity) { it.deleteFavorite(contentUrl) }
    }

    suspend fun queueFavorites(
        expectedIdentity: Long,
        guildId: String,
        channelId: String,
        shuffle: Boolean,
    ): QueueSnapshotResponse = coordinator.run(
        guildId,
        fetch = { sessionManager.withApiForSession(expectedIdentity) { it.getQueue(guildId) } },
        mutation = { token, key -> sessionManager.withApiForSession(expectedIdentity) {
            it.queueFavorites(QueueFavoritesRequest(guildId, channelId, token, shuffle), key)
        } },
    )

    suspend fun playSingle(
        expectedIdentity: Long,
        guildId: String,
        channelId: String,
        contentUrl: String,
    ): QueueSnapshotResponse = coordinator.run(
        guildId,
        fetch = { sessionManager.withApiForSession(expectedIdentity) { it.getQueue(guildId) } },
        mutation = { token, key -> sessionManager.withApiForSession(expectedIdentity) {
            it.enqueue(guildId, EnqueueRequest(channelId, listOf(contentUrl), token), key)
        } },
    )
}
