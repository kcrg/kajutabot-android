package com.tryniecki.qumi.appfunctions

import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionNotSupportedException
import com.tryniecki.qumi.data.repository.FavoritesRepository
import com.tryniecki.qumi.data.repository.PlayerRepository
import com.tryniecki.qumi.ui.favorites.supportsFavorites

internal class BotFavoriteCommands(
    private val player: PlayerRepository,
    private val favorites: FavoritesRepository,
    private val targets: PlaybackTargetResolver,
) {
    suspend fun addCurrentTrackToFavorites(): FavoritedTrack {
        val target = targets.target()
        val current = player.getQueue(target.identity, target.guildId).nowPlaying
            ?: throw AppFunctionElementNotFoundException("Nothing is playing.")
        if (!current.supportsFavorites) throw AppFunctionNotSupportedException("Jellyfin tracks cannot be favorites.")
        favorites.addFavorite(target.identity, current.contentType, current.contentId)
        return FavoritedTrack(current.title)
    }
}
