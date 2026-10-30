package com.tryniecki.qumi.ui.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import com.tryniecki.qumi.api.client.ControlApi
import com.tryniecki.qumi.api.model.auth.AuthUserResponse
import com.tryniecki.qumi.api.model.discord.DiscordGuildResponse
import com.tryniecki.qumi.api.model.search.SearchResponse
import com.tryniecki.qumi.auth.AppConfig
import com.tryniecki.qumi.auth.FakeAuthApi
import com.tryniecki.qumi.auth.FakePendingStorage
import com.tryniecki.qumi.auth.FakeSessionStore
import com.tryniecki.qumi.auth.SessionManager
import com.tryniecki.qumi.auth.UserSession
import com.tryniecki.qumi.data.preferences.UserPreferencesRepository
import com.tryniecki.qumi.data.repository.PlayerRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerSearchTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val preferences = BlockingPreferencesStore()
    private val calls = mutableListOf<Pair<String, String>>()
    private var response = CompletableDeferred<SearchResponse>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test fun `search starts and completes while history persistence is blocked`() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.setSearchQuery("track")
        model.submitSmartInput()
        runCurrent()
        assertEquals(listOf("track" to "all"), calls)
        assertTrue(preferences.writeStarted)
        response.complete(SearchResponse("track", emptyList()))
        runCurrent()
        assertFalse(model.ui.value.isSearching)
        assertEquals("track", model.ui.value.lastCompletedSearchQuery)

        // Editing the query cancels search work, but must not cancel the submitted history write.
        model.setSearchQuery("next")
        preferences.allowWrites.complete(Unit)
        runCurrent()
        assertEquals(listOf("track"), model.ui.value.searchHistory)
    }

    @Test fun `repeated submit keeps one request but source change starts a new search`() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.setSearchQuery("track")
        model.submitSmartInput()
        runCurrent()
        model.submitSmartInput()
        runCurrent()
        assertEquals(1, calls.size)
        model.setSearchSource(SearchSourceOption.AUDIUS)
        model.submitSmartInput()
        runCurrent()
        assertEquals(listOf("track" to "all", "track" to "audius"), calls)
    }

    private suspend fun model(): PlayerViewModel {
        val unusedApi = Proxy.newProxyInstance(
            ControlApi::class.java.classLoader,
            arrayOf(ControlApi::class.java),
        ) { _, method, _ -> error("Unexpected API call: ${method.name}") } as ControlApi
        val api = object : ControlApi by unusedApi {
            override suspend fun getMyGuilds(): List<DiscordGuildResponse> = emptyList()
            override suspend fun search(query: String, source: String, maxResults: Int): SearchResponse {
                calls += query to source
                return response.await()
            }
        }
        val manager = SessionManager(
            authApi = FakeAuthApi(),
            apiProvider = { api },
            sessionStore = FakeSessionStore(UserSession(
                "token", "2030-01-01T00:00:00Z", "refresh", "2030-02-01T00:00:00Z",
                AuthUserResponse("123", "user", "User"),
            )),
            pendingStorage = FakePendingStorage(),
            appConfig = AppConfig("https://example.test", "client"),
        )
        manager.restore()
        return PlayerViewModel(
            PlayerRepository(manager, "https://example.test"),
            UserPreferencesRepository(preferences),
        ).also { store.put("player", it) }
    }

    private class BlockingPreferencesStore : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        val allowWrites = CompletableDeferred<Unit>()
        var writeStarted = false
        private val mutex = Mutex()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = mutex.withLock {
            writeStarted = true
            allowWrites.await()
            transform(data.value).also { data.value = it }
        }
    }
}
