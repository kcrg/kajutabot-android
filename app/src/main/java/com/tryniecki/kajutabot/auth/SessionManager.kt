package com.tryniecki.kajutabot.auth

import com.tryniecki.kajutabot.api.client.KajutaBotApi
import com.tryniecki.kajutabot.api.client.KajutaBotApiErrors
import com.tryniecki.kajutabot.api.client.KajutaBotAuthApi
import com.tryniecki.kajutabot.api.model.auth.AuthSessionResponse
import com.tryniecki.kajutabot.api.model.auth.DiscordOAuthExchangeRequest
import com.tryniecki.kajutabot.api.model.auth.RefreshUserSessionRequest
import com.tryniecki.kajutabot.api.model.auth.SessionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

class SessionSignedOutException(message: String, cause: Throwable? = null) : Exception(message, cause)

class TransientSessionException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ContractSessionException(message: String, cause: Throwable? = null) : Exception(message, cause)
class PersistenceSessionException(message: String, cause: Throwable? = null) : Exception(message, cause)

sealed interface OAuthStartResult {
    data class Ready(val url: String) : OAuthStartResult
    data class Misconfigured(val message: String) : OAuthStartResult
}

sealed interface OAuthCallbackResult {
    data object Exchanged : OAuthCallbackResult
    data class Failed(val message: String) : OAuthCallbackResult
    data class Ignored(val reason: String) : OAuthCallbackResult
}

sealed interface LogoutResult {
    data object SignedOut : LogoutResult
    data class LocalOnly(val message: String) : LogoutResult
    data class NeedsRetry(val message: String) : LogoutResult
}

sealed interface GuestLoginResult {
    data object SignedIn : GuestLoginResult
    data class Failed(val message: String) : GuestLoginResult
    data object Superseded : GuestLoginResult
}

