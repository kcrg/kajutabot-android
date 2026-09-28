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
    private val coordinator: QueueMutationCoordinator = QueueMutationCoordinator(),
) {
    val sessionIdentity: StateFlow<Long?> = sessionManager.sessionIdentity
    val apiSnapshots = coordinator.apiSnapshots

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
        sessionManager.withApiForSession(expectedIdentity) { it.getQueue(guildId) }.also(coordinator::publish)

    fun observe(snapshot: QueueSnapshotResponse) = coordinator.observe(snapshot)

    private suspend fun mutate(
        identity: Long,
        guildId: String,
        call: suspend (com.tryniecki.kajutabot.api.client.KajutaBotApi, Long) -> QueueSnapshotResponse,
    ): QueueSnapshotResponse = coordinator.run(
        guildId = guildId,
        fetch = { getQueue(identity, guildId) },
        mutation = { token -> sessionManager.withApiForSession(identity) { call(it, token) } },
    )

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
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token ->
        api.enqueue(guildId, request.copy(expectedQueueVersion = token))
    }

    suspend fun skip(
        expectedIdentity: Long,
        guildId: String,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token ->
        api.skip(guildId, SkipQueueRequest(expectedQueueVersion = token))
    }

    suspend fun stop(
        expectedIdentity: Long,
        guildId: String,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token ->
        api.stop(guildId, QueueMutationRequest(expectedQueueVersion = token))
    }

    suspend fun setRepeat(
        expectedIdentity: Long,
        guildId: String,
        enabled: Boolean,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token ->
        api.setRepeat(guildId, SetQueueRepeatRequest(enabled, token))
    }

    suspend fun enableRadio(
        expectedIdentity: Long,
        guildId: String,
        request: EnableRadioRequest,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token ->
        api.enableRadio(guildId, request.copy(expectedQueueVersion = token))
    }

    suspend fun disableRadio(
        expectedIdentity: Long,
        guildId: String,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token ->
        api.disableRadio(guildId, token)
    }

    suspend fun removeQueueEntry(
        expectedIdentity: Long,
        guildId: String,
        entryId: String,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token ->
        api.removeQueueEntry(guildId, entryId, token)
    }

    suspend fun swapQueueEntries(
        expectedIdentity: Long,
        guildId: String,
        firstEntryId: String,
        secondEntryId: String,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token ->
        api.swapQueueEntries(guildId, SwapQueueEntriesRequest(firstEntryId, secondEntryId, token))
    }

    suspend fun clearQueue(
        expectedIdentity: Long,
        guildId: String,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token ->
        api.clearPendingQueue(guildId, token)
    }
}
