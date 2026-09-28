package com.tryniecki.kajutabot.data.repository

import com.tryniecki.kajutabot.api.model.favorites.AddFavoriteRequest
import com.tryniecki.kajutabot.api.model.favorites.FavoriteResponse
import com.tryniecki.kajutabot.api.model.favorites.QueueFavoritesRequest
import com.tryniecki.kajutabot.api.model.queue.EnqueueRequest
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.auth.SessionManager
import com.tryniecki.kajutabot.auth.UserSession
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
        mutation = { token -> sessionManager.withApiForSession(expectedIdentity) {
            it.queueFavorites(QueueFavoritesRequest(guildId, channelId, token, shuffle))
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
        mutation = { token -> sessionManager.withApiForSession(expectedIdentity) {
            it.enqueue(guildId, EnqueueRequest(channelId, listOf(contentUrl), token))
        } },
    )
}
