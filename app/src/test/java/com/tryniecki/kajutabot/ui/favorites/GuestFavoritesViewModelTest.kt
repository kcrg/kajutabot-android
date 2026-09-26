package com.tryniecki.kajutabot.ui.favorites

import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.ui.text.uiText
import com.tryniecki.kajutabot.api.client.KajutaBotApiClientFactory
import com.tryniecki.kajutabot.api.model.auth.AuthUserResponse
import com.tryniecki.kajutabot.api.model.auth.AuthSessionResponse
import com.tryniecki.kajutabot.api.model.auth.SessionType
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.auth.AppConfig
import com.tryniecki.kajutabot.auth.FakeAuthApi
import com.tryniecki.kajutabot.auth.FakePendingStorage
import com.tryniecki.kajutabot.auth.FakeSessionStore
import com.tryniecki.kajutabot.auth.SessionManager
import com.tryniecki.kajutabot.auth.UserSession
import com.tryniecki.kajutabot.data.preferences.UserPreferencesRepository
import com.tryniecki.kajutabot.data.repository.FavoritesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class GuestFavoritesViewModelTest {
    private val favoriteJson = """{"contentUrl":"https://example.com/track","title":"Demo","addedAt":"2026-09-20T00:00:00Z"}"""
    private val track = PlaybackTrackResponse(
        "demo", "YouTube", "Demo", "https://example.com/track", 60_000,
        null, 0,
    )

    @Test
    fun `guest loads toggles and queues favorites through normal user endpoints`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val server = MockWebServer()
        server.start()
        try {
            val baseUrl = server.url("/").toString()
            val stored = FakeSessionStore(UserSession(
                "guest-jwt", "2030-01-01T00:00:00Z", null, null,
                AuthUserResponse("", "guest", "Gość"), SessionType.GUEST,
            ))
            val manager = SessionManager(
                authApi = FakeAuthApi(),
                apiProvider = { token -> KajutaBotApiClientFactory.create(baseUrl) { token } },
                sessionStore = stored,
                pendingStorage = FakePendingStorage(),
                appConfig = AppConfig(baseUrl, "test-client"),
            )
            manager.restore()
            val preferences = UserPreferencesRepository(RuntimeEnvironment.getApplication())
            preferences.setGuildSelection("demo-guild", "voice")
            preferences.setFavoritesShuffle("guest", false)
            server.enqueue(MockResponse().setBody("[]"))
            val viewModel = FavoritesViewModel(
                repository = FavoritesRepository(manager),
                preferencesRepository = preferences,
            )
            withTimeout(5_000) { viewModel.ui.first { !it.isLoading } }
            assertEquals("/api/v1/app/users/me/favorites", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("[]"))
            viewModel.refresh()
            withTimeout(5_000) { viewModel.ui.first { !it.isLoading } }
            assertEquals("/api/v1/app/users/me/favorites", server.takeRequest().path)
            viewModel.setShuffle(true)
            assertTrue(withTimeout(5_000) { preferences.favoritesShuffle("guest").first { it } })

            server.enqueue(MockResponse().setBody(favoriteJson))
            viewModel.toggle(track)
            withTimeout(5_000) { viewModel.ui.first { it.favorites.size == 1 && !it.isMutating } }
            val add = server.takeRequest()
            assertEquals("POST", add.method)
            assertEquals("/api/v1/app/users/me/favorites", add.path)
            assertEquals("Bearer guest-jwt", add.getHeader("Authorization"))
            assertTrue(viewModel.isFavorite(track))

            server.enqueue(MockResponse().setBody(queueResponseJson()))
            viewModel.queueAll()
            withTimeout(5_000) { viewModel.ui.first { it.info == uiText(R.string.favorites_queued_all) } }
            val queue = server.takeRequest()
            assertEquals("/api/v1/app/users/me/favorites/queue", queue.path)
            assertTrue(queue.body.readUtf8().contains("\"shuffle\":true"))

            server.enqueue(MockResponse().setResponseCode(204))
            viewModel.toggle(track)
            withTimeout(5_000) { viewModel.ui.first { it.favorites.isEmpty() && !it.isMutating } }
            val delete = server.takeRequest()
            assertEquals("DELETE", delete.method)
            assertTrue(delete.path!!.startsWith("/api/v1/app/users/me/favorites?"))
            assertFalse(viewModel.isFavorite(track))
        } finally {
            server.shutdown()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `guest JWT renewal preserves Favorites ViewModel and shuffle owner`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val server = MockWebServer()
        server.start()
        try {
            val baseUrl = server.url("/").toString()
            var now = Instant.parse("2026-09-20T00:00:00Z")
            val user = AuthUserResponse("", "guest", "Gość")
            val stored = FakeSessionStore(UserSession(
                "old-jwt", "2026-09-20T01:00:00Z", null, null, user, SessionType.GUEST,
            ))
            val auth = FakeAuthApi(guestHandler = {
                AuthSessionResponse("new-jwt", "2030-01-01T00:00:00Z", null, null, user, SessionType.GUEST)
            })
            val manager = SessionManager(
                authApi = auth,
                apiProvider = { token -> KajutaBotApiClientFactory.create(baseUrl) { token } },
                sessionStore = stored,
                pendingStorage = FakePendingStorage(),
                appConfig = AppConfig(baseUrl, "test-client"),
                clock = { now },
            )
            manager.restore()
            val identity = checkNotNull(manager.sessionIdentity.value)
            val preferences = UserPreferencesRepository(RuntimeEnvironment.getApplication())
            preferences.setGuildSelection("demo-guild", "voice")
            preferences.setFavoritesShuffle("guest", false)
            server.enqueue(MockResponse().setBody("[]"))
            val viewModel = FavoritesViewModel(
                repository = FavoritesRepository(manager),
                preferencesRepository = preferences,
            )
            withTimeout(5_000) { viewModel.ui.first { !it.isLoading } }
            server.takeRequest()
            viewModel.setShuffle(true)

            now = Instant.parse("2026-09-20T00:59:45Z")
            assertEquals("new-jwt", manager.accessTokenForSession(identity))
            assertEquals(identity, manager.sessionIdentity.value)
            assertEquals(1, auth.guestCount.get())
            assertTrue(withTimeout(5_000) { preferences.favoritesShuffle("guest").first { it } })
            assertTrue(viewModel.ui.value.shuffle)
            assertEquals("new-jwt", stored.session?.accessToken)
        } finally {
            server.shutdown()
            Dispatchers.resetMain()
        }
    }

    private fun queueResponseJson() = """{"guildId":"demo-guild","voiceChannelId":"voice","nowPlaying":null,"nowPlayingFromRadio":false,"radio":{"isEnabled":false},"pendingEntries":[],"pendingDurationMilliseconds":0,"version":1}"""
}
