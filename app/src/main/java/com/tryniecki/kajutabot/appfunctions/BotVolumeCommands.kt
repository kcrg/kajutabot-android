package com.tryniecki.kajutabot.appfunctions

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionDeniedException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import com.tryniecki.kajutabot.api.client.KajutaBotRealtimeClient
import com.tryniecki.kajutabot.data.repository.PlayerRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val MAX_VOLUME_PERCENT = 200
private const val VOLUME_TIMEOUT_MS = 15_000L

/** Volume lives behind the realtime hub, so each command opens a short-lived connection. */
internal class BotVolumeCommands(
    private val player: PlayerRepository,
    private val targets: PlaybackTargetResolver,
    private val createRealtimeClient: (Long) -> KajutaBotRealtimeClient,
) {
    suspend fun setBotVolume(volumePercent: Int): BotVolume {
        if (volumePercent !in 0..MAX_VOLUME_PERCENT) {
            throw AppFunctionInvalidArgumentException("Volume must be from 0 to $MAX_VOLUME_PERCENT.")
        }
        return withConnection { client ->
            requireVolume(client)
            client.setLocalVolume(volumePercent)
            BotVolume(volumePercent)
        }
    }

    suspend fun changeBotVolume(percentagePoints: Int): BotVolume {
        if (percentagePoints == 0) {
            throw AppFunctionInvalidArgumentException("The volume change cannot be 0.")
        }
        return withConnection { client ->
            val volume = (requireVolume(client) + percentagePoints).coerceIn(0, MAX_VOLUME_PERCENT)
            client.setLocalVolume(volume)
            BotVolume(volume)
        }
    }

    private suspend fun requireVolume(client: KajutaBotRealtimeClient): Int {
        val state = client.getLocalVolumeState()
        if (!state.isValid() || !state.available) {
            throw AppFunctionAppUnknownException(
                "Volume control needs the KajutaBot helper running next to Discord on a computer.",
            )
        }
        return checkNotNull(state.volume)
    }

    private suspend fun <T> withConnection(block: suspend (KajutaBotRealtimeClient) -> T): T {
        val identity = targets.sessionIdentity()
        if (!player.isDiscordSession(identity)) {
            throw AppFunctionDeniedException("Volume control needs signing in with Discord.")
        }
        val client = createRealtimeClient(identity)
        try {
            return withTimeout(VOLUME_TIMEOUT_MS) {
                client.start()
                block(client)
            }
        } finally {
            withContext(NonCancellable) { runCatching { client.stop() } }
        }
    }
}
