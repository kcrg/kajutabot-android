package com.tryniecki.kajutabot.auth

import com.tryniecki.kajutabot.api.client.KajutaBotApi
import com.tryniecki.kajutabot.api.client.KajutaBotApiErrors
import com.tryniecki.kajutabot.api.client.KajutaBotAuthApi
import com.tryniecki.kajutabot.api.model.auth.AuthSessionResponse
import com.tryniecki.kajutabot.api.model.auth.DiscordOAuthExchangeRequest
import com.tryniecki.kajutabot.api.model.auth.RefreshUserSessionRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime

class SessionSignedOutException(message: String, cause: Throwable? = null) : Exception(message, cause)

class TransientSessionException(message: String, cause: Throwable? = null) : Exception(message, cause)

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
    data class NeedsRetry(val message: String) : LogoutResult
}

class SessionManager(
    private val authApi: KajutaBotAuthApi,
    private val apiProvider: () -> KajutaBotApi,
    private val sessionStore: SessionStore,
    private val pendingStorage: OAuthPendingStorage,
    private val appConfig: AppConfig,
    private val pkceGenerator: PkceGenerator = PkceGenerator(),
    private val clock: () -> Instant = Instant::now,
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Restoring)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val refreshMutex = Mutex()

    @Volatile
    private var currentSession: UserSession? = null

    fun currentAccessToken(): String? = currentSession?.accessToken

    fun currentUserSession(): UserSession? = currentSession

    suspend fun restore() {
        _authState.value = AuthState.Restoring
        val stored = try {
            sessionStore.load()
        } catch (_: Exception) {
            null
        }
        if (stored == null) {
            currentSession = null
            _authState.value = AuthState.SignedOut()
            return
        }
        currentSession = stored
        if (!isExpiringSoon(stored)) {
            _authState.value = AuthState.SignedIn(stored.user)
            return
        }
        try {
            refreshLocked(force = false, failedAccessToken = null)
        } catch (_: SessionSignedOutException) {
            // state already set to SignedOut inside refreshLocked
        } catch (e: TransientSessionException) {
            _authState.value = AuthState.RecoverableError(
                e.message ?: "Brak połączenia. Spróbuj ponownie.",
            )
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
        pendingStorage.save(pkce.state, pkce.verifier)
        val url = DiscordOAuth.buildAuthorizationUrl(
            clientId = appConfig.discordClientId,
            redirectUri = appConfig.redirectUri,
            codeChallenge = pkce.challenge,
            state = pkce.state,
        )
        return OAuthStartResult.Ready(url)
    }

    suspend fun handleOAuthCallback(
        code: String?,
        returnedState: String?,
        error: String?,
    ): OAuthCallbackResult {
        if (error != null) {
            pendingStorage.clear()
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

        val pending = pendingStorage.loadValid()
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

        return try {
            val response = authApi.exchange(
                DiscordOAuthExchangeRequest(
                    code = code,
                    codeVerifier = pending.codeVerifier,
                    redirectUri = appConfig.redirectUri,
                ),
            )
            val session = response.toUserSession()
            currentSession = session
            sessionStore.save(session)
            pendingStorage.clear()
            _authState.value = AuthState.SignedIn(session.user)
            OAuthCallbackResult.Exchanged
        } catch (e: HttpException) {
            pendingStorage.clear()
            val problem = KajutaBotApiErrors.problemDetailsOf(e)
            val message = mapExchangeError(problem?.errorCode, e.code())
            if (_authState.value !is AuthState.SignedIn) {
                _authState.value = AuthState.SignedOut(message)
            }
            OAuthCallbackResult.Failed(message)
        } catch (_: IOException) {
            pendingStorage.clear()
            val message = "Brak połączenia z serwerem. Spróbuj ponownie."
            if (_authState.value !is AuthState.SignedIn) {
                _authState.value = AuthState.SignedOut(message)
            }
            OAuthCallbackResult.Failed(message)
        } catch (_: Exception) {
            pendingStorage.clear()
            val message = "Logowanie nie powiodło się. Spróbuj ponownie."
            if (_authState.value !is AuthState.SignedIn) {
                _authState.value = AuthState.SignedOut(message)
            }
            OAuthCallbackResult.Failed(message)
        }
    }

    suspend fun logout(): LogoutResult {
        val session = currentSession
        if (session == null) {
            sessionStore.clear()
            _authState.value = AuthState.SignedOut()
            return LogoutResult.SignedOut
        }
        return try {
            apiProvider().logout()
            currentSession = null
            sessionStore.clear()
            _authState.value = AuthState.SignedOut()
            LogoutResult.SignedOut
        } catch (e: HttpException) {
            val problem = KajutaBotApiErrors.problemDetailsOf(e)
            val code = problem?.errorCode
            if (e.code() == 401 || e.code() == 404 || KajutaBotApiErrors.isInvalidSessionCode(code)) {
                currentSession = null
                sessionStore.clear()
                _authState.value = AuthState.SignedOut()
                LogoutResult.SignedOut
            } else {
                LogoutResult.NeedsRetry("Brak połączenia z serwerem. Sesja pozostała aktywna, spróbuj ponownie.")
            }
        } catch (_: IOException) {
            LogoutResult.NeedsRetry("Brak połączenia z serwerem. Sesja pozostała aktywna, spróbuj ponownie.")
        } catch (_: Exception) {
            LogoutResult.NeedsRetry("Wylogowanie nie powiodło się. Spróbuj ponownie.")
        }
    }

    suspend fun <T> withApi(block: suspend (KajutaBotApi) -> T): T {
        ensureFreshToken(force = false, failedAccessToken = null)
        val api = apiProvider()
        try {
            return block(api)
        } catch (e: HttpException) {
            if (e.code() != 401) throw e
            val failedToken = currentSession?.accessToken
            try {
                ensureFreshToken(force = true, failedAccessToken = failedToken)
            } catch (signedOut: SessionSignedOutException) {
                throw signedOut
            } catch (transient: TransientSessionException) {
                throw transient
            }
            // Single retry, no loop.
            return block(apiProvider())
        }
    }

    private suspend fun ensureFreshToken(force: Boolean, failedAccessToken: String?) {
        val session = currentSession
            ?: sessionStore.load()?.also { currentSession = it }
            ?: throw SessionSignedOutException("Brak aktywnej sesji.")
        if (!force && !isExpiringSoon(session)) return
        refreshLocked(force = force, failedAccessToken = failedAccessToken)
    }

    private suspend fun refreshLocked(force: Boolean, failedAccessToken: String?) {
        refreshMutex.withLock {
            val latest = currentSession
                ?: sessionStore.load()?.also { currentSession = it }
                ?: throw SessionSignedOutException("Brak aktywnej sesji.")
            if (!force) {
                if (!isExpiringSoon(latest)) return
            } else {
                if (failedAccessToken != null && latest.accessToken != failedAccessToken) {
                    // Another waiter already refreshed.
                    return
                }
            }
            val refreshToken = latest.refreshToken
            try {
                val response = authApi.refresh(RefreshUserSessionRequest(refreshToken))
                val newSession = response.toUserSession()
                currentSession = newSession
                sessionStore.save(newSession)
                _authState.value = AuthState.SignedIn(newSession.user)
            } catch (e: HttpException) {
                val problem = try {
                    // problemDetailsOf consumes errorBody once; do it here only.
                    KajutaBotApiErrors.problemDetailsOf(e)
                } catch (_: Exception) {
                    null
                }
                // Note: problemDetailsOf above already consumed the body for this instance,
                // so reuse `problem` instead of calling errorCodeOf again.
                val code = problem?.errorCode
                if (KajutaBotApiErrors.isInvalidSessionCode(code) || e.code() == 401) {
                    currentSession = null
                    try {
                        sessionStore.clear()
                    } catch (_: Exception) {
                    }
                    _authState.value = AuthState.SignedOut(null)
                    throw SessionSignedOutException("Sesja wygasła. Zaloguj się ponownie.", e)
                }
                throw TransientSessionException("Brak połączenia z serwerem. Spróbuj ponownie.", e)
            } catch (_: IOException) {
                throw TransientSessionException("Brak połączenia z serwerem. Spróbuj ponownie.")
            }
        }
    }

    private fun isExpiringSoon(session: UserSession): Boolean {
        val expiresAt = parseInstant(session.accessTokenExpiresAtUtc) ?: return false
        // Proactive refresh 30s before expiry.
        return !expiresAt.isAfter(clock().plusSeconds(30))
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

private fun AuthSessionResponse.toUserSession(): UserSession =
    UserSession(
        accessToken = accessToken,
        accessTokenExpiresAtUtc = accessTokenExpiresAtUtc,
        refreshToken = refreshToken,
        refreshTokenExpiresAtUtc = refreshTokenExpiresAtUtc,
        user = user,
    )
