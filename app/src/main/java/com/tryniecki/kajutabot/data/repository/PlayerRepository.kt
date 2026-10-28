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
import com.tryniecki.kajutabot.api.model.auth.SessionType
import kotlinx.coroutines.flow.StateFlow

class PlayerRepository(
    private val sessionManager: SessionManager,
    private val apiBaseUrl: String,
    private val coordinator: QueueMutationCoordinator = QueueMutationCoordinator(),
) {
    val sessionIdentity: StateFlow<Long?> = sessionManager.sessionIdentity
    val apiSnapshots = coordinator.apiSnapshots
    fun isDiscordSession(expectedIdentity: Long): Boolean =
        sessionIdentity.value == expectedIdentity &&
            sessionManager.currentUserSession()?.sessionType == SessionType.DISCORD

    suspend fun accessToken(expectedIdentity: Long): String =
        sessionManager.accessTokenForSession(expectedIdentity)

    fun createRealtimeClient(accessTokenProvider: suspend () -> String): KajutaBotRealtimeClient =
        KajutaBotRealtimeClientFactory.create(apiBaseUrl, accessTokenProvider = accessTokenProvider)

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
        call: suspend (com.tryniecki.kajutabot.api.client.KajutaBotApi, Long, String) -> QueueSnapshotResponse,
    ): QueueSnapshotResponse = coordinator.run(
        guildId = guildId,
        fetch = { getQueue(identity, guildId) },
        mutation = { token, key -> sessionManager.withApiForSession(identity) { call(it, token, key) } },
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
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token, key ->
        api.enqueue(guildId, request.copy(expectedQueueVersion = token), key)
    }

    suspend fun skip(
        expectedIdentity: Long,
        guildId: String,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token, key ->
        api.skip(guildId, SkipQueueRequest(expectedQueueVersion = token), key)
    }

    suspend fun stop(
        expectedIdentity: Long,
        guildId: String,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token, key ->
        api.stop(guildId, QueueMutationRequest(expectedQueueVersion = token), key)
    }

    suspend fun setRepeat(
        expectedIdentity: Long,
        guildId: String,
        enabled: Boolean,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token, key ->
        api.setRepeat(guildId, SetQueueRepeatRequest(enabled, token), key)
    }

    suspend fun enableRadio(
        expectedIdentity: Long,
        guildId: String,
        request: EnableRadioRequest,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token, key ->
        api.enableRadio(guildId, request.copy(expectedQueueVersion = token), key)
    }

    suspend fun disableRadio(
        expectedIdentity: Long,
        guildId: String,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token, key ->
        api.disableRadio(guildId, token, key)
    }

    suspend fun removeQueueEntry(
        expectedIdentity: Long,
        guildId: String,
        entryId: String,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token, key ->
        api.removeQueueEntry(guildId, entryId, token, key)
    }

    suspend fun swapQueueEntries(
        expectedIdentity: Long,
        guildId: String,
        firstEntryId: String,
        secondEntryId: String,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token, key ->
        api.swapQueueEntries(guildId, SwapQueueEntriesRequest(firstEntryId, secondEntryId, token), key)
    }

    suspend fun clearQueue(
        expectedIdentity: Long,
        guildId: String,
    ): QueueSnapshotResponse = mutate(expectedIdentity, guildId) { api, token, key ->
        api.clearPendingQueue(guildId, token, key)
    }
}
