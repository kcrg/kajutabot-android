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
) {
    val sessionIdentity: StateFlow<Long?> = sessionManager.sessionIdentity

    fun currentSession(): UserSession? = sessionManager.currentUserSession()

    suspend fun getFavorites(expectedIdentity: Long): List<FavoriteResponse> =
        sessionManager.withApiForSession(expectedIdentity) { it.getFavorites() }

    suspend fun addFavorite(
        expectedIdentity: Long,
        contentUrl: String,
        title: String?,
        thumbnailUrl: String?,
    ): FavoriteResponse = sessionManager.withApiForSession(expectedIdentity) {
        it.addFavorite(AddFavoriteRequest(contentUrl, title, thumbnailUrl))
    }

    suspend fun deleteFavorite(expectedIdentity: Long, contentUrl: String) {
        sessionManager.withApiForSession(expectedIdentity) { it.deleteFavorite(contentUrl) }
    }

    suspend fun queueFavorites(
        expectedIdentity: Long,
        guildId: String,
        channelId: String,
        shuffle: Boolean,
    ): QueueSnapshotResponse = sessionManager.withApiForSession(expectedIdentity) {
        it.queueFavorites(QueueFavoritesRequest(guildId, channelId, shuffle = shuffle))
    }

    suspend fun playSingle(
        expectedIdentity: Long,
        guildId: String,
        channelId: String,
        contentUrl: String,
    ): QueueSnapshotResponse = sessionManager.withApiForSession(expectedIdentity) { api ->
        val queue = api.getQueue(guildId)
        api.enqueue(guildId, EnqueueRequest(channelId, listOf(contentUrl), queue.version))
    }
}
