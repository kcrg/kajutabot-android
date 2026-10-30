package com.tryniecki.kajutabot.ui.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import com.tryniecki.kajutabot.api.client.KajutaBotApi
import com.tryniecki.kajutabot.api.model.auth.AuthUserResponse
import com.tryniecki.kajutabot.api.model.auth.SessionType
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.auth.AppConfig
import com.tryniecki.kajutabot.auth.FakeAuthApi
import com.tryniecki.kajutabot.auth.FakePendingStorage
import com.tryniecki.kajutabot.auth.FakeSessionStore
import com.tryniecki.kajutabot.auth.SessionManager
import com.tryniecki.kajutabot.auth.UserSession
import com.tryniecki.kajutabot.auth.httpError
import com.tryniecki.kajutabot.data.preferences.UserPreferencesRepository
import com.tryniecki.kajutabot.data.repository.PlayerRepository
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CachedGuildAccessTest {
    private val dispatcher = StandardTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()
    private val data = object : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
            transform(data.value).also { data.value = it }
    }
    private val preferences = UserPreferencesRepository(data)
    private var guildsResponse = CompletableDeferred<List<DiscordGuildResponse>>()
    private var guildCalls = 0
    private var channelCalls = 0

    private val guildA = DiscordGuildResponse("guild-a", "A", null, true)
    private val guildB = DiscordGuildResponse("guild-b", "B", null, true)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
    }

    @Test fun `saved list opens content before the slow request returns`() = runTest(dispatcher) {
        preferences.saveGuildCache("user-1", listOf(guildA))
        preferences.setGuildSelection("guild-a", "voice")

        val model = model()
        runCurrent()

        assertEquals(1, guildCalls)
        assertEquals(GuildAccessState.AVAILABLE, model.ui.value.guildAccessState)
        assertEquals(listOf(guildA), model.ui.value.guilds)
        assertEquals("guild-a", model.ui.value.selectedGuildId)
        assertEquals(1, channelCalls)
        assertEquals(QueueLoadState.LOADING, model.ui.value.queueLoadState)
    }

    @Test fun `no saved list keeps the checking screen and then saves the result`() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        assertEquals(GuildAccessState.CHECKING, model.ui.value.guildAccessState)

        guildsResponse.complete(listOf(guildA))
        runCurrent()

        assertEquals(GuildAccessState.AVAILABLE, model.ui.value.guildAccessState)
        assertEquals(listOf(guildA), preferences.cachedGuilds("user-1"))
    }

    @Test fun `no saved list and a transient failure shows the access error`() = runTest(dispatcher) {
        val model = model()
        runCurrent()

        guildsResponse.completeExceptionally(IOException("offline"))
        runCurrent()

        assertEquals(GuildAccessState.ERROR, model.ui.value.guildAccessState)
    }

    @Test fun `successful background refresh updates list without reloading the selection`() = runTest(dispatcher) {
        preferences.saveGuildCache("user-1", listOf(guildA))
        preferences.setGuildSelection("guild-a", "voice")
        val model = model()
        runCurrent()

        guildsResponse.complete(listOf(guildA, guildB))
        runCurrent()

        assertEquals(GuildAccessState.AVAILABLE, model.ui.value.guildAccessState)
        assertEquals(listOf(guildA, guildB), model.ui.value.guilds)
        assertEquals(listOf(guildA, guildB), preferences.cachedGuilds("user-1"))
        assertEquals("guild-a", model.ui.value.selectedGuildId)
        assertEquals(1, channelCalls)
    }

    @Test fun `background refresh clears a selected guild that is gone`() = runTest(dispatcher) {
        preferences.saveGuildCache("user-1", listOf(guildA, guildB))
        preferences.setGuildSelection("guild-a", "voice")
        val model = model()
        runCurrent()

        guildsResponse.complete(listOf(guildB))
        runCurrent()

        assertEquals("guild-b", model.ui.value.selectedGuildId)
        assertEquals("guild-b", preferences.currentGuildSelection().guildId)
    }

    @Test fun `empty list in the background revokes access and clears the saved list`() = runTest(dispatcher) {
        preferences.saveGuildCache("user-1", listOf(guildA))
        val model = model()
        runCurrent()

        guildsResponse.complete(emptyList())
        runCurrent()

        assertEquals(GuildAccessState.NONE, model.ui.value.guildAccessState)
        assertNull(preferences.cachedGuilds("user-1"))
    }

    @Test fun `denied access in the background revokes access and clears the saved list`() = runTest(dispatcher) {
        preferences.saveGuildCache("user-1", listOf(guildA))
        val model = model()
        runCurrent()

        guildsResponse.completeExceptionally(httpError(403, "discord_guild_access_denied"))
        runCurrent()

        assertEquals(GuildAccessState.NONE, model.ui.value.guildAccessState)
        assertEquals(emptyList<DiscordGuildResponse>(), model.ui.value.guilds)
        assertNull(preferences.cachedGuilds("user-1"))
    }

    @Test fun `transient background failure keeps content and retries on return`() = runTest(dispatcher) {
        preferences.saveGuildCache("user-1", listOf(guildA))
        val model = model()
        runCurrent()

        guildsResponse.completeExceptionally(httpError(503, "guild_access_service_unavailable"))
        runCurrent()

        assertEquals(GuildAccessState.AVAILABLE, model.ui.value.guildAccessState)
        assertNull(model.ui.value.guildAccessError)
        assertEquals(listOf(guildA), preferences.cachedGuilds("user-1"))

        guildsResponse = CompletableDeferred()
        model.setRealtimeOwner(RealtimeOwner.UI, true)
        runCurrent()
        assertEquals(2, guildCalls)
        assertEquals(GuildAccessState.AVAILABLE, model.ui.value.guildAccessState)
        model.setRealtimeOwner(RealtimeOwner.UI, false)
    }

    @Test fun `another account never sees the saved list and it is removed`() = runTest(dispatcher) {
        preferences.saveGuildCache("user-1", listOf(guildA))

        val model = model(userId = "user-2")
        runCurrent()

        assertEquals(GuildAccessState.CHECKING, model.ui.value.guildAccessState)
        assertEquals(emptyList<DiscordGuildResponse>(), model.ui.value.guilds)
        assertNull(preferences.cachedGuilds("user-1"))
    }

    @Test fun `guest session neither reads nor writes the saved list`() = runTest(dispatcher) {
        preferences.saveGuildCache("guest-id", listOf(guildA))

        val model = model(userId = "guest-id", sessionType = SessionType.GUEST)
        runCurrent()
        assertEquals(GuildAccessState.CHECKING, model.ui.value.guildAccessState)

        guildsResponse.complete(listOf(guildB))
        runCurrent()

        assertEquals(GuildAccessState.AVAILABLE, model.ui.value.guildAccessState)
        assertEquals(listOf(guildA), preferences.cachedGuilds("guest-id"))
    }

    @Test fun `unreadable saved list falls back to the checking screen`() = runTest(dispatcher) {
        preferences.saveGuildCache("user-1", listOf(guildA))
        data.data.value = data.data.value.toMutablePreferences().apply {
            this[androidx.datastore.preferences.core.stringPreferencesKey("guild_cache")] = "not json"
        }

        val model = model()
        runCurrent()

        assertEquals(GuildAccessState.CHECKING, model.ui.value.guildAccessState)
    }


    private suspend fun model(
        userId: String = "user-1",
        sessionType: SessionType = SessionType.DISCORD,
    ): PlayerViewModel {
        val unusedApi = Proxy.newProxyInstance(
            KajutaBotApi::class.java.classLoader,
            arrayOf(KajutaBotApi::class.java),
        ) { _, method, _ -> error("Unexpected API call: ${method.name}") } as KajutaBotApi
        val api = object : KajutaBotApi by unusedApi {
            override suspend fun getMyGuilds(): List<DiscordGuildResponse> {
                guildCalls++
                return guildsResponse.await()
            }
            override suspend fun getVoiceChannels(guildId: String): List<DiscordVoiceChannelResponse> {
                channelCalls++
                return listOf(DiscordVoiceChannelResponse("voice", "Voice", 0, 0, false))
            }
        }
        val manager = SessionManager(
            authApi = FakeAuthApi(),
            apiProvider = { api },
            sessionStore = FakeSessionStore(UserSession(
                "token", "2030-01-01T00:00:00Z",
                "refresh".takeIf { sessionType == SessionType.DISCORD },
                "2030-02-01T00:00:00Z".takeIf { sessionType == SessionType.DISCORD },
                AuthUserResponse(userId, "user", "User"), sessionType,
            )),
            pendingStorage = FakePendingStorage(),
            appConfig = AppConfig("https://example.test", "client"),
        )
        manager.restore()
        val store = ViewModelStore().also(stores::add)
        return PlayerViewModel(PlayerRepository(manager, "https://example.test"), preferences)
            .also { store.put("player", it) }
    }
}
