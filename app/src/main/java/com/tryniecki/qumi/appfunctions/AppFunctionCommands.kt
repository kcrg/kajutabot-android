package com.tryniecki.qumi.appfunctions

import com.tryniecki.qumi.api.client.RealtimeClient
import com.tryniecki.qumi.data.preferences.GuildSelection
import com.tryniecki.qumi.data.repository.FavoritesRepository
import com.tryniecki.qumi.data.repository.PlayerRepository

/** Everything AppFunctions can do, wired once so the service only declares the functions. */
internal class AppFunctionCommands(
    player: PlayerRepository,
    favorites: FavoritesRepository,
    guildSelection: suspend () -> GuildSelection,
    restoreSession: suspend () -> Unit,
    createRealtimeClient: (Long) -> RealtimeClient,
) {
    private val targets = PlaybackTargetResolver(player, guildSelection, restoreSession)

    val queue = BotQueueCommands(player, targets)
    val settings = BotPlaybackSettingsCommands(player, targets)
    val favorites = BotFavoriteCommands(player, favorites, targets)
    val volume = BotVolumeCommands(player, targets, createRealtimeClient)
}
