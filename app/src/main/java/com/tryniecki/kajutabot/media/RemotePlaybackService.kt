package com.tryniecki.kajutabot.media

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.KajutaBotApplication
import com.tryniecki.kajutabot.MainActivity
import com.tryniecki.kajutabot.ui.favorites.FavoritesViewModel
import com.tryniecki.kajutabot.ui.player.PlayerViewModel
import com.tryniecki.kajutabot.ui.player.RealtimeOwner
import com.tryniecki.kajutabot.ui.player.nowPlayingPresentationOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Exposes the Discord bot's remote playback; the device never plays audio. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class RemotePlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var container: AppContainer
    private var mediaSession: MediaSession? = null
    private var remotePlayer: RemoteQueuePlayer? = null
    private var playerState: PlayerViewModel? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as KajutaBotApplication).container
        val identity = container.sessionManager.sessionIdentity.value ?: run {
            stopSelf()
            return
        }
        val owner = container.ownerForSession(identity)
        val playerState = ViewModelProvider(owner, PlayerViewModel.factory(container))[PlayerViewModel::class.java]
        this.playerState = playerState
        playerState.setRealtimeOwner(RealtimeOwner.MEDIA_SERVICE, true)
        val favoritesState = ViewModelProvider(owner, FavoritesViewModel.factory(container))[FavoritesViewModel::class.java]
        val player = RemoteQueuePlayer {
            val state = playerState.ui.value
            if (!state.isMutating || state.activeControlAction != null) playerState.skip()
        }
        remotePlayer = player
        player.update(playerState.ui.value.queue?.nowPlayingPresentationOrNull())
        val session = MediaSession.Builder(this, player)
            .setCallback(RemoteSessionCallback(playerState, favoritesState))
            .setMediaButtonPreferences(mediaButtons(playerState, favoritesState))
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        mediaSession = session
        addSession(session)
        container.setMediaServiceActive(true)

        scope.launch {
            playerState.ui.collectLatest { state ->
                player.update(state.queue?.nowPlayingPresentationOrNull())
                session.setMediaButtonPreferences(mediaButtons(playerState, favoritesState))
                // Stop foreground playback promptly when the bot disconnects.
                if (state.queue != null && state.queue.nowPlaying == null && state.queue.voiceChannelId == null) {
                    stopSelf()
                }
            }
        }
        scope.launch {
            favoritesState.ui.collectLatest {
                session.setMediaButtonPreferences(mediaButtons(playerState, favoritesState))
            }
        }
        scope.launch {
            container.sessionManager.sessionIdentity.collectLatest { current ->
                if (current != identity) stopSelf()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        playerState?.setRealtimeOwner(RealtimeOwner.MEDIA_SERVICE, false)
        playerState = null
        if (::container.isInitialized) container.setMediaServiceActive(false)
        scope.cancel()
        mediaSession?.release()
        remotePlayer?.release()
        mediaSession = null
        remotePlayer = null
        super.onDestroy()
    }

    private class RemoteSessionCallback(
        private val player: PlayerViewModel,
        private val favorites: FavoritesViewModel,
    ) : MediaSession.Callback {
        override fun onConnectAsync(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.ConnectionResult> {
            if (!controller.isTrusted && !session.isMediaNotificationController(controller)) {
                return super.onConnectAsync(session, controller)
            }
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(STOP)
                .add(REPEAT)
                .add(RADIO)
                .add(FAVORITE)
                .build()
            return Futures.immediateFuture(
                MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                    .setAvailableSessionCommands(commands)
                    .setAvailablePlayerCommands(
                        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                            .remove(Player.COMMAND_RELEASE)
                            .build(),
                    )
                    .build(),
            )
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val state = player.ui.value
            val queue = state.queue
            val track = state.effectiveNowPlaying?.track
            val playbackControlsBlocked = state.isMutating && state.activeControlAction == null
            if ((!controller.isTrusted && !session.isMediaNotificationController(controller)) ||
                queue == null || track == null || playbackControlsBlocked
            ) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            when (customCommand.customAction) {
                STOP.customAction -> player.stop()
                REPEAT.customAction -> player.setRepeat(!queue.isRepeatEnabled)
                RADIO.customAction -> player.toggleRadio()
                FAVORITE.customAction -> {
                    if (favorites.ui.value.isLoading || favorites.ui.value.isMutating) {
                        return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                    }
                    favorites.toggle(track)
                }
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    companion object {
        const val ACTION_STOP = "com.tryniecki.kajutabot.media.STOP_DISCONNECT"
        const val ACTION_REPEAT = "com.tryniecki.kajutabot.media.REPEAT"
        const val ACTION_RADIO = "com.tryniecki.kajutabot.media.RADIO"
        const val ACTION_FAVORITE = "com.tryniecki.kajutabot.media.FAVORITE"
        val STOP = SessionCommand(ACTION_STOP, Bundle.EMPTY)
        val REPEAT = SessionCommand(ACTION_REPEAT, Bundle.EMPTY)
        val RADIO = SessionCommand(ACTION_RADIO, Bundle.EMPTY)
        val FAVORITE = SessionCommand(ACTION_FAVORITE, Bundle.EMPTY)

        fun start(context: Context) {
            // The app starts this while its UI is in the foreground. MediaSessionService
            // promotes itself to a media-playback foreground service as soon as the
            // Player exposes a MediaItem. Using startForegroundService() here creates a
            // race: remote playback can end before onCreate() publishes a notification,
            // which triggers ForegroundServiceDidNotStartInTimeException.
            context.startService(Intent(context, RemotePlaybackService::class.java))
        }

        fun mediaButtons(player: PlayerViewModel, favorites: FavoritesViewModel): List<CommandButton> {
            val state = player.ui.value
            val queue = state.queue ?: return emptyList()
            val track = state.effectiveNowPlaying?.track ?: return emptyList()
            val playbackControlsBlocked = state.isMutating && state.activeControlAction == null
            val isFavorite = favorites.isFavorite(track)
            return listOf(
                CommandButton.Builder(CommandButton.ICON_NEXT)
                    .setDisplayName("Pomiń utwór")
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                    .setEnabled(!playbackControlsBlocked)
                    .build(),
                CommandButton.Builder(if (queue.isRepeatEnabled) CommandButton.ICON_REPEAT_ALL else CommandButton.ICON_REPEAT_OFF)
                    .setDisplayName(if (queue.isRepeatEnabled) "Wyłącz powtarzanie" else "Włącz powtarzanie")
                    .setSessionCommand(REPEAT)
                    .setEnabled(!playbackControlsBlocked)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_RADIO)
                    .setDisplayName(if (queue.radio.isEnabled) "Wyłącz radio" else "Włącz radio")
                    .setSessionCommand(RADIO)
                    .setEnabled(!playbackControlsBlocked && (queue.radio.isEnabled || state.selectedVoiceChannelId != null))
                    .build(),
                CommandButton.Builder(if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                    .setDisplayName(if (isFavorite) "Usuń z ulubionych" else "Dodaj do ulubionych")
                    .setSessionCommand(FAVORITE)
                    .setEnabled(!playbackControlsBlocked && !favorites.ui.value.isMutating && !favorites.ui.value.isLoading)
                    .build(),
            )
        }
    }

}
