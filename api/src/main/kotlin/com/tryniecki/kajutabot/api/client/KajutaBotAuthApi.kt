package com.tryniecki.kajutabot.api.client

import com.tryniecki.kajutabot.api.model.auth.AuthSessionResponse
import com.tryniecki.kajutabot.api.model.auth.DiscordOAuthExchangeRequest
import com.tryniecki.kajutabot.api.model.auth.RefreshUserSessionRequest
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Anonymous auth operations. Intentionally without Bearer interceptor so that
 * refresh can never receive a stale token or recurse into itself.
 *
 * Paths are relative to a base URL ending with `/api/v1/app/`.
 */
interface KajutaBotAuthApi {
    @POST("auth/guest")
    suspend fun guest(): AuthSessionResponse

    @POST("auth/discord/exchange")
    suspend fun exchange(
        @Body request: DiscordOAuthExchangeRequest,
    ): AuthSessionResponse

    @POST("auth/refresh")
    suspend fun refresh(
        @Body request: RefreshUserSessionRequest,
    ): AuthSessionResponse
}
