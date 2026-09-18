package com.tryniecki.kajutabot.ui

import com.tryniecki.kajutabot.api.client.KajutaBotApiErrors
import com.tryniecki.kajutabot.auth.SessionSignedOutException
import com.tryniecki.kajutabot.auth.TransientSessionException
import retrofit2.HttpException
import java.io.IOException

fun userMessageForError(e: Throwable?): String {
    if (e is SessionSignedOutException) return "Sesja wygasła. Zaloguj się ponownie."
    if (e is TransientSessionException) return e.message ?: "Brak połączenia. Spróbuj ponownie."
    if (e is IOException) return "Brak połączenia z serwerem. Spróbuj ponownie."
    if (e is HttpException) {
        if (e.code() == 429) {
            val retry = KajutaBotApiErrors.retryAfterSeconds(e)
            return if (retry != null) {
                "Zbyt wiele żądań. Spróbuj ponownie za $retry s."
            } else {
                "Zbyt wiele żądań. Odczekaj chwilę i spróbuj ponownie."
            }
        }
        // Best-effort ProblemDetails mapping; body can be consumed only once internally.
        val problem = try {
            KajutaBotApiErrors.problemDetailsOf(e)
        } catch (_: Exception) {
            null
        }
        return when (problem?.errorCode) {
            "queue_version_conflict" -> "Kolejka zmieniła się w międzyczasie. Odświeżono stan."
            "queue_bound_to_other_channel" -> "Kolejka jest powiązana z innym kanałem głosowym."
            "guild_access_denied" -> "Brak dostępu do tego serwera."
            "discord_guild_access_denied" -> "Nie masz dostępu do żadnego serwera obsługiwanego przez KajutaBot."
            "guild_access_service_unavailable" -> "Usługa Discord jest chwilowo niedostępna."
            "search_failed" -> "Wyszukiwanie nie powiodło się. Spróbuj ponownie."
            "rate_limit_exceeded" -> "Zbyt wiele żądań. Odczekaj chwilę."
            "invalid_discord_id" -> "Nieprawidłowy identyfikator Discord."
            else -> when (e.code()) {
                401 -> "Sesja wygasła. Zaloguj się ponownie."
                403 -> "Brak dostępu."
                404 -> "Nie znaleziono zasobu."
                409 -> "Kolejka zmieniła się w międzyczasie. Odświeżono stan."
                502, 503, 504 -> "Serwer jest chwilowo niedostępny. Spróbuj ponownie."
                else -> "Coś poszło nie tak. Spróbuj ponownie."
            }
        }
    }
    return "Coś poszło nie tak. Spróbuj ponownie."
}
