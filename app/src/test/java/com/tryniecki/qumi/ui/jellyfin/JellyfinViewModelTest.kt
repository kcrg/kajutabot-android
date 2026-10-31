package com.tryniecki.qumi.ui.jellyfin

import androidx.lifecycle.ViewModelStore
import com.tryniecki.qumi.R
import com.tryniecki.qumi.api.model.jellyfin.JellyfinAudioItemResponse
import com.tryniecki.qumi.api.model.jellyfin.JellyfinAudioPageResponse
import com.tryniecki.qumi.api.model.jellyfin.JellyfinConnectionResponse
import com.tryniecki.qumi.api.model.jellyfin.JellyfinQuickConnectEvent
import com.tryniecki.qumi.api.model.jellyfin.JellyfinQuickConnectStartResponse
import com.tryniecki.qumi.api.model.jellyfin.JellyfinQuickConnectStatusResponse
import com.tryniecki.qumi.auth.httpError
import com.tryniecki.qumi.data.preferences.GuildSelection
import com.tryniecki.qumi.data.repository.JellyfinRepository
import com.tryniecki.qumi.ui.components.SwipeActionStatus
import com.tryniecki.qumi.ui.text.uiText
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val repository = FakeJellyfinRepository()
    private val connection = JellyfinConnectionResponse("https://jf.example", "Home", "kacper", "2026-01-01T00:00:00Z")

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun model(isAvailable: Boolean = true, selection: GuildSelection = GuildSelection("guild", "voice")) =
        JellyfinViewModel(repository, MutableStateFlow(selection), isAvailable).also { store.put("jellyfin", it) }

    private fun items(range: IntRange) = range.map { JellyfinAudioItemResponse("id-$it", "Track $it") }

    @Test fun `linked account is connected and an unlinked one is not`() = runTest(dispatcher) {
        repository.connection = connection
        val linked = model()
        assertEquals(JellyfinConnectionStatus.CHECKING, linked.ui.value.status)
        runCurrent()
        assertEquals(JellyfinConnectionStatus.CONNECTED, linked.ui.value.status)

        repository.connection = null
        linked.refreshConnection()
        runCurrent()
        assertEquals(JellyfinConnectionStatus.DISCONNECTED, linked.ui.value.status)
        assertNull(linked.ui.value.connection)
    }

    @Test fun `connection status only changes with the connection`() = runTest(dispatcher) {
        repository.connection = connection
        val model = model()
        runCurrent()
        assertEquals(JellyfinConnectionStatus.CONNECTED, model.connectionStatus.value)

        model.setSearchQuery("tool")
        runCurrent()
        assertEquals(JellyfinConnectionStatus.CONNECTED, model.connectionStatus.value)

        model.disconnect()
        runCurrent()
        assertEquals(JellyfinConnectionStatus.DISCONNECTED, model.connectionStatus.value)
    }

    @Test fun `guest never calls the API and never sees a connection`() = runTest(dispatcher) {
        val model = model(isAvailable = false)
        runCurrent()

        assertEquals(0, repository.connectionCalls)
        assertEquals(JellyfinConnectionStatus.DISCONNECTED, model.ui.value.status)
    }

    @Test fun `failed check keeps a known connection but reports an unknown one`() = runTest(dispatcher) {
        repository.connectionError = IOException("offline")
        val model = model()
        runCurrent()
        assertEquals(JellyfinConnectionStatus.ERROR, model.ui.value.status)

        repository.connectionError = null
        repository.connection = connection
        model.refreshConnection()
        runCurrent()
        repository.connectionError = IOException("offline")
        model.refreshConnection()
        runCurrent()
        assertEquals(JellyfinConnectionStatus.CONNECTED, model.ui.value.status)
    }

    @Test fun `quick connect shows the code and connects once approved`() = runTest(dispatcher) {
        val model = model()
        runCurrent()

        model.startQuickConnect("  https://jf.example ")
        runCurrent()
        assertEquals("https://jf.example", repository.startedUrl)
        assertEquals(JellyfinLinkState.Waiting("123456"), model.ui.value.link)

        advanceTimeBy(15_001)
        assertEquals(JellyfinLinkState.Waiting("123456"), model.ui.value.link)

        repository.quickConnectStatus = JellyfinQuickConnectStatusResponse("authorized", connection)
        advanceTimeBy(15_001)
        assertEquals(JellyfinLinkState.Idle, model.ui.value.link)
        assertTrue(model.ui.value.isConnected)
    }

    @Test fun `dropped poll keeps waiting and a final status ends it`() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.startQuickConnect("https://jf.example")
        runCurrent()

        repository.quickConnectError = IOException("offline")
        advanceTimeBy(15_001)
        assertEquals(JellyfinLinkState.Waiting("123456"), model.ui.value.link)

        repository.quickConnectError = null
        repository.quickConnectStatus = JellyfinQuickConnectStatusResponse("rejected")
        advanceTimeBy(15_001)
        assertEquals(JellyfinLinkState.Failed(uiText(R.string.jellyfin_link_rejected)), model.ui.value.link)
    }

    @Test fun `polling gives up once the attempt expired and the backend stays silent`() = runTest(dispatcher) {
        repository.quickConnectExpiresAt = "2020-01-01T00:00:00Z"
        repository.quickConnectError = IOException("offline")
        val model = model()
        runCurrent()
        model.startQuickConnect("https://jf.example")
        runCurrent()

        advanceTimeBy(15_001)

        assertEquals(JellyfinLinkState.Failed(uiText(R.string.jellyfin_link_expired)), model.ui.value.link)
    }

    @Test fun `rejected address is explained in the dialog`() = runTest(dispatcher) {
        repository.quickConnectStartError = httpError(400, "invalid_server_url")
        val model = model()
        runCurrent()

        model.startQuickConnect("nonsense")
        runCurrent()

        assertEquals(JellyfinLinkState.Failed(uiText(R.string.error_jellyfin_invalid_url)), model.ui.value.link)
    }

    @Test fun `cancelling stops polling`() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.startQuickConnect("https://jf.example")
        runCurrent()

        model.cancelQuickConnect()
        advanceTimeBy(10_000)

        assertEquals(JellyfinLinkState.Idle, model.ui.value.link)
        assertEquals(0, repository.pollCalls)
    }

    @Test fun `library pages without duplicates until everything is loaded`() = runTest(dispatcher) {
        repository.connection = connection
        repository.pages = listOf(items(1..50), items(50..60))
        repository.totalCount = 60
        val model = model()
        runCurrent()

        model.ensureLibraryLoaded()
        runCurrent()
        assertEquals(50, model.ui.value.items.size)
        assertTrue(model.ui.value.canLoadMore)

        model.loadMore()
        runCurrent()
        assertEquals(60, model.ui.value.items.size)
        assertEquals(60, model.ui.value.items.distinctBy { it.itemId }.size)
        assertEquals(listOf(0, 50), repository.pageStarts)
    }

    @Test fun `search waits for typing to stop and restarts from the first page`() = runTest(dispatcher) {
        repository.connection = connection
        val model = model()
        runCurrent()
        model.ensureLibraryLoaded()
        runCurrent()

        model.setSearchQuery("to")
        advanceTimeBy(100)
        model.setSearchQuery("tool ")
        advanceTimeBy(351)

        assertEquals(listOf(null, "tool"), repository.searchTerms)
        assertEquals(listOf(0, 0), repository.pageStarts)
    }

    @Test fun `new search keeps the current results until its own arrive`() = runTest(dispatcher) {
        repository.connection = connection
        repository.pages = listOf(items(1..2), items(3..3))
        repository.totalCount = 2
        val model = model()
        runCurrent()
        model.ensureLibraryLoaded()
        runCurrent()

        model.reloadLibrary()
        assertTrue(model.ui.value.isLoading)
        assertTrue(model.ui.value.isLibraryLoaded)
        assertFalse(model.ui.value.isLoadingMore)
        assertEquals(2, model.ui.value.items.size)

        runCurrent()
        assertEquals(listOf("id-3"), model.ui.value.items.map { it.itemId })
    }

    @Test fun `retry after a failed search starts from its first page`() = runTest(dispatcher) {
        repository.connection = connection
        repository.pages = listOf(items(1..2), items(3..3))
        repository.totalCount = 2
        val model = model()
        runCurrent()
        model.ensureLibraryLoaded()
        runCurrent()

        repository.audioError = httpError(502, "jellyfin_unavailable")
        model.reloadLibrary()
        runCurrent()
        assertEquals(2, model.ui.value.items.size)

        repository.audioError = null
        model.retryLoad()
        runCurrent()

        assertEquals(listOf(0, 0), repository.pageStarts)
        assertEquals(listOf("id-3"), model.ui.value.items.map { it.itemId })
    }

    @Test fun `library failure can be retried`() = runTest(dispatcher) {
        repository.connection = connection
        repository.audioError = httpError(502, "jellyfin_unavailable")
        val model = model()
        runCurrent()
        model.ensureLibraryLoaded()
        runCurrent()
        assertTrue(model.ui.value.loadFailed)

        repository.audioError = null
        repository.pages = listOf(items(1..2))
        repository.totalCount = 2
        model.retryLoad()
        runCurrent()

        assertFalse(model.ui.value.loadFailed)
        assertEquals(2, model.ui.value.items.size)
    }

    @Test fun `link removed elsewhere disconnects instead of showing an error`() = runTest(dispatcher) {
        repository.connection = connection
        repository.audioError = httpError(404, "jellyfin_not_connected")
        val model = model()
        runCurrent()
        model.ensureLibraryLoaded()
        runCurrent()

        assertEquals(JellyfinConnectionStatus.DISCONNECTED, model.ui.value.status)
        assertNull(model.ui.value.error)
    }

    @Test fun `disconnect clears the library`() = runTest(dispatcher) {
        repository.connection = connection
        repository.pages = listOf(items(1..2))
        repository.totalCount = 2
        val model = model()
        runCurrent()
        model.ensureLibraryLoaded()
        runCurrent()

        model.disconnect()
        runCurrent()

        assertEquals(JellyfinConnectionStatus.DISCONNECTED, model.ui.value.status)
        assertEquals(emptyList<JellyfinAudioItemResponse>(), model.ui.value.items)
        assertEquals(1, repository.disconnectCalls)
    }

    @Test fun `enqueue targets the selected channel and reports its status`() = runTest(dispatcher) {
        val model = model()
        runCurrent()

        model.enqueue("id-1")
        assertEquals(SwipeActionStatus.PENDING, model.ui.value.queueStatuses["id-1"])
        runCurrent()

        assertEquals(listOf(Triple("guild", "voice", "id-1")), repository.enqueued)
        assertEquals(SwipeActionStatus.SUCCESS, model.ui.value.queueStatuses["id-1"])
        advanceTimeBy(1_201)
        assertNull(model.ui.value.queueStatuses["id-1"])
    }

    @Test fun `enqueue without a voice channel does nothing`() = runTest(dispatcher) {
        val model = model(selection = GuildSelection("guild", null))
        runCurrent()

        model.enqueue("id-1")
        runCurrent()

        assertFalse(model.ui.value.canEnqueue)
        assertEquals(emptyList<Triple<String, String, String>>(), repository.enqueued)
    }

    @Test fun `quick connect result from the hub ends the attempt without a poll`() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.startQuickConnect("https://jf.example")
        runCurrent()

        repository.quickConnectUpdates.emit(JellyfinQuickConnectEvent("other-attempt", "rejected"))
        runCurrent()
        assertEquals(JellyfinLinkState.Waiting("123456"), model.ui.value.link)

        repository.quickConnectUpdates.emit(JellyfinQuickConnectEvent("attempt", "authorized", connection))
        runCurrent()
        assertEquals(JellyfinLinkState.Idle, model.ui.value.link)
        assertTrue(model.ui.value.isConnected)
        assertEquals(0, repository.pollCalls)
    }

    @Test fun `hub connection checks an attempt whose event could have been missed`() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.startQuickConnect("https://jf.example")
        runCurrent()

        repository.quickConnectStatus = JellyfinQuickConnectStatusResponse("rejected")
        repository.hubConnected.emit(Unit)
        runCurrent()

        assertEquals(1, repository.pollCalls)
        assertEquals(JellyfinLinkState.Failed(uiText(R.string.jellyfin_link_rejected)), model.ui.value.link)
    }

    private class FakeJellyfinRepository : JellyfinRepository {
        override val quickConnectUpdates = MutableSharedFlow<JellyfinQuickConnectEvent>()
        override val hubConnected = MutableSharedFlow<Unit>()
        var connection: JellyfinConnectionResponse? = null
        var connectionError: Exception? = null
        var quickConnectStatus = JellyfinQuickConnectStatusResponse("waiting")
        var quickConnectError: Exception? = null
        var quickConnectStartError: Exception? = null
        var quickConnectExpiresAt = "2030-01-01T00:00:00Z"
        var audioError: Exception? = null
        var pages = listOf<List<JellyfinAudioItemResponse>>()
        var totalCount = 0
        var startedUrl: String? = null
        var connectionCalls = 0
        var pollCalls = 0
        var disconnectCalls = 0
        val pageStarts = mutableListOf<Int>()
        val searchTerms = mutableListOf<String?>()
        val enqueued = mutableListOf<Triple<String, String, String>>()

        override suspend fun getConnection(): JellyfinConnectionResponse? {
            connectionCalls++
            connectionError?.let { throw it }
            return connection
        }
        override suspend fun startQuickConnect(serverUrl: String): JellyfinQuickConnectStartResponse {
            startedUrl = serverUrl
            quickConnectStartError?.let { throw it }
            return JellyfinQuickConnectStartResponse("attempt", "123456", quickConnectExpiresAt)
        }
        override suspend fun getQuickConnectStatus(attemptId: String): JellyfinQuickConnectStatusResponse {
            pollCalls++
            quickConnectError?.let { throw it }
            return quickConnectStatus
        }
        override suspend fun disconnect() { disconnectCalls++ }
        override suspend fun getAudioPage(startIndex: Int, limit: Int, searchTerm: String?): JellyfinAudioPageResponse {
            audioError?.let { throw it }
            pageStarts += startIndex
            searchTerms += searchTerm
            return JellyfinAudioPageResponse(totalCount, pages.getOrElse(pageStarts.size - 1) { emptyList() })
        }
        override suspend fun enqueue(guildId: String, channelId: String, itemId: String) {
            enqueued += Triple(guildId, channelId, itemId)
        }
    }
}
