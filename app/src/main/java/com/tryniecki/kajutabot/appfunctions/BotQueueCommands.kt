package com.tryniecki.kajutabot.appfunctions

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.queue.EnqueueRequest
import com.tryniecki.kajutabot.api.model.queue.QueueEntryResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.queue.SkipOutcome
import com.tryniecki.kajutabot.api.model.search.SearchItemResponse
import com.tryniecki.kajutabot.data.repository.PlayerRepository
import com.tryniecki.kajutabot.ui.player.SearchSourceOption
import kotlinx.coroutines.CancellationException

/** Skipping and adding tracks. Results go through [PlayerRepository], so open screens follow. */
internal class BotQueueCommands(
    private val player: PlayerRepository,
    private val targets: PlaybackTargetResolver,
) {
    suspend fun skipCurrentTrack(): SkippedTrack {
        val target = targets.target()
        val current = player.getQueue(target.identity, target.guildId).nowPlaying
            ?: throw AppFunctionElementNotFoundException("Nothing is playing.")
        val result = player.skip(target.identity, target.guildId)
        return SkippedTrack(current.title, result.skipOutcome == SkipOutcome.RestartedRepeatedTrack)
    }

    suspend fun addTrackToQueue(trackTitle: String): QueuedTrack {
        val target = targets.target()
        val found = findTrack(target, trackTitle)
        val added = enqueue(target, found)
        return QueuedTrack(
            title = added.addedTrack()?.title ?: found.track.title,
            queuePosition = added.entryFor(found)?.let { added.pendingEntries.indexOf(it) + 1 },
        )
    }

    suspend fun playTrackNow(trackTitle: String): PlayedTrack {
        val target = targets.target()
        val found = findTrack(target, trackTitle)
        val added = enqueue(target, found)
        val title = added.addedTrack()?.title ?: found.track.title
        // Nothing was playing, so the bot started this track on its own.
        val entry = added.entryFor(found) ?: return PlayedTrack(title, isPlayingNext = true)
        try {
            val first = added.pendingEntries.minBy(QueueEntryResponse::position)
            if (first.entryId != entry.entryId) {
                player.moveQueueEntry(target.identity, target.guildId, entry.entryId, first.position)
            }
            if (added.nowPlaying == null) return PlayedTrack(title, isPlayingNext = true)
            val skipped = player.skip(target.identity, target.guildId)
            return PlayedTrack(title, isPlayingNext = skipped.skipOutcome != SkipOutcome.RestartedRepeatedTrack)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            throw AppFunctionAppUnknownException(
                "\"$title\" was added to the queue, but it could not be played right away.",
            )
        }
    }

    private suspend fun findTrack(target: PlaybackTarget, trackTitle: String): SearchItemResponse {
        val query = trackTitle.trim()
        if (query.isEmpty()) throw AppFunctionInvalidArgumentException("The track title cannot be blank.")
        return player.search(target.identity, query, SearchSourceOption.SMART.apiValue, maxResults = 1)
            .items.firstOrNull()
            ?: throw AppFunctionElementNotFoundException("No track matches \"$query\".")
    }

    private suspend fun enqueue(target: PlaybackTarget, item: SearchItemResponse): QueueSnapshotResponse {
        val queue = player.getQueue(target.identity, target.guildId)
        val channelId = queue.voiceChannelId ?: target.selectedChannelId ?: throw targets.missingVoiceChannel()
        return player.enqueue(target.identity, target.guildId, EnqueueRequest(channelId, listOf(item.input)))
    }

    private fun QueueSnapshotResponse.addedTrack(): PlaybackTrackResponse? = addedTracks?.firstOrNull()

    private fun QueueSnapshotResponse.entryFor(item: SearchItemResponse): QueueEntryResponse? {
        val url = addedTrack()?.url ?: item.input
        return pendingEntries.lastOrNull { it.track.url == url }
    }
}
