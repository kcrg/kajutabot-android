package com.tryniecki.kajutabot.appfunctions

import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionDeniedException
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import com.tryniecki.kajutabot.api.client.KajutaBotApiClientFactory
import com.tryniecki.kajutabot.api.client.KajutaBotRealtimeClient
import com.tryniecki.kajutabot.api.client.RealtimeDisconnect
import com.tryniecki.kajutabot.api.model.auth.AuthUserResponse
import com.tryniecki.kajutabot.api.model.playback.LocalVolumeStateDto
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.auth.AppConfig
import com.tryniecki.kajutabot.auth.FakeAuthApi
import com.tryniecki.kajutabot.auth.FakePendingStorage
import com.tryniecki.kajutabot.auth.FakeSessionStore
import com.tryniecki.kajutabot.auth.SessionManager
import com.tryniecki.kajutabot.auth.UserSession
import com.tryniecki.kajutabot.data.preferences.GuildSelection
import com.tryniecki.kajutabot.data.repository.FavoritesRepository
import com.tryniecki.kajutabot.data.repository.PlayerRepository
import com.tryniecki.kajutabot.data.repository.QueueMutationCoordinator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AppFunctionException needs a real android.os.Bundle.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppFunctionCommandsTest {
    private val server = MockWebServer()
    private val requests = mutableListOf<String>()
    private val volume = FakeVolumeClient()
    private var pending = listOf("q1", "q2")
    private var nowPlaying: String? = "current"
    private var repeatRestarts = false
    private var searchHasResults = true
    private var failOn: String? = null
    private var selection = GuildSelection("guild", "voice")

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = handle(request)
        }
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    private fun track(id: String) = """{"contentId":"$id","contentType":"youtube","title":"Title $id",""" +
        """"url":"https://example.test/$id","durationMilliseconds":90000,"artworkUrl":null,"playCount":0}"""

    private fun snapshot(added: String? = null, skipOutcome: String? = null): String {
        val entries = pending.mapIndexed { index, id ->
            """{"entryId":"entry-$id","position":${index + 1},"track":${track(id)}}"""
        }.joinToString(",")
        val current = nowPlaying?.let(::track) ?: "null"
        val addedTracks = added?.let { ""","addedTracks":[${track(it)}]""" }.orEmpty()
        val outcome = skipOutcome?.let { ""","skipOutcome":"$it"""" }.orEmpty()
        return """{"guildId":"guild","voiceChannelId":"voice","nowPlaying":$current,"nowPlayingFromRadio":false,""" +
            """"radio":{"isEnabled":false},"pendingEntries":[$entries],"pendingEntriesCount":${pending.size},""" +
            """"pendingDurationMilliseconds":0,"version":1,"queueVersion":1$addedTracks$outcome}"""
    }

    private fun handle(request: RecordedRequest): MockResponse {
        val path = request.path.orEmpty().substringAfter("/api/v1/app/").substringBefore('?')
        val call = "${request.method} $path"
        synchronized(requests) { requests += call }
        if (failOn != null && call.startsWith(failOn!!)) return MockResponse().setResponseCode(500)
        val body = when (call) {
            "GET search" -> if (searchHasResults) {
                """{"query":"q","items":[{"input":"https://example.test/new","track":${searchTrack()},""" +
                    """"metricCount":0,"metricCaption":""}]}"""
            } else {
                """{"query":"q","items":[]}"""
            }
            "POST guilds/guild/queue/items" -> {
                if (nowPlaying == null) {
                    nowPlaying = "new"
                    snapshot(added = "new")
                } else {
                    pending = pending + "new"
                    snapshot(added = "new")
                }
            }
            "PUT guilds/guild/queue/items/entry-new/position" -> {
                pending = listOf("new") + (pending - "new")
                snapshot()
            }
            "POST guilds/guild/queue/skip" -> if (repeatRestarts) {
                snapshot(skipOutcome = "RestartedRepeatedTrack")
            } else {
                nowPlaying = pending.firstOrNull()
                pending = pending.drop(1)
                snapshot(skipOutcome = "Advanced")
            }
            "POST users/me/favorites" ->
                """{"contentUrl":"https://example.test/current","title":"Title current","addedAt":"2026-01-01T00:00:00Z"}"""
            else -> snapshot()
        }
        return MockResponse().setBody(body)
    }

    private fun searchTrack() = """{"contentId":"new","contentType":"youtube","title":"Title new",""" +
        """"url":"https://example.test/new","durationMilliseconds":90000,"artworkUrl":null}"""

    private suspend fun commands(signedIn: Boolean = true): AppFunctionCommands {
        val base = server.url("/").toString()
        val session = UserSession(
            "access-1", "2030-01-01T00:00:00Z", "refresh-1", "2030-02-01T00:00:00Z",
            AuthUserResponse("123", "user", "User"),
        )
        val manager = SessionManager(
            FakeAuthApi(), { token -> KajutaBotApiClientFactory.create(base) { token } },
            FakeSessionStore(session.takeIf { signedIn }), FakePendingStorage(), AppConfig(base, "client"),
        )
        val coordinator = QueueMutationCoordinator()
        return AppFunctionCommands(
            player = PlayerRepository(manager, base, coordinator),
            favorites = FavoritesRepository(manager, coordinator),
            guildSelection = { selection },
            restoreSession = manager::restore,
            createRealtimeClient = { volume },
        )
    }

    private fun mutations() = synchronized(requests) { requests.filterNot { it.startsWith("GET") } }

    @Test fun `play now adds moves to the front and skips while keeping the queue`() = runBlocking<Unit> {
        val played = commands().queue.playTrackNow("  new song ")

        assertEquals("Title new", played.title)
        assertTrue(played.isPlayingNext)
        assertEquals(
            listOf(
                "POST guilds/guild/queue/items",
                "PUT guilds/guild/queue/items/entry-new/position",
                "POST guilds/guild/queue/skip",
            ),
            mutations(),
        )
        assertEquals("new", nowPlaying)
        assertEquals(listOf("q1", "q2"), pending)
    }

    @Test fun `play now does not skip a track that started on an idle bot`() = runBlocking<Unit> {
        nowPlaying = null
        pending = emptyList()

        val played = commands().queue.playTrackNow("new song")

        assertTrue(played.isPlayingNext)
        assertEquals(listOf("POST guilds/guild/queue/items"), mutations())
    }

    @Test fun `play now does not move a track that is already first`() = runBlocking<Unit> {
        pending = emptyList()

        commands().queue.playTrackNow("new song")

        assertEquals(listOf("POST guilds/guild/queue/items", "POST guilds/guild/queue/skip"), mutations())
    }

    @Test fun `play now reports when repeat restarted the current track`() = runBlocking<Unit> {
        repeatRestarts = true

        assertFalse(commands().queue.playTrackNow("new song").isPlayingNext)
    }

    @Test fun `play now says the track was added when skipping fails`() = runBlocking<Unit> {
        failOn = "POST guilds/guild/queue/skip"
        val commands = commands()

        val error = assertThrows(AppFunctionAppUnknownException::class.java) {
            runBlocking { commands.queue.playTrackNow("new song") }
        }

        assertTrue(error.errorMessage.orEmpty().contains("was added to the queue"))
    }

    @Test fun `add to queue reports the place in the queue`() = runBlocking<Unit> {
        val queued = commands().queue.addTrackToQueue("new song")

        assertEquals(QueuedTrack("Title new", 3), queued)
        assertEquals(listOf("POST guilds/guild/queue/items"), mutations())
    }

    @Test fun `add to queue has no place when the track started playing`() = runBlocking<Unit> {
        nowPlaying = null
        pending = emptyList()

        assertNull(commands().queue.addTrackToQueue("new song").queuePosition)
    }

    @Test fun `no search result never touches the queue`() = runBlocking<Unit> {
        searchHasResults = false
        val commands = commands()

        assertThrows(AppFunctionElementNotFoundException::class.java) {
            runBlocking { commands.queue.addTrackToQueue("nothing") }
        }
        assertEquals(emptyList<String>(), mutations())
    }

    @Test fun `blank title is rejected without calling the service`() = runBlocking<Unit> {
        val commands = commands()

        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking { commands.queue.playTrackNow("   ") }
        }
        assertEquals(emptyList<String>(), synchronized(requests) { requests.toList() })
    }

    @Test fun `skip without a current track does not call skip`() = runBlocking<Unit> {
        nowPlaying = null
        val commands = commands()

        assertThrows(AppFunctionElementNotFoundException::class.java) {
            runBlocking { commands.queue.skipCurrentTrack() }
        }
        assertEquals(emptyList<String>(), mutations())
    }

    @Test fun `skip returns the skipped track`() = runBlocking<Unit> {
        assertEquals(SkippedTrack("Title current", false), commands().queue.skipCurrentTrack())
    }

    @Test fun `current track is added to favorites`() = runBlocking<Unit> {
        assertEquals(FavoritedTrack("Title current"), commands().favorites.addCurrentTrackToFavorites())
        assertEquals(listOf("POST users/me/favorites"), mutations())
    }

    @Test fun `missing server selection is reported`() = runBlocking<Unit> {
        selection = GuildSelection()
        val commands = commands()

        assertThrows(AppFunctionElementNotFoundException::class.java) {
            runBlocking { commands.settings.getPlaybackStatus() }
        }
    }

    @Test fun `signed out user is denied`() = runBlocking<Unit> {
        val commands = commands(signedIn = false)

        assertThrows(AppFunctionDeniedException::class.java) {
            runBlocking { commands.queue.skipCurrentTrack() }
        }
    }

    @Test fun `cold process restores the session before acting`() = runBlocking<Unit> {
        assertEquals("Title current", commands().settings.getPlaybackStatus().currentTrackTitle)
    }

    @Test fun `status lists at most five next tracks`() = runBlocking<Unit> {
        pending = (1..8).map { "q$it" }

        val status = commands().settings.getPlaybackStatus()

        assertEquals(8, status.queuedTrackCount)
        assertEquals((1..5).map { "Title q$it" }, status.nextTrackTitles)
    }

    @Test fun `volume is lowered by percentage points`() = runBlocking<Unit> {
        volume.current = 70

        assertEquals(BotVolume(50), commands().volume.changeBotVolume(-20))
        assertEquals(50, volume.current)
        assertTrue(volume.stopped)
    }

    @Test fun `relative volume stays in range`() = runBlocking<Unit> {
        volume.current = 10
        assertEquals(BotVolume(0), commands().volume.changeBotVolume(-50))
        volume.current = 190
        assertEquals(BotVolume(200), commands().volume.changeBotVolume(50))
    }

    @Test fun `exact volume out of range never connects`() = runBlocking<Unit> {
        val commands = commands()

        assertThrows(AppFunctionInvalidArgumentException::class.java) {
            runBlocking { commands.volume.setBotVolume(201) }
        }
        assertFalse(volume.started)
    }

    @Test fun `unavailable helper leaves the volume alone and closes the connection`() = runBlocking<Unit> {
        volume.state = LocalVolumeStateDto(installed = true, online = false, available = false, volume = null)
        val commands = commands()

        assertThrows(AppFunctionAppUnknownException::class.java) {
            runBlocking { commands.volume.setBotVolume(40) }
        }
        assertEquals(0, volume.writes)
        assertTrue(volume.stopped)
    }

    private class FakeVolumeClient : KajutaBotRealtimeClient {
        var current = 100
        var writes = 0
        var started = false
        var stopped = false
        var state: LocalVolumeStateDto? = null
        override val updates: Flow<QueueSnapshotResponse> = emptyFlow()
        override val heartbeats: Flow<Unit> = emptyFlow()
        override val closed: Flow<RealtimeDisconnect> = emptyFlow()
        override val localVolumeUpdates: Flow<LocalVolumeStateDto> = emptyFlow()
        override suspend fun getLocalVolumeState() =
            state ?: LocalVolumeStateDto(installed = true, online = true, available = true, volume = current)
        override suspend fun setLocalVolume(volume: Int) { current = volume; writes++ }
        override suspend fun start() { started = true }
        override suspend fun subscribeGuild(guildId: String) = true
        override suspend fun stop() { stopped = true }
    }
}
