package com.tryniecki.qumi.ui.history

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.tryniecki.qumi.ui.feedback.FeedbackEffect
import com.tryniecki.qumi.api.model.history.PlaybackHistoryEntryResponse
import com.tryniecki.qumi.api.model.history.PlaybackHistoryPageResponse
import com.tryniecki.qumi.data.preferences.GuildSelection
import com.tryniecki.qumi.data.repository.HistoryRepository
import com.tryniecki.qumi.ui.components.SwipeActionStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val selection = MutableStateFlow(GuildSelection("guild-a", "voice-a"))
    private val repository = FakeHistoryRepository()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model() = HistoryViewModel(repository, selection).also { store.put("history", it) }

    @Test fun `only completed local enqueue emits feedback and refresh stays silent`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.queue = { gate.await() }
        val model = model()
        model.feedbackEvents.test {
            runCurrent()
            expectNoEvents()
            model.enqueue(1)
            model.enqueue(1)
            runCurrent()
            expectNoEvents()
            gate.complete(Unit)
            runCurrent()
            assertEquals(FeedbackEffect.Confirm, awaitItem())
            model.refresh()
            runCurrent()
            expectNoEvents()
            repository.queue = { throw IOException("offline") }
            model.enqueue(1)
            runCurrent()
            assertEquals(FeedbackEffect.Reject, awaitItem())
            expectNoEvents()
        }
    }

    @Test fun `cancelled and obsolete enqueue outcomes do not emit feedback`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.queue = { gate.await() }
        val model = model()
        runCurrent()
        model.feedbackEvents.test {
            model.enqueue(1)
            runCurrent()
            selection.value = GuildSelection("guild-b", "voice-b")
            runCurrent()
            gate.complete(Unit)
            runCurrent()
            expectNoEvents()
            repository.queue = { throw kotlinx.coroutines.CancellationException() }
            model.enqueue(1)
            runCurrent()
            expectNoEvents()
        }
    }

    @Test fun `paginates by cursor and retains repeated tracks as separate plays`() = runTest(dispatcher) {
        repository.load = { _, cursor ->
            when (cursor) {
                null -> PlaybackHistoryPageResponse((1L..50L).map(::entry), 50)
                else -> PlaybackHistoryPageResponse(listOf(entry(50), entry(51)), null)
            }
        }
        val model = model()
        runCurrent()
        assertEquals(50, model.ui.value.entries.size)
        model.loadMore()
        model.loadMore()
        runCurrent()
        assertEquals(listOf(null, 50L), repository.reads.map { it.second })
        assertEquals(51, model.ui.value.entries.size)
        model.loadMore()
        runCurrent()
        assertEquals(2, repository.reads.size)
    }

    @Test fun `failed next page keeps entries and retries same cursor without auto retry`() = runTest(dispatcher) {
        var fail = true
        repository.load = { _, cursor ->
            if (cursor == null) PlaybackHistoryPageResponse(listOf(entry(1)), 1)
            else if (fail) throw IOException("offline")
            else PlaybackHistoryPageResponse(listOf(entry(2)), null)
        }
        val model = model()
        runCurrent()
        model.loadMore()
        runCurrent()
        assertTrue(model.ui.value.loadFailed)
        assertEquals(listOf(1L), model.ui.value.entries.map { it.id })
        model.loadMore()
        runCurrent()
        assertEquals(2, repository.reads.size)
        fail = false
        model.retry()
        runCurrent()
        assertEquals(listOf(null, 1L, 1L), repository.reads.map { it.second })
        assertFalse(model.ui.value.loadFailed)
        assertEquals(listOf(1L, 2L), model.ui.value.entries.map { it.id })
    }

    @Test fun `duplicate page reuses entries and refresh resets the ID index`() = runTest(dispatcher) {
        repository.load = { _, cursor ->
            PlaybackHistoryPageResponse(listOf(entry(1), entry(1)), if (cursor == null) 1 else null)
        }
        val model = model()
        runCurrent()
        val firstPage = model.ui.value.entries
        assertEquals(listOf(1L), firstPage.map { it.id })
        model.loadMore()
        runCurrent()
        assertSame(firstPage, model.ui.value.entries)
        model.refresh()
        runCurrent()
        assertEquals(listOf(1L), model.ui.value.entries.map { it.id })
    }

    @Test fun `discarded old page cannot contaminate the new guild ID index`() = runTest(dispatcher) {
        val oldPage = CompletableDeferred<PlaybackHistoryPageResponse>()
        repository.load = { guild, cursor ->
            when {
                guild == "guild-a" -> withContext(NonCancellable) { oldPage.await() }
                cursor == null -> PlaybackHistoryPageResponse(listOf(entry(2)), 2)
                else -> PlaybackHistoryPageResponse(listOf(entry(1)), null)
            }
        }
        val model = model()
        runCurrent()
        selection.value = GuildSelection("guild-b", "voice-b")
        runCurrent()
        oldPage.complete(PlaybackHistoryPageResponse(listOf(entry(1)), null))
        runCurrent()
        model.loadMore()
        runCurrent()
        assertEquals(listOf(2L, 1L), model.ui.value.entries.map { it.id })
    }

    @Test fun `guild switch ignores old response even if cancellation is delayed`() = runTest(dispatcher) {
        val oldPage = CompletableDeferred<PlaybackHistoryPageResponse>()
        repository.load = { guild, _ ->
            if (guild == "guild-a") withContext(NonCancellable) { oldPage.await() }
            else PlaybackHistoryPageResponse(listOf(entry(2)), null)
        }
        val model = model()
        runCurrent()
        selection.value = GuildSelection("guild-b", "voice-b")
        runCurrent()
        oldPage.complete(PlaybackHistoryPageResponse(listOf(entry(1)), 1))
        runCurrent()
        assertEquals("guild-b", model.ui.value.guildId)
        assertEquals(listOf(2L), model.ui.value.entries.map { it.id })
        assertFalse(model.ui.value.isLoading)
    }

    @Test fun `refresh resets cursor and reloads first page`() = runTest(dispatcher) {
        repository.load = { _, cursor -> PlaybackHistoryPageResponse(listOf(entry(if (cursor == null) 1 else 2)), 1) }
        val model = model()
        runCurrent()
        model.loadMore()
        runCurrent()
        model.refresh()
        runCurrent()
        assertEquals(listOf(null, 1L, null), repository.reads.map { it.second })
        assertEquals(listOf(1L), model.ui.value.entries.map { it.id })
    }

    @Test fun `channel change uses new target without reloading guild history`() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        selection.value = GuildSelection("guild-a", "voice-b")
        runCurrent()
        model.enqueue(1)
        runCurrent()
        assertEquals(1, repository.reads.size)
        assertEquals(listOf(Triple("guild-a", "voice-b", 1L)), repository.enqueues)
        assertEquals(SwipeActionStatus.SUCCESS, model.ui.value.queueStatuses[1])
        advanceTimeBy(1_200)
        runCurrent()
        assertTrue(model.ui.value.queueStatuses.isEmpty())
    }

    @Test fun `pending enqueue cannot duplicate and survives refresh`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.queue = { gate.await() }
        val model = model()
        runCurrent()
        model.enqueue(1)
        model.enqueue(1)
        runCurrent()
        model.refresh()
        runCurrent()
        assertEquals(SwipeActionStatus.PENDING, model.ui.value.queueStatuses[1])
        assertEquals(1, repository.enqueues.size)
        gate.complete(Unit)
        runCurrent()
        assertEquals(SwipeActionStatus.SUCCESS, model.ui.value.queueStatuses[1])
    }

    @Test fun `queue failure shows failure feedback without removing history`() = runTest(dispatcher) {
        repository.queue = { throw IOException("offline") }
        val model = model()
        runCurrent()
        model.enqueue(1)
        runCurrent()
        assertEquals(SwipeActionStatus.FAILURE, model.ui.value.queueStatuses[1])
        assertEquals(1, model.ui.value.entries.size)
        assertTrue(model.ui.value.error != null)
    }

    @Test fun `late enqueue result from previous guild cannot change new guild feedback`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.queue = { gate.await() }
        val model = model()
        runCurrent()
        model.enqueue(1)
        runCurrent()
        selection.value = GuildSelection("guild-b", "voice-b")
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        assertEquals("guild-b", model.ui.value.guildId)
        assertTrue(model.ui.value.queueStatuses.isEmpty())
        assertEquals(listOf(Triple("guild-a", "voice-a", 1L)), repository.enqueues)
    }

    @Test fun `no guild does not load or enqueue and guild without channel can load`() = runTest(dispatcher) {
        selection.value = GuildSelection()
        val model = model()
        runCurrent()
        model.refresh()
        model.enqueue(1)
        runCurrent()
        assertTrue(repository.reads.isEmpty())
        selection.value = GuildSelection("guild-a", null)
        runCurrent()
        assertEquals(1, repository.reads.size)
        assertFalse(model.ui.value.canEnqueue)
        model.enqueue(1)
        runCurrent()
        assertTrue(repository.enqueues.isEmpty())
    }

    private class FakeHistoryRepository : HistoryRepository {
        val reads = mutableListOf<Pair<String, Long?>>()
        val enqueues = mutableListOf<Triple<String, String, Long>>()
        var load: suspend (String, Long?) -> PlaybackHistoryPageResponse = { _, _ ->
            PlaybackHistoryPageResponse(listOf(entry(1)), null)
        }
        var queue: suspend () -> Unit = {}
        override suspend fun getPage(guildId: String, cursor: Long?): PlaybackHistoryPageResponse {
            reads += guildId to cursor
            return load(guildId, cursor)
        }
        override suspend fun enqueue(guildId: String, channelId: String, entry: PlaybackHistoryEntryResponse) {
            enqueues += Triple(guildId, channelId, entry.id)
            queue()
        }
    }

    companion object {
        private fun entry(id: Long) = PlaybackHistoryEntryResponse(
            id, "YouTube", "same-track", "Repeated track", "https://example.test/track", "2026-10-14T12:00:00Z",
        )
    }
}
