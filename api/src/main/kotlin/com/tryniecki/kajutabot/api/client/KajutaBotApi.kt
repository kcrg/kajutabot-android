package com.tryniecki.kajutabot.api.client

import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.api.model.favorites.AddFavoriteRequest
import com.tryniecki.kajutabot.api.model.favorites.FavoriteResponse
import com.tryniecki.kajutabot.api.model.favorites.QueueFavoritesRequest
import com.tryniecki.kajutabot.api.model.jellyfin.JellyfinAudioPageResponse
import com.tryniecki.kajutabot.api.model.jellyfin.JellyfinConnectionResponse
import com.tryniecki.kajutabot.api.model.jellyfin.JellyfinQuickConnectStartResponse
import com.tryniecki.kajutabot.api.model.jellyfin.JellyfinQuickConnectStatusResponse
import com.tryniecki.kajutabot.api.model.jellyfin.StartJellyfinQuickConnectRequest
import com.tryniecki.kajutabot.api.model.queue.EnqueueRequest
import com.tryniecki.kajutabot.api.model.queue.MoveQueueEntryRequest
import com.tryniecki.kajutabot.api.model.queue.QueueMutationRequest
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.queue.SetQueueRepeatRequest
import com.tryniecki.kajutabot.api.model.queue.SkipQueueRequest
import com.tryniecki.kajutabot.api.model.queue.SwapQueueEntriesRequest
import com.tryniecki.kajutabot.api.model.radio.EnableRadioRequest
import com.tryniecki.kajutabot.api.model.history.PlaybackHistoryPageResponse
import com.tryniecki.kajutabot.api.model.history.EnqueueJellyfinTrackRequest
import com.tryniecki.kajutabot.api.model.search.SearchResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit contract for the mobile-facing, user-scoped subset of KajutaBot Control API v1.
 *
 * Paths are relative to a base URL ending with `/api/v1/app/`.
 * All calls expect `Authorization: Bearer <access-token>` (added by the factory interceptor).
 * The mobile client never sends its own discordUserId for self-scoped data; identity comes from JWT.
 */
interface KajutaBotApi {
    @POST("auth/logout")
    suspend fun logout()

    @GET("users/me/guilds")
    suspend fun getMyGuilds(): List<DiscordGuildResponse>

    @GET("discord/guilds/{guildId}/voice-channels")
    suspend fun getVoiceChannels(
        @Path("guildId") guildId: String,
    ): List<DiscordVoiceChannelResponse>

    @GET("guilds/{guildId}/queue")
    suspend fun getQueue(
        @Path("guildId") guildId: String,
    ): QueueSnapshotResponse

    @GET("guilds/{guildId}/history")
    suspend fun getHistory(
        @Path("guildId") guildId: String,
        @Query("cursor") cursor: Long? = null,
        @Query("pageSize") pageSize: Int = 50,
    ): PlaybackHistoryPageResponse

    @GET("users/me/jellyfin")
    suspend fun getJellyfinConnection(): JellyfinConnectionResponse

    @DELETE("users/me/jellyfin")
    suspend fun disconnectJellyfin()

    @POST("users/me/jellyfin/quick-connect")
    suspend fun startJellyfinQuickConnect(
        @Body request: StartJellyfinQuickConnectRequest,
    ): JellyfinQuickConnectStartResponse

    @GET("users/me/jellyfin/quick-connect/{attemptId}")
    suspend fun getJellyfinQuickConnectStatus(
        @Path("attemptId") attemptId: String,
    ): JellyfinQuickConnectStatusResponse

    @GET("users/me/jellyfin/audio")
    suspend fun getJellyfinAudio(
        @Query("startIndex") startIndex: Int,
        @Query("limit") limit: Int,
        @Query("searchTerm") searchTerm: String? = null,
    ): JellyfinAudioPageResponse

    @POST("users/me/jellyfin/audio/{itemId}/enqueue")
    suspend fun enqueueJellyfinTrack(
        @Path("itemId") itemId: String,
        @Body request: EnqueueJellyfinTrackRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QueueSnapshotResponse

    @POST("guilds/{guildId}/queue/items")
    suspend fun enqueue(
        @Path("guildId") guildId: String,
        @Body request: EnqueueRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QueueSnapshotResponse

    @DELETE("guilds/{guildId}/queue/items/{entryId}")
    suspend fun removeQueueEntry(
        @Path("guildId") guildId: String,
        @Path("entryId") entryId: String,
        @Query("expectedQueueVersion") expectedQueueVersion: Long? = null,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QueueSnapshotResponse

    @PUT("guilds/{guildId}/queue/items/{entryId}/position")
    suspend fun moveQueueEntry(
        @Path("guildId") guildId: String,
        @Path("entryId") entryId: String,
        @Body request: MoveQueueEntryRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QueueSnapshotResponse

    @POST("guilds/{guildId}/queue/items/swap")
    suspend fun swapQueueEntries(
        @Path("guildId") guildId: String,
        @Body request: SwapQueueEntriesRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QueueSnapshotResponse

    @DELETE("guilds/{guildId}/queue/items")
    suspend fun clearPendingQueue(
        @Path("guildId") guildId: String,
        @Query("expectedQueueVersion") expectedQueueVersion: Long? = null,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QueueSnapshotResponse

    @POST("guilds/{guildId}/queue/skip")
    suspend fun skip(
        @Path("guildId") guildId: String,
        @Body request: SkipQueueRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QueueSnapshotResponse

    @PUT("guilds/{guildId}/queue/repeat")
    suspend fun setRepeat(
        @Path("guildId") guildId: String,
        @Body request: SetQueueRepeatRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QueueSnapshotResponse

    @POST("guilds/{guildId}/queue/stop")
    suspend fun stop(
        @Path("guildId") guildId: String,
        @Body request: QueueMutationRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QueueSnapshotResponse

    @PUT("guilds/{guildId}/radio")
    suspend fun enableRadio(
        @Path("guildId") guildId: String,
        @Body request: EnableRadioRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QueueSnapshotResponse

    @DELETE("guilds/{guildId}/radio")
    suspend fun disableRadio(
        @Path("guildId") guildId: String,
        @Query("expectedQueueVersion") expectedQueueVersion: Long? = null,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QueueSnapshotResponse

    @GET("search")
    suspend fun search(
        @Query("query") query: String,
        @Query("source") source: String,
        @Query("maxResults") maxResults: Int,
    ): SearchResponse

    @GET("users/me/favorites")
    suspend fun getFavorites(): List<FavoriteResponse>

    @POST("users/me/favorites")
    suspend fun addFavorite(
        @Body request: AddFavoriteRequest,
    ): FavoriteResponse

    @DELETE("users/me/favorites")
    suspend fun deleteFavorite(
        @Query("contentUrl") contentUrl: String,
    )

    @POST("users/me/favorites/queue")
    suspend fun queueFavorites(
        @Body request: QueueFavoritesRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): QueueSnapshotResponse
}
