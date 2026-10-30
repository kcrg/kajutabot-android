package com.tryniecki.kajutabot.appfunctions

import com.tryniecki.kajutabot.api.client.KajutaBotRealtimeClient
import com.tryniecki.kajutabot.data.preferences.GuildSelection
import com.tryniecki.kajutabot.data.repository.FavoritesRepository
import com.tryniecki.kajutabot.data.repository.PlayerRepository

/** Everything AppFunctions can do, wired once so the service only declares the functions. */
internal class AppFunctionCommands(
    player: PlayerRepository,
    favorites: FavoritesRepository,
    guildSelection: suspend () -> GuildSelection,
    restoreSession: suspend () -> Unit,
    createRealtimeClient: (Long) -> KajutaBotRealtimeClient,
) {
    private val targets = PlaybackTargetResolver(player, guildSelection, restoreSession)

    val queue = BotQueueCommands(player, targets)
    val settings = BotPlaybackSettingsCommands(player, targets)
    val favorites = BotFavoriteCommands(player, favorites, targets)
    val volume = BotVolumeCommands(player, targets, createRealtimeClient)
}
