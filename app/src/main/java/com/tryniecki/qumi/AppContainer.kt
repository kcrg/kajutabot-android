package com.tryniecki.qumi

import android.content.Context
import com.tryniecki.qumi.api.client.ControlApi
import com.tryniecki.qumi.api.client.ApiClientFactory
import com.tryniecki.qumi.api.client.AuthApi
import com.tryniecki.qumi.auth.AppConfig
import com.tryniecki.qumi.auth.OAuthPendingStorage
import com.tryniecki.qumi.auth.PendingOAuthStore
import com.tryniecki.qumi.auth.PendingOAuthStoreAdapter
import com.tryniecki.qumi.auth.PkceGenerator
import com.tryniecki.qumi.auth.SecureSessionStore
import com.tryniecki.qumi.auth.SessionManager
import com.tryniecki.qumi.auth.SessionStore
import com.tryniecki.qumi.data.preferences.UserPreferencesRepository
import com.tryniecki.qumi.data.repository.FavoritesRepository
import com.tryniecki.qumi.data.repository.PlayerRepository
import com.tryniecki.qumi.data.repository.QueueMutationCoordinator
import com.tryniecki.qumi.data.repository.SessionRepository
import com.tryniecki.qumi.ui.app.SessionViewModelOwner
import com.tryniecki.qumi.ui.app.SessionViewModelScope
import com.tryniecki.qumi.ui.theme.PlatformThemeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Process-lifetime composition root owned by MainApplication.
 * Dependencies are constructed here and ViewModels receive repositories explicitly.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appConfig = AppConfig(
        apiBaseUrl = BuildConfig.API_BASE_URL,
        discordClientId = BuildConfig.DISCORD_CLIENT_ID,
    )

    val sessionStore: SessionStore = SecureSessionStore(appContext)
    val pendingStore: PendingOAuthStore = PendingOAuthStore(appContext)
    val pendingStorage: OAuthPendingStorage = PendingOAuthStoreAdapter(pendingStore)
    val pkceGenerator = PkceGenerator()

    val authApi: AuthApi =
        ApiClientFactory.createAuth(appConfig.apiBaseUrl)

    private val tokenApis = LinkedHashMap<String, ControlApi>(2, 0.75f, true)

    private fun apiForToken(token: String): ControlApi = synchronized(tokenApis) {
        tokenApis[token]?.let { return@synchronized it }
        ApiClientFactory.create(appConfig.apiBaseUrl) { token }.also { api ->
            tokenApis[token] = api
            if (tokenApis.size > 2) tokenApis.remove(tokenApis.keys.first())
        }
    }

    fun clearApiCache() {
        synchronized(tokenApis) { tokenApis.clear() }
        queueMutationCoordinator.clear()
    }

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
    val queueMutationCoordinator = QueueMutationCoordinator()
    val playerRepository = PlayerRepository(sessionManager, appConfig.apiBaseUrl, queueMutationCoordinator)
    val favoritesRepository = FavoritesRepository(sessionManager, queueMutationCoordinator)

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
        appScope.launch {
            preferencesRepository.clearGuildSelection()
            preferencesRepository.clearGuildCache()
        }
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
