package com.tryniecki.qumi.appfunctions

import androidx.appfunctions.AppFunctionElementNotFoundException
import com.tryniecki.qumi.data.repository.FavoritesRepository
import com.tryniecki.qumi.data.repository.PlayerRepository

internal class BotFavoriteCommands(
    private val player: PlayerRepository,
    private val favorites: FavoritesRepository,
    private val targets: PlaybackTargetResolver,
) {
    suspend fun addCurrentTrackToFavorites(): FavoritedTrack {
        val target = targets.target()
        val current = player.getQueue(target.identity, target.guildId).nowPlaying
            ?: throw AppFunctionElementNotFoundException("Nothing is playing.")
        favorites.addFavorite(target.identity, current.contentType, current.contentId)
        return FavoritedTrack(current.title)
    }
}
