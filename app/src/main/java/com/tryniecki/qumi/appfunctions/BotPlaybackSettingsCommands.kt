package com.tryniecki.qumi.appfunctions

import com.tryniecki.qumi.data.repository.PlayerRepository
import com.tryniecki.qumi.ui.player.RadioToggleAction
import com.tryniecki.qumi.ui.player.decideRadioToggle

private const val NEXT_TRACKS_LIMIT = 5

/** Reading playback state and switching repeat and radio. */
internal class BotPlaybackSettingsCommands(
    private val player: PlayerRepository,
    private val targets: PlaybackTargetResolver,
) {
    suspend fun getPlaybackStatus(): PlaybackStatus {
        val target = targets.target()
        val queue = player.getQueue(target.identity, target.guildId)
        return PlaybackStatus(
            currentTrackTitle = queue.nowPlaying?.title,
            currentTrackDurationSeconds = queue.nowPlaying?.durationMilliseconds?.div(1_000L),
            queuedTrackCount = queue.pendingEntriesCount,
            nextTrackTitles = queue.pendingEntries.take(NEXT_TRACKS_LIMIT).map { it.track.title },
            isRepeatEnabled = queue.isRepeatEnabled,
            isRadioEnabled = queue.radio.isEnabled,
        )
    }

    suspend fun setRepeatEnabled(isEnabled: Boolean): PlaybackStatus {
        val target = targets.target()
        player.setRepeat(target.identity, target.guildId, isEnabled)
        return getPlaybackStatus()
    }

    suspend fun setRadioEnabled(isEnabled: Boolean): PlaybackStatus {
        val target = targets.target()
        val queue = player.getQueue(target.identity, target.guildId)
        if (queue.radio.isEnabled == isEnabled) return getPlaybackStatus()
        when (val action = decideRadioToggle(queue, queue.voiceChannelId ?: target.selectedChannelId)) {
            is RadioToggleAction.Enable -> player.enableRadio(target.identity, target.guildId, action.request)
            is RadioToggleAction.Disable -> player.disableRadio(target.identity, target.guildId)
            RadioToggleAction.MissingVoiceChannel -> throw targets.missingVoiceChannel()
        }
        return getPlaybackStatus()
    }
}
