package com.tryniecki.kajutabot

import android.content.Context
import com.tryniecki.kajutabot.api.client.KajutaBotApi
import com.tryniecki.kajutabot.api.client.KajutaBotApiClientFactory
import com.tryniecki.kajutabot.api.client.KajutaBotAuthApi
import com.tryniecki.kajutabot.auth.AppConfig
import com.tryniecki.kajutabot.auth.OAuthPendingStorage
import com.tryniecki.kajutabot.auth.PendingOAuthStore
import com.tryniecki.kajutabot.auth.PendingOAuthStoreAdapter
import com.tryniecki.kajutabot.auth.PkceGenerator
import com.tryniecki.kajutabot.auth.SecureSessionStore
import com.tryniecki.kajutabot.auth.SessionManager
import com.tryniecki.kajutabot.auth.SessionStore
import com.tryniecki.kajutabot.data.preferences.UserPreferencesRepository
import com.tryniecki.kajutabot.data.repository.FavoritesRepository
import com.tryniecki.kajutabot.data.repository.PlayerRepository
import com.tryniecki.kajutabot.data.repository.SessionRepository
import com.tryniecki.kajutabot.ui.app.SessionViewModelOwner
import com.tryniecki.kajutabot.ui.app.SessionViewModelScope
import com.tryniecki.kajutabot.ui.theme.PlatformThemeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-lifetime composition root owned by KajutaBotApplication.
 * Dependencies are constructed here and ViewModels receive repositories explicitly.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appConfig = AppConfig(
        apiBaseUrl = BuildConfig.KAJUTABOT_API_BASE_URL,
        discordClientId = BuildConfig.KAJUTABOT_DISCORD_CLIENT_ID,
    )

    val sessionStore: SessionStore = SecureSessionStore(appContext)
    val pendingStore: PendingOAuthStore = PendingOAuthStore(appContext)
    val pendingStorage: OAuthPendingStorage = PendingOAuthStoreAdapter(pendingStore)
    val pkceGenerator = PkceGenerator()

    val authApi: KajutaBotAuthApi =
        KajutaBotApiClientFactory.createAuth(appConfig.apiBaseUrl)

    private val tokenApis = LinkedHashMap<String, KajutaBotApi>(2, 0.75f, true)

    private fun apiForToken(token: String): KajutaBotApi = synchronized(tokenApis) {
        tokenApis[token]?.let { return@synchronized it }
        KajutaBotApiClientFactory.create(appConfig.apiBaseUrl) { token }.also { api ->
            tokenApis[token] = api
            if (tokenApis.size > 2) tokenApis.remove(tokenApis.keys.first())
        }
    }

    fun clearApiCache() = synchronized(tokenApis) { tokenApis.clear() }

    val sessionManager = SessionManager(
        authApi = authApi,
        apiProvider = ::apiForToken,
        sessionStore = sessionStore,
        pendingStorage = pendingStorage,
        appConfig = appConfig,
        pkceGenerator = pkceGenerator,
    )

    val preferencesRepository = UserPreferencesRepository(appContext)
    val platformThemeController = PlatformThemeController(appContext)
    val sessionRepository = SessionRepository(sessionManager)
    val playerRepository = PlayerRepository(sessionManager, appConfig.apiBaseUrl)
    val favoritesRepository = FavoritesRepository(sessionManager)

    private val _mediaServiceActive = MutableStateFlow(false)
    val mediaServiceActive = _mediaServiceActive.asStateFlow()
    fun setMediaServiceActive(active: Boolean) {
        _mediaServiceActive.value = active
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // The authenticated ViewModel store is shared by Activity and MediaSessionService.
    // It survives Activity destruction, but is cleared as soon as the user session ends.
    private val sessionScope = SessionViewModelScope {
        clearApiCache()
        appScope.launch { preferencesRepository.clearGuildSelection() }
    }

    init {
        appScope.launch {
            sessionRepository.sessionIdentity.collect { identity ->
                if (identity == null) sessionScope.end() else sessionScope.ownerFor(identity)
            }
        }
    }

    fun ownerForSession(identity: Long): SessionViewModelOwner = sessionScope.ownerFor(identity)
}
