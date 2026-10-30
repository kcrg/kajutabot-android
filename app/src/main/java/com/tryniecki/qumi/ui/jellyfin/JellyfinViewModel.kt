package com.tryniecki.qumi.ui.jellyfin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tryniecki.qumi.AppContainer
import com.tryniecki.qumi.R
import com.tryniecki.qumi.api.model.jellyfin.JellyfinAudioItemResponse
import com.tryniecki.qumi.api.model.jellyfin.JellyfinConnectionResponse
import com.tryniecki.qumi.api.model.jellyfin.JellyfinQuickConnectStartResponse
import com.tryniecki.qumi.data.preferences.GuildSelection
import com.tryniecki.qumi.data.repository.JellyfinRepository
import com.tryniecki.qumi.data.repository.SessionJellyfinRepository
import com.tryniecki.qumi.ui.components.SwipeActionStatus
import com.tryniecki.qumi.ui.feedback.FeedbackEffect
import com.tryniecki.qumi.ui.feedback.UserFeedback
import com.tryniecki.qumi.ui.text.UiText
import com.tryniecki.qumi.ui.text.uiText
import com.tryniecki.qumi.ui.userMessageForError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.time.Instant
import java.time.OffsetDateTime

private const val PAGE_SIZE = 50
private const val MAX_SEARCH_LENGTH = 200
private const val SEARCH_DEBOUNCE_MS = 350L
private const val QUICK_CONNECT_POLL_MS = 3_000L
private const val ENQUEUE_FEEDBACK_MS = 1_200L

enum class JellyfinConnectionStatus { CHECKING, CONNECTED, DISCONNECTED, ERROR }

sealed interface JellyfinLinkState {
    data object Idle : JellyfinLinkState
    data object Starting : JellyfinLinkState
    data class Waiting(val code: String) : JellyfinLinkState
    data class Failed(val message: UiText) : JellyfinLinkState
}

data class JellyfinUiState(
    val status: JellyfinConnectionStatus = JellyfinConnectionStatus.CHECKING,
    val connection: JellyfinConnectionResponse? = null,
    val link: JellyfinLinkState = JellyfinLinkState.Idle,
    val isDisconnecting: Boolean = false,
    val disconnectError: UiText? = null,
    val searchQuery: String = "",
    val items: List<JellyfinAudioItemResponse> = emptyList(),
    val totalCount: Int = 0,
    val isLibraryLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadFailed: Boolean = false,
    val canEnqueue: Boolean = false,
    val queueStatuses: Map<String, SwipeActionStatus> = emptyMap(),
    val error: UiText? = null,
) {
    val isConnected: Boolean get() = status == JellyfinConnectionStatus.CONNECTED
    val canLoadMore: Boolean get() = isLibraryLoaded && !isLoading && !loadFailed && items.size < totalCount
}

