package com.tryniecki.kajutabot.ui.player

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.tryniecki.kajutabot.api.client.KajutaBotApi
import com.tryniecki.kajutabot.api.model.auth.AuthUserResponse
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.api.model.queue.EnqueueRequest
import com.tryniecki.kajutabot.api.model.queue.QueueEntryResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.queue.SetQueueRepeatRequest
import com.tryniecki.kajutabot.api.model.queue.SwapQueueEntriesRequest
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import com.tryniecki.kajutabot.auth.AppConfig
import com.tryniecki.kajutabot.auth.FakeAuthApi
import com.tryniecki.kajutabot.auth.FakePendingStorage
import com.tryniecki.kajutabot.auth.FakeSessionStore
import com.tryniecki.kajutabot.auth.SessionManager
import com.tryniecki.kajutabot.auth.UserSession
import com.tryniecki.kajutabot.data.preferences.UserPreferencesRepository
import com.tryniecki.kajutabot.data.repository.PlayerRepository
import com.tryniecki.kajutabot.data.repository.QueueMutationCoordinator
import com.tryniecki.kajutabot.ui.feedback.FeedbackEffect
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerFeedbackTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val coordinator = QueueMutationCoordinator()
    private val track = PlaybackTrackResponse("id", "YouTube", "Track", "https://example.test/track", 1000, null, 0)
    private val snapshot = QueueSnapshotResponse(
        guildId = "guild", voiceChannelId = "voice", nowPlaying = track, nowPlayingFromRadio = false,
        radio = RadioStateResponse(false),
        pendingEntries = listOf(QueueEntryResponse("a", 1, track), QueueEntryResponse("b", 2, track)),
        pendingEntriesCount = 2, pendingDurationMilliseconds = 2000, version = 1, queueVersion = 1,
    )
    private var mutate: suspend () -> QueueSnapshotResponse = { snapshot.copy(version = 2, queueVersion = 2) }

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test fun `bulk enqueue confirms once after acknowledgement and cancellation is silent`() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.selectGuild("guild"); runCurrent(); model.selectChannel("voice")
        val gate = CompletableDeferred<QueueSnapshotResponse>()
        mutate = { gate.await() }
        model.feedbackEvents.test {
            model.enqueueInputs(listOf("one", "two", "three"))
            runCurrent()
            expectNoEvents()
            gate.complete(snapshot.copy(version = 2))
            runCurrent()
            assertEquals(FeedbackEffect.Confirm, awaitItem())
            expectNoEvents()
            mutate = { throw kotlinx.coroutines.CancellationException() }
            model.enqueueInputs(listOf("cancelled"))
            runCurrent()
            expectNoEvents()
        }
    }

    @Test fun `repeat uses server result and external snapshots and media actions stay silent`() = runTest(dispatcher) {
        val model = model()
        runCurrent(); model.selectGuild("guild"); runCurrent()
        val gate = CompletableDeferred<QueueSnapshotResponse>()
        mutate = { gate.await() }
        model.feedbackEvents.test {
            model.setRepeat(true)
            runCurrent()
            expectNoEvents()
            gate.complete(snapshot.copy(isRepeatEnabled = true, version = 2, queueVersion = 2))
            runCurrent()
            assertEquals(FeedbackEffect.ToggleOn, awaitItem())
            mutate = { snapshot.copy(isRepeatEnabled = false, version = 3, queueVersion = 3) }
            model.setRepeat(false)
            runCurrent()
            assertEquals(FeedbackEffect.ToggleOff, awaitItem())
            coordinator.publish(snapshot.copy(version = 4, queueVersion = 4))
            runCurrent()
            expectNoEvents()
            model.setRepeat(true, feedbackEnabled = false)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test fun `swap rejects stale drop and confirms only committed changed order`() = runTest(dispatcher) {
        val model = model()
        runCurrent(); model.selectGuild("guild"); runCurrent(); model.refreshQueue("guild"); runCurrent()
        model.feedbackEvents.test {
            assertFalse(model.swapEntries("a", "a", 1))
            expectNoEvents()
            assertFalse(model.swapEntries("a", "b", 0))
            assertEquals(FeedbackEffect.Reject, awaitItem())
            val gate = CompletableDeferred<QueueSnapshotResponse>()
            mutate = { gate.await() }
            assertTrue(model.swapEntries("a", "b", 1))
            runCurrent()
            expectNoEvents() // Optimistic preview is not success.
            gate.complete(snapshot.copy(version = 2, queueVersion = 2))
            runCurrent()
            assertEquals(FeedbackEffect.Confirm, awaitItem())
            mutate = { throw IOException("offline") }
            assertTrue(model.swapEntries("a", "b", 2))
            runCurrent()
            assertEquals(FeedbackEffect.Reject, awaitItem())
            expectNoEvents() // Reconciliation GET must not confirm the failed mutation.
        }
    }

    private suspend fun model(): PlayerViewModel {
        val unused = Proxy.newProxyInstance(KajutaBotApi::class.java.classLoader, arrayOf(KajutaBotApi::class.java)) {
                _, method, _ -> error("Unexpected API call: ${method.name}")
        } as KajutaBotApi
        val api = object : KajutaBotApi by unused {
            override suspend fun getMyGuilds(): List<DiscordGuildResponse> = emptyList()
            override suspend fun getVoiceChannels(guildId: String): List<DiscordVoiceChannelResponse> = emptyList()
            override suspend fun getQueue(guildId: String) = snapshot
            override suspend fun enqueue(guildId: String, request: EnqueueRequest, idempotencyKey: String) = mutate()
            override suspend fun setRepeat(guildId: String, request: SetQueueRepeatRequest, idempotencyKey: String) = mutate()
            override suspend fun swapQueueEntries(guildId: String, request: SwapQueueEntriesRequest, idempotencyKey: String) = mutate()
        }
        val manager = SessionManager(FakeAuthApi(), { api }, FakeSessionStore(UserSession(
            "token", "2030-01-01T00:00:00Z", "refresh", "2030-02-01T00:00:00Z", AuthUserResponse("id", "user", "User"),
        )), FakePendingStorage(), AppConfig("https://example.test", "client"))
        manager.restore()
        val preferences = object : DataStore<Preferences> {
            override val data = MutableStateFlow(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                transform(data.value).also { data.value = it }
        }
        return PlayerViewModel(PlayerRepository(manager, "https://example.test", coordinator),
            UserPreferencesRepository(preferences)).also { store.put("player", it) }
    }
}