class SessionManager(
    private val authApi: KajutaBotAuthApi,
    private val apiProvider: (String) -> KajutaBotApi,
    private val sessionStore: SessionStore,
    private val pendingStorage: OAuthPendingStorage,
    private val appConfig: AppConfig,
    private val pkceGenerator: PkceGenerator = PkceGenerator(),
    private val clock: () -> Instant = Instant::now,
    private val refreshScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Restoring)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _sessionIdentity = MutableStateFlow<Long?>(null)
    val sessionIdentity: StateFlow<Long?> = _sessionIdentity.asStateFlow()

    private val lock = Any()
    private var generation = 0L
    private data class RefreshFlight(val generation: Long, val tokenKey: String, val deferred: Deferred<UserSession>)
    private var refreshFlight: RefreshFlight? = null

    @Volatile
    private var currentSession: UserSession? = null

    fun currentAccessToken(): String? = currentSession?.accessToken

    /** Fresh token for a particular login, including SignalR reconnects. */
    suspend fun accessTokenForSession(expectedIdentity: Long): String {
        val epoch = synchronized(lock) {
            if (_sessionIdentity.value != expectedIdentity) {
                throw SessionSignedOutException("Sesja zmieniła się przed żądaniem.")
            }
            generation
        }
        ensureFreshToken(force = false, failedAccessToken = null, expectedGeneration = epoch)
        return tokenForSession(epoch, expectedIdentity)
    }

    fun currentUserSession(): UserSession? = currentSession

    suspend fun restore() {
        val epoch = synchronized(lock) {
            _authState.value = AuthState.Restoring
            generation
        }
        val stored = try {
            sessionStore.load()
        } catch (e: Exception) {
            synchronized(lock) {
                if (generation == epoch) _authState.value =
                    AuthState.RecoverableError("Nie można odczytać bezpiecznego magazynu sesji.")
            }
            return
        }
        synchronized(lock) {
            if (generation != epoch) return
            if (stored != null && !isValidSession(stored)) {
                generation++
                try {
                    clearLocalSession("Sesja wygasła. Zaloguj się ponownie.")
                } catch (_: Exception) {
                    _authState.value = AuthState.RecoverableError("Nie można usunąć wygasłej sesji.")
                }
                return
            }
            currentSession = stored
            _sessionIdentity.value = if (stored == null) null else epoch
            _authState.value = if (stored == null) AuthState.SignedOut() else AuthState.SignedIn(stored.user)
        }
        if (stored == null || !isExpiringSoon(stored)) return
        try {
            ensureFreshToken(force = false, failedAccessToken = null, expectedGeneration = epoch)
        } catch (e: CancellationException) {
            throw e
        } catch (_: SessionSignedOutException) {
            // Invalid refresh already changed the state.
        } catch (e: Exception) {
            synchronized(lock) {
                if (generation == epoch) _authState.value = AuthState.RecoverableError(
                    e.message ?: "Nie można odnowić sesji.",
                )
            }
        }
    }

    suspend fun retryRestore() {
        restore()
    }

    fun startLogin(): OAuthStartResult {
        if (!appConfig.isOAuthConfigured) {
            return OAuthStartResult.Misconfigured(
                "Brak konfiguracji Discord Client ID. Uzupełnij KAJUTABOT_DISCORD_CLIENT_ID.",
            )
        }
        val pkce = pkceGenerator.generate()
        try {
            synchronized(lock) {
                generation++
                refreshFlight = null
                pendingStorage.save(pkce.state, pkce.verifier)
            }
        } catch (_: Exception) {
            return OAuthStartResult.Misconfigured("Nie można zapisać próby logowania.")
        }
        val url = DiscordOAuth.buildAuthorizationUrl(
            clientId = appConfig.discordClientId,
            redirectUri = appConfig.redirectUri,
            codeChallenge = pkce.challenge,
            state = pkce.state,
        )
        return OAuthStartResult.Ready(url)
    }

    suspend fun continueAsGuest(): GuestLoginResult {
        val epoch = synchronized(lock) {
            refreshFlight = null
            ++generation
        }
        return try {
            val session = authApi.guest().toUserSession()
            require(session.sessionType == SessionType.GUEST)
            synchronized(lock) {
                if (generation != epoch) return GuestLoginResult.Superseded
                sessionStore.save(session)
                currentSession = session
                _sessionIdentity.value = epoch
                _authState.value = AuthState.SignedIn(session.user)
                try { pendingStorage.clear() } catch (_: Exception) { /* Stale PKCE expires. */ }
            }
            GuestLoginResult.SignedIn
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = guestErrorMessage(e)
            synchronized(lock) {
                if (generation == epoch && currentSession == null) _authState.value = AuthState.SignedOut(message)
            }
            GuestLoginResult.Failed(message)
        }
    }

    suspend fun handleOAuthCallback(
        code: String?,
        returnedState: String?,
        error: String?,
    ): OAuthCallbackResult {
        if (error != null) {
            val pending = try {
                pendingStorage.loadValid()
            } catch (_: Exception) {
                return OAuthCallbackResult.Failed("Nie można odczytać próby logowania.")
            }
            synchronized(lock) {
                if (pending == null || pending.state != returnedState) {
                    return OAuthCallbackResult.Ignored("Nowsza próba logowania zastąpiła tę odpowiedź.")
                }
                generation++
                pendingStorage.clear()
            }
            val message = if (error == "access_denied") {
                "Logowanie przez Discord zostało anulowane."
            } else {
                "Logowanie nie powiodło się. Spróbuj ponownie."
            }
            if (_authState.value !is AuthState.SignedIn) {
                _authState.value = AuthState.SignedOut(message)
            }
            return OAuthCallbackResult.Failed(message)
        }

        val pending = try {
            pendingStorage.loadValid()
        } catch (_: Exception) {
            return OAuthCallbackResult.Failed("Nie można odczytać próby logowania.")
        }
        if (pending == null) {
            return OAuthCallbackResult.Ignored("Brak aktywnej sesji logowania lub wygasła.")
        }
        if (returnedState.isNullOrBlank() || returnedState != pending.state) {
            pendingStorage.clear()
            return OAuthCallbackResult.Ignored("Nieprawidłowy stan logowania.")
        }
        if (code.isNullOrBlank()) {
            pendingStorage.clear()
            val message = "Brak kodu autoryzacji. Spróbuj ponownie."
            if (_authState.value !is AuthState.SignedIn) {
                _authState.value = AuthState.SignedOut(message)
            }
            return OAuthCallbackResult.Failed(message)
        }

        val epoch = synchronized(lock) { ++generation }
        return try {
            val response = authApi.exchange(
                DiscordOAuthExchangeRequest(
                    code = code,
                    codeVerifier = pending.codeVerifier,
                    redirectUri = appConfig.redirectUri,
                ),
            )
            val session = response.toUserSession()
            synchronized(lock) {
                if (generation != epoch) return OAuthCallbackResult.Ignored("Nowsza próba logowania zastąpiła tę odpowiedź.")
                sessionStore.save(session)
                currentSession = session
                _sessionIdentity.value = epoch
                _authState.value = AuthState.SignedIn(session.user)
                try {
                    pendingStorage.clear()
                } catch (_: Exception) {
                    // The committed session remains valid; stale PKCE data expires by itself.
                }
            }
            OAuthCallbackResult.Exchanged
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            synchronized(lock) { if (generation == epoch) pendingStorage.clear() }
            val problem = KajutaBotApiErrors.problemDetailsOf(e)
            val message = mapExchangeError(problem?.errorCode, e.code())
            if (generation == epoch && _authState.value !is AuthState.SignedIn) {
                _authState.value = AuthState.SignedOut(message)
            }
            OAuthCallbackResult.Failed(message)
        } catch (_: IOException) {
            synchronized(lock) { if (generation == epoch) pendingStorage.clear() }
            val message = "Brak połączenia z serwerem. Spróbuj ponownie."
            if (generation == epoch && _authState.value !is AuthState.SignedIn) {
                _authState.value = AuthState.SignedOut(message)
            }
            OAuthCallbackResult.Failed(message)
        } catch (_: Exception) {
            synchronized(lock) { if (generation == epoch) pendingStorage.clear() }
            val message = "Nie można zapisać sesji lub przetworzyć odpowiedzi serwera."
            if (generation == epoch && _authState.value !is AuthState.SignedIn) {
                _authState.value = AuthState.SignedOut(message)
            }
            OAuthCallbackResult.Failed(message)
        }
    }

    suspend fun logout(): LogoutResult {
        // Invalidate every in-flight restore/exchange/refresh before the first suspension.
        val (epoch, original, oldFlight) = synchronized(lock) {
            val old = refreshFlight
            refreshFlight = null
            Triple(++generation, currentSession, old)
        }
        if (original == null) {
            return try {
                synchronized(lock) { if (generation == epoch) clearLocalSession() }
                LogoutResult.LocalOnly("Zakończono tylko lokalną sesję.")
            } catch (_: Exception) {
                LogoutResult.NeedsRetry("Nie można usunąć lokalnej sesji.")
            }
        }
        if (original.sessionType == SessionType.GUEST) {
            return try {
                synchronized(lock) {
                    requireGeneration(epoch)
                    clearLocalSession()
                }
                LogoutResult.SignedOut
            } catch (_: Exception) {
                LogoutResult.NeedsRetry("Nie można usunąć lokalnej sesji.")
            }
        }
        return try {
            var session = original
            if (oldFlight?.generation == epoch - 1 || isExpiringSoon(session)) {
                session = if (oldFlight?.generation == epoch - 1) {
                    oldFlight.deferred.await()
                } else {
                    requestRefresh(session)
                }
                commitLogoutRefresh(epoch, session)
            }
            try {
                synchronized(lock) { requireGeneration(epoch) }
                apiProvider(session.accessToken).logout()
            } catch (e: HttpException) {
                if (e.code() != 401) throw e
                // An access-token 401 is not proof that the refresh token was revoked.
                session = requestRefresh(session)
                commitLogoutRefresh(epoch, session)
                apiProvider(session.accessToken).logout()
            }
            synchronized(lock) {
                requireGeneration(epoch)
                clearLocalSession()
            }
            LogoutResult.SignedOut
        } catch (e: CancellationException) {
            throw e
        } catch (_: SessionSignedOutException) {
            return try {
                synchronized(lock) {
                    if (generation == epoch) clearLocalSession(
                        "Zakończono lokalnie; serwer nie potwierdził unieważnienia tokenu odświeżania.",
                    )
                }
                LogoutResult.LocalOnly("Serwer nie potwierdził unieważnienia tokenu odświeżania.")
            } catch (_: Exception) {
                LogoutResult.NeedsRetry("Nie można usunąć lokalnej sesji.")
            }
        } catch (e: HttpException) {
            if (e.code() == 401) {
                try {
                    synchronized(lock) {
                        if (generation == epoch) clearLocalSession(
                            "Zakończono lokalnie; serwer nie potwierdził unieważnienia tokenu odświeżania.",
                        )
                    }
                    LogoutResult.LocalOnly("Serwer nie potwierdził unieważnienia tokenu odświeżania.")
                } catch (_: Exception) {
                    LogoutResult.NeedsRetry("Nie można usunąć lokalnej sesji.")
                }
            } else {
                LogoutResult.NeedsRetry("Serwer nie potwierdził wylogowania. Sesja pozostała aktywna.")
            }
        } catch (e: Exception) {
            LogoutResult.NeedsRetry(e.message ?: "Wylogowanie nie powiodło się. Sesja pozostała aktywna.")
        }
    }

    private fun commitLogoutRefresh(epoch: Long, session: UserSession) {
        synchronized(lock) {
            requireGeneration(epoch)
            sessionStore.save(session)
            currentSession = session
            _authState.value = AuthState.SignedIn(session.user)
        }
    }

    private fun clearLocalSession(message: String? = null) {
        sessionStore.clear()
        currentSession = null
        _sessionIdentity.value = null
        _authState.value = AuthState.SignedOut(message)
    }

    private fun requireGeneration(epoch: Long) {
        if (generation != epoch) throw SessionSignedOutException("Sesja zmieniła się podczas operacji.")
    }

    suspend fun <T> withApi(block: suspend (KajutaBotApi) -> T): T =
        withApiInternal(expectedIdentity = null, block)

    suspend fun <T> withApiForSession(
        expectedIdentity: Long,
        block: suspend (KajutaBotApi) -> T,
    ): T = withApiInternal(expectedIdentity, block)

    private suspend fun <T> withApiInternal(
        expectedIdentity: Long?,
        block: suspend (KajutaBotApi) -> T,
    ): T {
        val entryEpoch = synchronized(lock) {
            if (expectedIdentity != null && _sessionIdentity.value != expectedIdentity) {
                throw SessionSignedOutException("Sesja zmieniła się przed żądaniem.")
            }
            generation
        }
        ensureFreshToken(force = false, failedAccessToken = null, expectedGeneration = entryEpoch)
        val (epoch, identity, fallbackToken) = synchronized(lock) {
            requireGeneration(entryEpoch)
            if (expectedIdentity != null && _sessionIdentity.value != expectedIdentity) {
                throw SessionSignedOutException("Sesja zmieniła się przed żądaniem.")
            }
            Triple(generation, _sessionIdentity.value, currentSession?.accessToken)
        }
        if (identity == null) throw SessionSignedOutException("Brak aktywnej sesji.")
        try {
            val result = block(apiProvider(fallbackToken ?: throw SessionSignedOutException("Brak tokenu dostępu.")))
            requireSession(epoch, identity)
            return result
        } catch (e: HttpException) {
            if (e.code() != 401) throw e
            requireSession(epoch, identity)
            val actualToken = e.response()?.raw()?.request?.header("Authorization")
                ?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
                ?: fallbackToken
            ensureFreshToken(force = true, failedAccessToken = actualToken, expectedGeneration = epoch)
            requireSession(epoch, identity)
            // Only one retry; a second 401 propagates.
            val result = block(apiProvider(tokenForSession(epoch, identity)))
            requireSession(epoch, identity)
            return result
        }
    }

    private fun requireSession(epoch: Long, identity: Long) {
        synchronized(lock) {
            if (generation != epoch || _sessionIdentity.value != identity) {
                throw SessionSignedOutException("Sesja zmieniła się podczas żądania.")
            }
        }
    }

    private fun tokenForSession(epoch: Long, identity: Long): String = synchronized(lock) {
        requireSession(epoch, identity)
        currentSession?.accessToken ?: throw SessionSignedOutException("Brak tokenu dostępu.")
    }

    private suspend fun ensureFreshToken(
        force: Boolean,
        failedAccessToken: String?,
        expectedGeneration: Long? = null,
    ) {
        val flight = synchronized(lock) {
            if (expectedGeneration != null) requireGeneration(expectedGeneration)
            val latest = currentSession ?: try {
                sessionStore.load()?.also {
                    currentSession = it
                    _sessionIdentity.value = generation
                }
            } catch (e: Exception) {
                throw PersistenceSessionException("Nie można odczytać bezpiecznego magazynu sesji.", e)
            } ?: throw SessionSignedOutException("Brak aktywnej sesji.")
            if (!force && !isExpiringSoon(latest)) return
            if (force && failedAccessToken != null && latest.accessToken != failedAccessToken) return
            if (!isValidSession(latest)) {
                generation++
                refreshFlight = null
                clearLocalSession("Sesja wygasła. Zaloguj się ponownie.")
                throw SessionSignedOutException("Sesja wygasła. Zaloguj się ponownie.")
            }
            val tokenKey = if (latest.sessionType == SessionType.GUEST) latest.accessToken else latest.refreshToken!!
            refreshFlight?.takeIf {
                it.generation == generation && it.tokenKey == tokenKey
            } ?: run {
                val epoch = generation
                RefreshFlight(
                    epoch,
                    tokenKey,
                    refreshScope.async { performRefresh(latest, epoch) },
                ).also { refreshFlight = it }
            }
        }
        flight.deferred.await()
        synchronized(lock) { requireGeneration(flight.generation) }
    }

    private suspend fun performRefresh(session: UserSession, epoch: Long): UserSession {
        try {
            val refreshed = if (session.sessionType == SessionType.GUEST) {
                requestGuestRenewal()
            } else {
                requestRefresh(session)
            }
            synchronized(lock) {
                // Logout may adopt this rotated token to revoke it on the server.
                // Other old callers still fail their generation check after await.
                if (generation != epoch) return refreshed
                try {
                    sessionStore.save(refreshed)
                } catch (e: Exception) {
                    _authState.value = AuthState.RecoverableError("Nie można zapisać odnowionej sesji.")
                    throw PersistenceSessionException("Nie można zapisać odnowionej sesji.", e)
                }
                currentSession = refreshed
                _authState.value = AuthState.SignedIn(refreshed.user)
            }
            return refreshed
        } catch (e: HttpException) {
            val code = KajutaBotApiErrors.problemDetailsOf(e)?.errorCode
            if (e.code() == 401 || (session.sessionType == SessionType.GUEST &&
                    (e.code() == 404 || code == "guest_access_disabled")) ||
                KajutaBotApiErrors.isInvalidSessionCode(code)) {
                synchronized(lock) {
                    if (generation == epoch) {
                        generation++
                        refreshFlight = null
                        try {
                            clearLocalSession("Sesja wygasła. Zaloguj się ponownie.")
                        } catch (storage: Exception) {
                            _authState.value = AuthState.RecoverableError("Nie można usunąć wygasłej sesji.")
                            throw PersistenceSessionException("Nie można usunąć wygasłej sesji.", storage)
                        }
                    }
                }
                throw SessionSignedOutException("Sesja wygasła. Zaloguj się ponownie.", e)
            }
            throw TransientSessionException(if (session.sessionType == SessionType.GUEST) guestErrorMessage(e)
                else "Serwer odmówił odnowienia sesji (HTTP " + e.code() + ").", e)
        } catch (e: IOException) {
            throw TransientSessionException("Brak połączenia z serwerem. Spróbuj ponownie.", e)
        } finally {
            synchronized(lock) {
                if (refreshFlight?.generation == epoch &&
                    refreshFlight?.tokenKey == if (session.sessionType == SessionType.GUEST) session.accessToken else session.refreshToken
                ) refreshFlight = null
            }
        }
    }

    private suspend fun requestRefresh(session: UserSession): UserSession = try {
        authApi.refresh(RefreshUserSessionRequest(requireNotNull(session.refreshToken))).toUserSession()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        throw e
    } catch (e: IOException) {
        throw TransientSessionException("Brak połączenia z serwerem. Spróbuj ponownie.", e)
    } catch (e: Exception) {
        throw ContractSessionException("Nieprawidłowa odpowiedź serwera podczas odnowienia sesji.", e)
    }

    private suspend fun requestGuestRenewal(): UserSession = try {
        authApi.guest().toUserSession().also { require(it.sessionType == SessionType.GUEST) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        throw e
    } catch (e: IOException) {
        throw TransientSessionException("Brak połączenia z serwerem. Spróbuj ponownie.", e)
    } catch (e: Exception) {
        throw ContractSessionException("Nieprawidłowa odpowiedź serwera podczas odnowienia sesji.", e)
    }
    private fun isExpiringSoon(session: UserSession): Boolean {
        val expiresAt = parseInstant(session.accessTokenExpiresAtUtc) ?: return true
        // Proactive refresh 30s before expiry.
        return !expiresAt.isAfter(clock().plusSeconds(30))
    }

    private fun isValidSession(session: UserSession): Boolean =
        session.accessToken.isNotBlank() && parseInstant(session.accessTokenExpiresAtUtc) != null &&
            when (session.sessionType) {
                SessionType.GUEST -> session.refreshToken == null && session.refreshTokenExpiresAtUtc == null
                SessionType.DISCORD -> !session.refreshToken.isNullOrBlank() &&
                    parseInstant(session.refreshTokenExpiresAtUtc.orEmpty()) != null
            }

    private fun guestErrorMessage(error: Exception): String = when (error) {
        is IOException -> "Brak połączenia z serwerem. Spróbuj ponownie."
        is HttpException -> when (KajutaBotApiErrors.problemDetailsOf(error)?.errorCode) {
            "guest_access_disabled" -> "Tryb gościa jest obecnie wyłączony."
            "guest_access_unavailable" -> "Serwer demonstracyjny jest chwilowo niedostępny."
            "rate_limit_exceeded" -> "Zbyt wiele prób. Odczekaj chwilę i spróbuj ponownie."
            else -> when (error.code()) {
                404 -> "Tryb gościa jest obecnie wyłączony."
                429 -> "Zbyt wiele prób. Odczekaj chwilę i spróbuj ponownie."
                502, 503, 504 -> "Serwer demonstracyjny jest chwilowo niedostępny."
                else -> "Nie można rozpocząć trybu gościa. Spróbuj ponownie."
            }
        }
        else -> "Nie można rozpocząć trybu gościa. Spróbuj ponownie."
    }

    companion object {
        internal fun parseInstant(raw: String): Instant? {
            return try {
                OffsetDateTime.parse(raw).toInstant()
            } catch (_: Exception) {
                try {
                    Instant.parse(raw)
                } catch (_: Exception) {
                    null
                }
            }
        }

        private fun mapExchangeError(errorCode: String?, httpStatus: Int): String {
            return when (errorCode) {
                "discord_guild_access_denied" ->
                    "Nie masz dostępu do żadnego serwera Discord obsługiwanego przez KajutaBot."
                "guild_access_service_unavailable" ->
                    "Usługa Discord jest chwilowo niedostępna. Spróbuj ponownie."
                "invalid_oauth_request" ->
                    "Nieprawidłowe żądanie logowania. Rozpocznij logowanie ponownie."
                "discord_oauth_failed", "discord_identity_failed" ->
                    "Logowanie przez Discord nie powiodło się. Spróbuj ponownie."
                "rate_limit_exceeded" ->
                    "Zbyt wiele prób. Odczekaj chwilę i spróbuj ponownie."
                else -> when (httpStatus) {
                    429 -> "Zbyt wiele prób. Odczekaj chwilę i spróbuj ponownie."
                    502, 503, 504 -> "Serwer jest chwilowo niedostępny. Spróbuj ponownie."
                    else -> "Logowanie nie powiodło się. Spróbuj ponownie."
                }
            }
        }
    }
}

private fun AuthSessionResponse.toUserSession(): UserSession {
    require(accessToken.isNotBlank())
    require(SessionManager.parseInstant(accessTokenExpiresAtUtc) != null)
    when (sessionType) {
        SessionType.DISCORD -> {
            require(!refreshToken.isNullOrBlank())
            require(SessionManager.parseInstant(refreshTokenExpiresAtUtc.orEmpty()) != null)
        }
        SessionType.GUEST -> require(refreshToken == null && refreshTokenExpiresAtUtc == null)
    }
    return UserSession(
        accessToken = accessToken,
        accessTokenExpiresAtUtc = accessTokenExpiresAtUtc,
        refreshToken = refreshToken,
        refreshTokenExpiresAtUtc = refreshTokenExpiresAtUtc,
        user = user,
        sessionType = sessionType,
    )
}
