package com.tryniecki.kajutabot.api.client

import com.tryniecki.kajutabot.api.model.auth.AuthUserResponse
import com.tryniecki.kajutabot.api.model.common.HealthResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.api.model.favorites.AddFavoriteRequest
import com.tryniecki.kajutabot.api.model.favorites.FavoriteResponse
import com.tryniecki.kajutabot.api.model.favorites.QueueFavoritesRequest
import com.tryniecki.kajutabot.api.model.queue.EnqueueRequest
import com.tryniecki.kajutabot.api.model.queue.EnqueueResponse
import com.tryniecki.kajutabot.api.model.queue.MoveQueueEntryRequest
import com.tryniecki.kajutabot.api.model.queue.QueueMutationRequest
import com.tryniecki.kajutabot.api.model.queue.QueueMutationResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.queue.SetQueueRepeatRequest
import com.tryniecki.kajutabot.api.model.queue.SkipQueueRequest
import com.tryniecki.kajutabot.api.model.radio.EnableRadioRequest
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import com.tryniecki.kajutabot.api.model.search.SearchResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit contract for the mobile-facing, user-scoped subset of KajutaBot Control API v1.
 *
 * Paths are relative to a base URL ending with `/api/v1/`.
 * All calls expect `Authorization: Bearer <access-token>` (added by the factory interceptor).
 * The mobile client never sends its own discordUserId for self-scoped data; identity comes from JWT.
 */
interface KajutaBotApi {
    @GET("health")
    suspend fun getHealth(): HealthResponse

    @POST("auth/logout")
    suspend fun logout()

    @GET("auth/me")
    suspend fun getMe(): AuthUserResponse

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

    @POST("guilds/{guildId}/queue/items")
    suspend fun enqueue(
        @Path("guildId") guildId: String,
        @Body request: EnqueueRequest,
    ): EnqueueResponse

    @DELETE("guilds/{guildId}/queue/items/{entryId}")
    suspend fun removeQueueEntry(
        @Path("guildId") guildId: String,
        @Path("entryId") entryId: String,
        @Query("expectedVersion") expectedVersion: Long? = null,
    ): QueueMutationResponse

    @PUT("guilds/{guildId}/queue/items/{entryId}/position")
    suspend fun moveQueueEntry(
        @Path("guildId") guildId: String,
        @Path("entryId") entryId: String,
        @Body request: MoveQueueEntryRequest,
    ): QueueMutationResponse

    @DELETE("guilds/{guildId}/queue/items")
    suspend fun clearPendingQueue(
        @Path("guildId") guildId: String,
        @Query("expectedVersion") expectedVersion: Long? = null,
    ): QueueMutationResponse

    @POST("guilds/{guildId}/queue/skip")
    suspend fun skip(
        @Path("guildId") guildId: String,
        @Body request: SkipQueueRequest,
    ): QueueMutationResponse

    @PUT("guilds/{guildId}/queue/repeat")
    suspend fun setRepeat(
        @Path("guildId") guildId: String,
        @Body request: SetQueueRepeatRequest,
    ): QueueMutationResponse

    @POST("guilds/{guildId}/queue/stop")
    suspend fun stop(
        @Path("guildId") guildId: String,
        @Body request: QueueMutationRequest,
    ): QueueMutationResponse

    @GET("guilds/{guildId}/radio")
    suspend fun getRadioState(
        @Path("guildId") guildId: String,
    ): RadioStateResponse

    @PUT("guilds/{guildId}/radio")
    suspend fun enableRadio(
        @Path("guildId") guildId: String,
        @Body request: EnableRadioRequest,
    ): QueueMutationResponse

    @DELETE("guilds/{guildId}/radio")
    suspend fun disableRadio(
        @Path("guildId") guildId: String,
        @Query("expectedVersion") expectedVersion: Long? = null,
    ): QueueMutationResponse

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
    ): QueueMutationResponse
}
