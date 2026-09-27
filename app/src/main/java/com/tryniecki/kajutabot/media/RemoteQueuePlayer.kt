package com.tryniecki.kajutabot.media

import android.net.Uri
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tryniecki.kajutabot.ui.components.ArtworkSource
import com.tryniecki.kajutabot.ui.components.resolveArtworkSource
import com.tryniecki.kajutabot.ui.player.NowPlayingPresentation
import com.tryniecki.kajutabot.ui.player.backendPositionMs
import com.tryniecki.kajutabot.ui.player.playbackIdentity

/** Media3 projection of the bot's queue. It never opens or renders an audio stream. */
@UnstableApi
class RemoteQueuePlayer(
    private val onSkip: () -> Unit,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private var nowPlaying: NowPlayingPresentation? = null
    private var artworkData: ByteArray? = null

    fun update(presentation: NowPlayingPresentation?) {
        verifyApplicationThread()
        if (nowPlaying == presentation) return
        if (nowPlaying?.track?.artworkUrl != presentation?.track?.artworkUrl) artworkData = null
        nowPlaying = presentation
        invalidateState()
    }

    fun updateArtwork(artworkUrl: String, data: ByteArray) {
        verifyApplicationThread()
        if (nowPlaying?.track?.artworkUrl != artworkUrl || artworkData?.contentEquals(data) == true) return
        artworkData = data
        invalidateState()
    }

    override fun getState(): State {
        val commands = Player.Commands.Builder()
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_METADATA)
            .add(Player.COMMAND_RELEASE)
        val presentation = nowPlaying ?: return State.Builder()
            .setAvailableCommands(commands.build())
            .setPlaybackState(Player.STATE_IDLE)
            .build()
        val track = presentation.track

        commands.add(Player.COMMAND_SEEK_TO_NEXT)
        val identity = playbackIdentity(track, presentation.playbackInstanceId)
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setDisplayTitle(track.title)
            .apply {
                val artwork = resolveArtworkSource(track.artworkUrl)
                if (artwork is ArtworkSource.Remote) setArtworkUri(Uri.parse(artwork.url))
                artworkData?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
                // The Control API's PlaybackTrackResponse has no artist/author field.
            }
            .build()
        val item = MediaItem.Builder()
            .setMediaId(identity)
            .setMediaMetadata(metadata)
            .build()
        val durationMs = track.durationMilliseconds
        val durationUs = if (durationMs > 0) durationMs * 1_000 else C.TIME_UNSET
        val positionMs = backendPositionMs(presentation.positionMs, durationMs) ?: 0L
        return State.Builder()
            .setAvailableCommands(commands.build())
            .setPlaylist(
                listOf(MediaItemData.Builder(CURRENT_ITEM_UID)
                    .setMediaItem(item)
                    .setDurationUs(durationUs)
                    .build()),
            )
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(positionMs)
            .setPlaybackState(if (presentation.isPlaying) Player.STATE_READY else Player.STATE_BUFFERING)
            // Reports remote progress; COMMAND_PLAY_PAUSE is deliberately unavailable.
            .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        if (seekCommand == Player.COMMAND_SEEK_TO_NEXT) onSkip()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private companion object {
        const val CURRENT_ITEM_UID = "remote-current-item"
    }
}
