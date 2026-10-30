package com.tryniecki.kajutabot.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import com.tryniecki.kajutabot.KajutaBotApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext

@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "KajutaBotAppFunctionService",
    appFunctionXmlFileName = "kajutabot_app_function_service",
)
abstract class BaseKajutaBotAppFunctionService : AppFunctionService() {
    private val commands by lazy {
        val container = (applicationContext as KajutaBotApplication).container
        AppFunctionCommands(
            player = container.playerRepository,
            favorites = container.favoritesRepository,
            guildSelection = container.preferencesRepository::currentGuildSelection,
            restoreSession = container.sessionRepository::restore,
            createRealtimeClient = { identity ->
                container.playerRepository.createRealtimeClient {
                    container.playerRepository.accessToken(identity)
                }
            },
        )
    }

    private suspend fun <T> run(action: suspend AppFunctionCommands.() -> T): T =
        withContext(Dispatchers.IO) {
            try {
                commands.action()
            } catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                throw error.toAppFunctionException()
            }
        }

    /**
     * Skips the track that the bot is playing on Discord.
     *
     * @return The skipped track. With repeat enabled, the track restarts instead.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun skipCurrentTrack(): SkippedTrack = run { queue.skipCurrentTrack() }

    /**
     * Finds the best match for a track title and adds it to the end of the bot's queue. If nothing
     * is playing, the bot starts playing it.
     *
     * @param trackTitle The track title, optionally with the artist, such as "Tool Lateralus".
     * @return The added track and its place in the queue.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addTrackToQueue(trackTitle: String): QueuedTrack = run { queue.addTrackToQueue(trackTitle) }

    /**
     * Finds the best match for a track title and plays it immediately: adds it, moves it to the
     * front of the queue and skips the current track. Other queued tracks stay in the queue.
     *
     * @param trackTitle The track title, optionally with the artist, such as "Tool Lateralus".
     * @return The added track and whether it plays next.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun playTrackNow(trackTitle: String): PlayedTrack = run { queue.playTrackNow(trackTitle) }

    /**
     * Sets the bot's volume in the signed-in user's Discord client to an exact level.
     *
     * @param volumePercent The volume from 0 to 200, where 100 is the default level.
     * @return The new volume.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun setBotVolume(volumePercent: Int): BotVolume = run { volume.setBotVolume(volumePercent) }

    /**
     * Raises or lowers the bot's volume in the signed-in user's Discord client, relative to the
     * current level. The result stays within 0 to 200.
     *
     * @param percentagePoints The change in percentage points, not 0. Negative values lower the
     * volume: -20 changes 70 to 50.
     * @return The new volume.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun changeBotVolume(percentagePoints: Int): BotVolume = run { volume.changeBotVolume(percentagePoints) }

    /**
     * Gets the current track, the next tracks in the queue, and the repeat and radio settings.
     *
     * @return The current playback status.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getPlaybackStatus(): PlaybackStatus = run { settings.getPlaybackStatus() }

    /**
     * Turns repeating the current track on or off.
     *
     * @param isEnabled True to repeat the current track, false to stop repeating it.
     * @return The updated playback status.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun setRepeatEnabled(isEnabled: Boolean): PlaybackStatus = run { settings.setRepeatEnabled(isEnabled) }

    /**
     * Turns radio on or off. While on, the bot keeps adding tracks when the queue runs out.
     *
     * @param isEnabled True to turn radio on, false to turn it off.
     * @return The updated playback status.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun setRadioEnabled(isEnabled: Boolean): PlaybackStatus = run { settings.setRadioEnabled(isEnabled) }

    /**
     * Adds the track that the bot is playing to the signed-in user's favorites.
     *
     * @return The track that was added to favorites.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addCurrentTrackToFavorites(): FavoritedTrack = run { favorites.addCurrentTrackToFavorites() }
}
