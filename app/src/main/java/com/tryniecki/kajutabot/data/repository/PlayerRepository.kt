package com.tryniecki.kajutabot.data.repository

import com.tryniecki.kajutabot.api.client.KajutaBotRealtimeClient
import com.tryniecki.kajutabot.api.client.KajutaBotRealtimeClientFactory
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.api.model.queue.EnqueueRequest
import com.tryniecki.kajutabot.api.model.queue.QueueMutationRequest
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.queue.SetQueueRepeatRequest
import com.tryniecki.kajutabot.api.model.queue.SkipQueueRequest
import com.tryniecki.kajutabot.api.model.queue.SwapQueueEntriesRequest
import com.tryniecki.kajutabot.api.model.radio.EnableRadioRequest
import com.tryniecki.kajutabot.api.model.search.SearchResponse
import com.tryniecki.kajutabot.auth.SessionManager
import kotlinx.coroutines.flow.StateFlow

class PlayerRepository(
    private val sessionManager: SessionManager,
    private val apiBaseUrl: String,
) {
    val sessionIdentity: StateFlow<Long?> = sessionManager.sessionIdentity

    suspend fun accessToken(expectedIdentity: Long): String =
        sessionManager.accessTokenForSession(expectedIdentity)

    fun createRealtimeClient(token: String): KajutaBotRealtimeClient =
        KajutaBotRealtimeClientFactory.create(apiBaseUrl, token)

    suspend fun getGuilds(expectedIdentity: Long): List<DiscordGuildResponse> =
        sessionManager.withApiForSession(expectedIdentity) { it.getMyGuilds() }

    suspend fun getVoiceChannels(
        expectedIdentity: Long,
        guildId: String,
    ): List<DiscordVoiceChannelResponse> =
        sessionManager.withApiForSession(expectedIdentity) { it.getVoiceChannels(guildId) }

    suspend fun getQueue(expectedIdentity: Long, guildId: String): QueueSnapshotResponse =
        sessionManager.withApiForSession(expectedIdentity) { it.getQueue(guildId) }

    suspend fun search(
        expectedIdentity: Long,
        query: String,
        source: String,
        maxResults: Int,
    ): SearchResponse = sessionManager.withApiForSession(expectedIdentity) {
        it.search(query = query, source = source, maxResults = maxResults)
    }

    suspend fun enqueue(
        expectedIdentity: Long,
        guildId: String,
        request: EnqueueRequest,
    ): QueueSnapshotResponse = sessionManager.withApiForSession(expectedIdentity) {
        it.enqueue(guildId, request)
    }

    suspend fun skip(
        expectedIdentity: Long,
        guildId: String,
        expectedVersion: Long?,
    ): QueueSnapshotResponse = sessionManager.withApiForSession(expectedIdentity) {
        it.skip(guildId, SkipQueueRequest(expectedVersion = expectedVersion))
    }

    suspend fun stop(
        expectedIdentity: Long,
        guildId: String,
        expectedVersion: Long?,
    ): QueueSnapshotResponse = sessionManager.withApiForSession(expectedIdentity) {
        it.stop(guildId, QueueMutationRequest(expectedVersion = expectedVersion))
    }

    suspend fun setRepeat(
        expectedIdentity: Long,
        guildId: String,
        enabled: Boolean,
        expectedVersion: Long?,
    ): QueueSnapshotResponse = sessionManager.withApiForSession(expectedIdentity) {
        it.setRepeat(guildId, SetQueueRepeatRequest(enabled, expectedVersion))
    }

    suspend fun enableRadio(
        expectedIdentity: Long,
        guildId: String,
        request: EnableRadioRequest,
    ): QueueSnapshotResponse = sessionManager.withApiForSession(expectedIdentity) {
        it.enableRadio(guildId, request)
    }

    suspend fun disableRadio(
        expectedIdentity: Long,
        guildId: String,
        expectedVersion: Long?,
    ): QueueSnapshotResponse = sessionManager.withApiForSession(expectedIdentity) {
        it.disableRadio(guildId, expectedVersion)
    }

    suspend fun removeQueueEntry(
        expectedIdentity: Long,
        guildId: String,
        entryId: String,
        expectedVersion: Long?,
    ): QueueSnapshotResponse = sessionManager.withApiForSession(expectedIdentity) {
        it.removeQueueEntry(guildId, entryId, expectedVersion)
    }

    suspend fun swapQueueEntries(
        expectedIdentity: Long,
        guildId: String,
        firstEntryId: String,
        secondEntryId: String,
        expectedVersion: Long,
    ): QueueSnapshotResponse = sessionManager.withApiForSession(expectedIdentity) {
        it.swapQueueEntries(guildId, SwapQueueEntriesRequest(firstEntryId, secondEntryId, expectedVersion))
    }

    suspend fun clearQueue(
        expectedIdentity: Long,
        guildId: String,
        expectedVersion: Long?,
    ): QueueSnapshotResponse = sessionManager.withApiForSession(expectedIdentity) {
        it.clearPendingQueue(guildId, expectedVersion)
    }
}