class JellyfinViewModel(
    private val repository: JellyfinRepository,
    guildSelection: Flow<GuildSelection>,
    private val isAvailable: Boolean,
) : ViewModel() {
    private val _ui = MutableStateFlow(
        JellyfinUiState(
            status = if (isAvailable) JellyfinConnectionStatus.CHECKING else JellyfinConnectionStatus.DISCONNECTED,
        ),
    )
    val ui = _ui.asStateFlow()
    val connectionStatus = _ui.map { it.status }
        .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.status)
    private val feedback = UserFeedback()
    val feedbackEvents = feedback.events

    private var selection = GuildSelection()
    private var connectionJob: Job? = null
    private var linkJob: Job? = null
    private var libraryJob: Job? = null
    private var searchJob: Job? = null
    private var libraryGeneration = 0L
    // Results of the previous search stay on screen until the first page of the new one arrives.
    private var replaceItems = false
    private val knownItemIds = HashSet<String>()
    private val feedbackJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            guildSelection.collect { target ->
                selection = target
                _ui.update { it.copy(canEnqueue = target.guildId != null && target.voiceChannelId != null) }
            }
        }
        refreshConnection()
    }

    /** Also picks up a server linked or removed on the web panel. */
    fun refreshConnection() {
        if (!isAvailable || connectionJob?.isActive == true) return
        connectionJob = viewModelScope.launch {
            try {
                val connection = repository.getConnection()
                if (connection == null) showDisconnected() else showConnected(connection)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _ui.update { if (it.isConnected) it else it.copy(status = JellyfinConnectionStatus.ERROR) }
            }
        }
    }

    fun startQuickConnect(serverUrl: String) {
        val url = serverUrl.trim()
        if (url.isEmpty()) return
        linkJob?.cancel()
        _ui.update { it.copy(link = JellyfinLinkState.Starting) }
        linkJob = viewModelScope.launch {
            try {
                val attempt = repository.startQuickConnect(url)
                _ui.update { it.copy(link = JellyfinLinkState.Waiting(attempt.code)) }
                awaitQuickConnect(attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(link = JellyfinLinkState.Failed(userMessageForError(e))) }
            }
        }
    }

    // The backend ends an attempt with a final status; the clock only matters once it stops answering.
    private suspend fun awaitQuickConnect(attempt: JellyfinQuickConnectStartResponse) {
        while (true) {
            delay(QUICK_CONNECT_POLL_MS)
            val result = try {
                repository.getQuickConnectStatus(attempt.attemptId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The user is approving this in another app; a dropped poll must not lose the attempt.
                if (attempt.hasExpired()) {
                    _ui.update { it.copy(link = JellyfinLinkState.Failed(uiText(R.string.jellyfin_link_expired))) }
                    return
                }
                continue
            }
            when (result.status) {
                "waiting" -> Unit
                "authorized" -> {
                    _ui.update { it.copy(link = JellyfinLinkState.Idle) }
                    val connection = result.connection
                    if (connection != null) showConnected(connection) else refreshConnection()
                    return
                }
                else -> {
                    val message = when (result.status) {
                        "expired" -> R.string.jellyfin_link_expired
                        "rejected" -> R.string.jellyfin_link_rejected
                        else -> R.string.jellyfin_link_unreachable
                    }
                    _ui.update { it.copy(link = JellyfinLinkState.Failed(uiText(message))) }
                    return
                }
            }
        }
    }

    private fun JellyfinQuickConnectStartResponse.hasExpired(): Boolean =
        runCatching { OffsetDateTime.parse(expiresAtUtc).toInstant() < Instant.now() }.getOrDefault(false)

    fun cancelQuickConnect() {
        linkJob?.cancel()
        _ui.update { it.copy(link = JellyfinLinkState.Idle) }
    }

    fun disconnect() {
        if (_ui.value.isDisconnecting) return
        _ui.update { it.copy(isDisconnecting = true, disconnectError = null) }
        viewModelScope.launch {
            try {
                repository.disconnect()
                showDisconnected()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(disconnectError = userMessageForError(e)) }
            } finally {
                _ui.update { it.copy(isDisconnecting = false) }
            }
        }
    }

    fun ensureLibraryLoaded() {
        val state = _ui.value
        if (state.isConnected && !state.isLibraryLoaded && !state.isLoading) reloadLibrary()
    }

    fun setSearchQuery(query: String) {
        val next = query.take(MAX_SEARCH_LENGTH)
        if (next == _ui.value.searchQuery) return
        _ui.update { it.copy(searchQuery = next) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            reloadLibrary()
        }
    }

    fun reloadLibrary() {
        libraryJob?.cancel()
        libraryGeneration++
        replaceItems = true
        _ui.update { it.copy(loadFailed = false, error = null) }
        loadPage()
    }

    fun loadMore() {
        if (_ui.value.canLoadMore) loadPage()
    }

    fun retryLoad() {
        if (!_ui.value.isLoading) loadPage()
    }

    private fun loadPage() {
        val generation = libraryGeneration
        val state = _ui.value
        val replace = replaceItems
        _ui.update { it.copy(isLoading = true, isLoadingMore = !replace, loadFailed = false) }
        libraryJob = viewModelScope.launch {
            try {
                val page = repository.getAudioPage(
                    startIndex = if (replace) 0 else state.items.size,
                    limit = PAGE_SIZE,
                    searchTerm = state.searchQuery.trim().ifEmpty { null },
                )
                if (generation != libraryGeneration) return@launch
                replaceItems = false
                if (replace) knownItemIds.clear()
                // Offsets can overlap if the library changes between pages, and keys must stay unique.
                val added = page.items.filter { knownItemIds.add(it.itemId) }
                _ui.update {
                    it.copy(
                        items = if (replace) added else it.items + added,
                        totalCount = page.totalCount,
                        isLibraryLoaded = true,
                        isLoading = false,
                        isLoadingMore = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != libraryGeneration) return@launch
                if (e is HttpException && e.code() == 404) {
                    showDisconnected()
                } else {
                    _ui.update {
                        it.copy(isLoading = false, isLoadingMore = false, loadFailed = true, error = userMessageForError(e))
                    }
                }
            }
        }
    }

    fun enqueue(itemId: String) {
        val state = _ui.value
        if (!state.canEnqueue || state.queueStatuses[itemId] == SwipeActionStatus.PENDING) return
        val guildId = selection.guildId ?: return
        val channelId = selection.voiceChannelId ?: return
        feedbackJobs.remove(itemId)?.cancel()
        _ui.update { it.copy(queueStatuses = it.queueStatuses + (itemId to SwipeActionStatus.PENDING)) }
        viewModelScope.launch {
            val status = try {
                repository.enqueue(guildId, channelId, itemId)
                SwipeActionStatus.SUCCESS
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(error = userMessageForError(e)) }
                SwipeActionStatus.FAILURE
            }
            feedback.emit(if (status == SwipeActionStatus.SUCCESS) FeedbackEffect.Confirm else FeedbackEffect.Reject)
            _ui.update { it.copy(queueStatuses = it.queueStatuses + (itemId to status)) }
            feedbackJobs[itemId] = viewModelScope.launch {
                delay(ENQUEUE_FEEDBACK_MS)
                _ui.update { it.copy(queueStatuses = it.queueStatuses - itemId) }
                feedbackJobs.remove(itemId)
            }
        }
    }

    fun dismissMessage() {
        _ui.update { it.copy(error = null) }
    }

    private fun showConnected(connection: JellyfinConnectionResponse) {
        _ui.update { it.copy(status = JellyfinConnectionStatus.CONNECTED, connection = connection) }
    }

    private fun showDisconnected() {
        libraryJob?.cancel()
        searchJob?.cancel()
        libraryGeneration++
        replaceItems = false
        knownItemIds.clear()
        _ui.update {
            it.copy(
                status = JellyfinConnectionStatus.DISCONNECTED,
                connection = null,
                searchQuery = "",
                items = emptyList(),
                totalCount = 0,
                isLibraryLoaded = false,
                isLoading = false,
                isLoadingMore = false,
                loadFailed = false,
            )
        }
    }

    companion object {
        fun factory(container: AppContainer, isAvailable: Boolean): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                JellyfinViewModel(
                    SessionJellyfinRepository(container.sessionManager, container.queueMutationCoordinator),
                    container.preferencesRepository.guildSelection,
                    isAvailable,
                )
            }
        }
    }
}
