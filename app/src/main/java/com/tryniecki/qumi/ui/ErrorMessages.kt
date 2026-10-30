package com.tryniecki.qumi.ui

import com.tryniecki.qumi.R
import com.tryniecki.qumi.api.client.ApiErrors
import com.tryniecki.qumi.api.model.error.ProblemDetails
import com.tryniecki.qumi.auth.ContractSessionException
import com.tryniecki.qumi.auth.PersistenceSessionException
import com.tryniecki.qumi.auth.SessionSignedOutException
import com.tryniecki.qumi.auth.TransientSessionException
import com.tryniecki.qumi.ui.text.UiText
import com.tryniecki.qumi.ui.text.uiText
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

fun userMessageForError(e: Throwable?, parsedProblem: ProblemDetails? = null): UiText {
    if (e is SessionSignedOutException) return uiText(R.string.auth_session_expired)
    if (e is TransientSessionException) {
        if (e.cause is HttpException || e.cause is SocketTimeoutException) {
            return userMessageForError(e.cause)
        }
        return uiText(R.string.error_connection)
    }
    if (e is ContractSessionException) return uiText(R.string.error_invalid_server_response)
    if (e is PersistenceSessionException) return uiText(R.string.error_session_save)
    if (e is SocketTimeoutException) return uiText(R.string.error_timeout)
    if (e is IOException) return uiText(R.string.auth_no_server_connection)
    if (e is HttpException) {
        if (e.code() == 429) {
            val retry = ApiErrors.retryAfterSeconds(e)
            return if (retry != null && retry > 0) {
                uiText(R.string.error_rate_limit_retry_seconds, retry)
            } else {
                uiText(R.string.error_rate_limit_wait)
            }
        }
        // Map backend error codes to app-owned localized UI copy. We intentionally do not
        // translate or rewrite arbitrary response payload text from the API.
        val problem = parsedProblem ?: runCatching { ApiErrors.problemDetailsOf(e) }.getOrNull()
        return when (problem?.errorCode) {
            "worker_ownership_lost", "worker_not_ready" -> uiText(R.string.error_server_unavailable)
            "idempotency_in_progress" -> uiText(R.string.error_operation_in_progress)
            "idempotency_key_conflict" -> uiText(R.string.error_operation_key_conflict)
            "queue_version_conflict" -> uiText(R.string.error_queue_conflict)
            "queue_persistence_conflict" -> uiText(R.string.error_queue_persistence_conflict)
            "queue_full" -> uiText(R.string.error_queue_full)
            "content_unavailable" -> uiText(R.string.error_content_unavailable)
            "queue_bound_to_other_channel" -> uiText(R.string.error_queue_other_channel)
            "voice_channel_access_denied" -> uiText(R.string.error_voice_channel_access)
            "voice_channel_changed" -> uiText(R.string.error_voice_channel_changed)
            "guild_access_denied" -> uiText(R.string.error_guild_access)
            "discord_guild_access_denied" -> uiText(R.string.error_discord_guild_access)
            "guild_access_service_unavailable" -> uiText(R.string.error_discord_unavailable)
            "search_failed" -> uiText(R.string.error_search_failed)
            "rate_limit_exceeded" -> uiText(R.string.error_rate_limit_short)
            "invalid_discord_id" -> uiText(R.string.error_invalid_discord_id)
            "invalid_server_url" -> uiText(R.string.error_jellyfin_invalid_url)
            "remote_endpoint_not_allowed" -> uiText(R.string.error_jellyfin_address_not_allowed)
            "network_error" -> uiText(R.string.jellyfin_link_unreachable)
            "jellyfin_timeout" -> uiText(R.string.error_jellyfin_timeout)
            "jellyfin_unavailable" -> uiText(R.string.error_jellyfin_unavailable)
            "jellyfin_authorization_failed" -> uiText(R.string.error_jellyfin_authorization)
            "jellyfin_not_connected" -> uiText(R.string.error_jellyfin_not_connected)
            else -> when (e.code()) {
                401 -> uiText(R.string.auth_session_expired)
                403 -> uiText(R.string.error_access_denied)
                404 -> uiText(R.string.error_not_found)
                409 -> uiText(R.string.error_queue_conflict)
                in 500..599 -> uiText(R.string.error_server_unavailable)
                else -> uiText(R.string.error_generic)
            }
        }
    }
    return uiText(R.string.error_generic)
}
