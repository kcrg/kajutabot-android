package com.tryniecki.kajutabot.ui.history

import androidx.lifecycle.ViewModel
import com.tryniecki.kajutabot.ui.feedback.FeedbackEffect
import com.tryniecki.kajutabot.ui.feedback.UserFeedback
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.api.model.history.PlaybackHistoryEntryResponse
import com.tryniecki.kajutabot.data.preferences.GuildSelection
import com.tryniecki.kajutabot.data.repository.HistoryRepository
import com.tryniecki.kajutabot.data.repository.SessionHistoryRepository
import com.tryniecki.kajutabot.ui.components.SwipeActionStatus
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.ui.userMessageForError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val guildId: String? = null,
    val canEnqueue: Boolean = false,
    val entries: List<PlaybackHistoryEntryResponse> = emptyList(),
    val nextCursor: Long? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val error: UiText? = null,
    val queueStatuses: Map<Long, SwipeActionStatus> = emptyMap(),
)

class HistoryViewModel(
    private val repository: HistoryRepository,
    guildSelection: Flow<GuildSelection>,
) : ViewModel() {
    private val _ui = MutableStateFlow(HistoryUiState())
    private val feedback = UserFeedback()
    val feedbackEvents = feedback.events
    val ui = _ui.asStateFlow()
    private var selection = GuildSelection()
    private var generation = 0L
    private var targetGeneration = 0L
    private var loadJob: Job? = null
    // Keep the ID index across pages instead of rebuilding it from the full list.
    private val knownEntryIds = HashSet<Long>()
    private val feedbackJobs = mutableMapOf<Long, Job>()

    init {
        viewModelScope.launch {
            guildSelection.collect { target ->
                val guildChanged = selection.guildId != target.guildId
                selection = target
                if (guildChanged) {
                    targetGeneration++
                    feedbackJobs.values.forEach { it.cancel() }
                    feedbackJobs.clear()
                    _ui.value = HistoryUiState()
                    refresh()
                }
                _ui.update { it.copy(canEnqueue = target.guildId != null && target.voiceChannelId != null) }
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        generation++
        knownEntryIds.clear()
        _ui.value = HistoryUiState(
            guildId = selection.guildId,
            canEnqueue = selection.guildId != null && selection.voiceChannelId != null,
            queueStatuses = _ui.value.queueStatuses,
        )
        loadPage(cursor = null)
    }

    fun loadMore() {
        val state = _ui.value
        if (state.isLoading || state.loadFailed) return
        loadPage(state.nextCursor ?: return)
    }

    fun retry() {
        if (_ui.value.isLoading) return
        loadPage(_ui.value.nextCursor)
    }

    private fun loadPage(cursor: Long?) {
        val guildId = selection.guildId ?: return
        val current = generation
        _ui.update { it.copy(isLoading = true, loadFailed = false, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val page = repository.getPage(guildId, cursor)
                if (current != generation) return@launch
                val entries = _ui.value.entries
                var merged: ArrayList<PlaybackHistoryEntryResponse>? = null
                for (entry in page.items) {
                    if (knownEntryIds.add(entry.id)) {
                        val destination = merged ?: ArrayList<PlaybackHistoryEntryResponse>(entries.size + page.items.size)
                            .also { it.addAll(entries); merged = it }
                        destination.add(entry)
                    }
                }
                _ui.update { state ->
                    state.copy(
                        entries = merged ?: state.entries,
                        nextCursor = page.nextCursor,
                        isLoading = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (current == generation) _ui.update {
                    it.copy(isLoading = false, loadFailed = true, error = userMessageForError(e))
                }
            }
        }
    }

    fun enqueue(entryId: Long) {
        val state = _ui.value
        if (!state.canEnqueue || state.queueStatuses[entryId] == SwipeActionStatus.PENDING) return
        val entry = state.entries.firstOrNull { it.id == entryId } ?: return
        val guildId = selection.guildId ?: return
        val channelId = selection.voiceChannelId ?: return
        val current = targetGeneration
        feedbackJobs.remove(entryId)?.cancel()
        _ui.update { it.copy(queueStatuses = it.queueStatuses + (entryId to SwipeActionStatus.PENDING)) }
        viewModelScope.launch {
            val status = try {
                repository.enqueue(guildId, channelId, entry)
                SwipeActionStatus.SUCCESS
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (current == targetGeneration) _ui.update { it.copy(error = userMessageForError(e)) }
                SwipeActionStatus.FAILURE
            }
            if (current != targetGeneration) return@launch
            feedback.emit(if (status == SwipeActionStatus.SUCCESS) FeedbackEffect.Confirm else FeedbackEffect.Reject)
            _ui.update { it.copy(queueStatuses = it.queueStatuses + (entryId to status)) }
            feedbackJobs[entryId] = viewModelScope.launch {
                delay(1_200)
                if (current == targetGeneration) _ui.update { it.copy(queueStatuses = it.queueStatuses - entryId) }
                feedbackJobs.remove(entryId)
            }
        }
    }

    fun dismissMessage() { _ui.update { it.copy(error = null) } }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HistoryViewModel(
                    SessionHistoryRepository(container.sessionManager, container.queueMutationCoordinator),
                    container.preferencesRepository.guildSelection,
                )
            }
        }
    }
}
