package com.tryniecki.kajutabot.ui

import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.api.client.KajutaBotApiErrors
import com.tryniecki.kajutabot.auth.ContractSessionException
import com.tryniecki.kajutabot.auth.PersistenceSessionException
import com.tryniecki.kajutabot.auth.SessionSignedOutException
import com.tryniecki.kajutabot.auth.TransientSessionException
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.ui.text.uiText
import retrofit2.HttpException
import java.io.IOException

fun userMessageForError(e: Throwable?): UiText {
    if (e is SessionSignedOutException) return uiText(R.string.auth_session_expired)
    if (e is TransientSessionException) return uiText(R.string.error_connection)
    if (e is ContractSessionException) return uiText(R.string.error_invalid_server_response)
    if (e is PersistenceSessionException) return uiText(R.string.error_session_save)
    if (e is IOException) return uiText(R.string.auth_no_server_connection)
    if (e is HttpException) {
        if (e.code() == 429) {
            val retry = KajutaBotApiErrors.retryAfterSeconds(e)
            return if (retry != null) {
                uiText(R.string.error_rate_limit_retry_seconds, retry)
            } else {
                uiText(R.string.error_rate_limit_wait)
            }
        }
        // Map backend error codes to app-owned localized UI copy. We intentionally do not
        // translate or rewrite arbitrary response payload text from the API.
        val problem = try {
            KajutaBotApiErrors.problemDetailsOf(e)
        } catch (_: Exception) {
            null
        }
        return when (problem?.errorCode) {
            "queue_version_conflict" -> uiText(R.string.error_queue_conflict)
            "queue_bound_to_other_channel" -> uiText(R.string.error_queue_other_channel)
            "guild_access_denied" -> uiText(R.string.error_guild_access)
            "discord_guild_access_denied" -> uiText(R.string.error_discord_guild_access)
            "guild_access_service_unavailable" -> uiText(R.string.error_discord_unavailable)
            "search_failed" -> uiText(R.string.error_search_failed)
            "rate_limit_exceeded" -> uiText(R.string.error_rate_limit_short)
            "invalid_discord_id" -> uiText(R.string.error_invalid_discord_id)
            else -> when (e.code()) {
                401 -> uiText(R.string.auth_session_expired)
                403 -> uiText(R.string.error_access_denied)
                404 -> uiText(R.string.error_not_found)
                409 -> uiText(R.string.error_queue_conflict)
                502, 503, 504 -> uiText(R.string.error_server_unavailable)
                else -> uiText(R.string.error_generic)
            }
        }
    }
    return uiText(R.string.error_generic)
}
