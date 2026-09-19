package com.tryniecki.kajutabot.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.api.client.KajutaBotApiErrors
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.api.model.queue.EnqueueRequest
import com.tryniecki.kajutabot.api.model.queue.QueueMutationRequest
import com.tryniecki.kajutabot.api.model.queue.MoveQueueEntryRequest
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.queue.SetQueueRepeatRequest
import com.tryniecki.kajutabot.api.model.queue.SkipQueueRequest
import com.tryniecki.kajutabot.api.model.search.SearchItemResponse
import com.tryniecki.kajutabot.api.model.common.TrackResponse
import com.tryniecki.kajutabot.ui.userMessageForError
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

data class PlayerUiState(
    val guilds: List<DiscordGuildResponse> = emptyList(),
    val voiceChannels: List<DiscordVoiceChannelResponse> = emptyList(),
    val selectedGuildId: String? = null,
    val selectedVoiceChannelId: String? = null,
    val queue: QueueSnapshotResponse? = null,
    val searchQuery: String = "",
    val searchResults: List<SearchItemResponse> = emptyList(),
    val isLoadingGuilds: Boolean = false,
    val isLoadingQueue: Boolean = false,
    val isSearching: Boolean = false,
    val isMutating: Boolean = false,
    val showGuildPicker: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val selectedGuild: DiscordGuildResponse? = guilds.firstOrNull { it.id == selectedGuildId }
    val selectedChannel: DiscordVoiceChannelResponse? = voiceChannels.firstOrNull { it.id == selectedVoiceChannelId }
    val hasSelection: Boolean = selectedGuildId != null && selectedVoiceChannelId != null
}

/**
 * Narrow, independently observed slices of [PlayerUiState]. The mappers are
 * pure so the projections stay unit-testable without Android/Compose.
 */
data class PlayerScreenState(
    val guilds: List<DiscordGuildResponse> = emptyList(),
    val voiceChannels: List<DiscordVoiceChannelResponse> = emptyList(),
    val selectedGuildId: String? = null,
    val selectedVoiceChannelId: String? = null,
    val queue: QueueSnapshotResponse? = null,
    val isLoadingGuilds: Boolean = false,
    val isLoadingQueue: Boolean = false,
    val isMutating: Boolean = false,
    val showGuildPicker: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val selectedGuild: DiscordGuildResponse? = guilds.firstOrNull { it.id == selectedGuildId }
    val selectedChannel: DiscordVoiceChannelResponse? = voiceChannels.firstOrNull { it.id == selectedVoiceChannelId }
    val hasSelection: Boolean = selectedGuildId != null && selectedVoiceChannelId != null
}

data class AddTrackUiState(
    val searchQuery: String = "",
    val searchResults: List<SearchItemResponse> = emptyList(),
    val isSearching: Boolean = false,
    val isMutating: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

data class MiniPlayerState(
    val slide: NowPlayingSlide,
    val track: TrackResponse,
    val isMutating: Boolean,
)

data class PlayerPollingKeys(
    val guildId: String?,
    val playbackKey: String?,
)

fun PlayerUiState.toPlayerScreenState(): PlayerScreenState = PlayerScreenState(
    guilds = guilds,
    voiceChannels = voiceChannels,
    selectedGuildId = selectedGuildId,
    selectedVoiceChannelId = selectedVoiceChannelId,
    queue = queue,
    isLoadingGuilds = isLoadingGuilds,
    isLoadingQueue = isLoadingQueue,
    isMutating = isMutating,
    showGuildPicker = showGuildPicker,
    error = error,
    info = info,
)

fun PlayerUiState.toAddTrackUiState(): AddTrackUiState = AddTrackUiState(
    searchQuery = searchQuery,
    searchResults = searchResults,
    isSearching = isSearching,
    isMutating = isMutating,
    error = error,
    info = info,
)

fun PlayerUiState.toMiniPlayerState(): MiniPlayerState? {
    val queueSnapshot = queue ?: return null
    val track = queueSnapshot.nowPlaying ?: return null
    return MiniPlayerState(
        slide = nowPlayingSlide(queueSnapshot),
        track = track,
        isMutating = isMutating,
    )
}

fun PlayerUiState.toPollingKeys(): PlayerPollingKeys {
    val guildId = selectedGuildId ?: return PlayerPollingKeys(null, null)
    val snapshot = queue
    val track = snapshot?.nowPlaying
    return PlayerPollingKeys(
        guildId = guildId,
        playbackKey = track?.let { playbackIdentity(it, snapshot.nowPlayingStartedAt) },
    )
}

class PlayerViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val sessionManager = container.sessionManager
    private val sessionIdentity = checkNotNull(sessionManager.sessionIdentity.value)
    private val selection = container.selectionStore

    private val _ui = MutableStateFlow(
        PlayerUiState(
            selectedGuildId = selection.guildId,
            selectedVoiceChannelId = selection.voiceChannelId,
        ),
    )
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    /**
     * Narrow projections so collectors only recompose on their own slice:
     * typing in AddTrack search must not recompose the hidden Player screen,
     * the shell or the MiniPlayer, and polling keys must not carry the queue.
     */
    val playerScreenState: StateFlow<PlayerScreenState> = _ui
        .map { it.toPlayerScreenState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.toPlayerScreenState())

    val addTrackState: StateFlow<AddTrackUiState> = _ui
        .map { it.toAddTrackUiState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.toAddTrackUiState())

    val miniPlayerState: StateFlow<MiniPlayerState?> = _ui
        .map { it.toMiniPlayerState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.toMiniPlayerState())

    val playerError: StateFlow<String?> = _ui
        .map { it.error }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.error)

    val pollingKeys: StateFlow<PlayerPollingKeys> = _ui
        .map { it.toPollingKeys() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.toPollingKeys())

    private val _trackAdded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val trackAdded: SharedFlow<Unit> = _trackAdded.asSharedFlow()

    private var searchJob: Job? = null

    /**
     * Serializes all queue GETs (regular poll, expected-end refresh, explicit
     * refresh, conflict refetch) so two fetches never run in parallel and the
     * expected-end one-shot can't overlap the interval poll.
     */
    private val queueFetchMutex = Mutex()

    init {
        refreshGuilds()
    }

    fun setSearchQuery(query: String) {
        _ui.update { it.copy(searchQuery = query) }
    }

    fun setShowPicker(show: Boolean) {
        _ui.update { it.copy(showGuildPicker = show) }
    }

    fun dismissMessage() {
        _ui.update { it.copy(error = null, info = null) }
    }

    fun clearAddTrack() {
        searchJob?.cancel()
        _ui.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                error = null,
                info = null,
            )
        }
    }

    fun refreshGuilds() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoadingGuilds = true, error = null) }
            try {
                val guilds = sessionManager.withApiForSession(sessionIdentity) { it.getMyGuilds() }
                var selGuild = selection.guildId
                var selChannel = selection.voiceChannelId
                if (selGuild != null && guilds.none { g -> g.id == selGuild }) {
                    selGuild = null
                    selChannel = null
                    selection.guildId = null
                    selection.voiceChannelId = null
                }
                if (selGuild == null && guilds.size == 1) {
                    selGuild = guilds.first().id
                    selection.guildId = selGuild
                }
                _ui.update {
                    it.copy(
                        guilds = guilds,
                        selectedGuildId = selGuild,
                        selectedVoiceChannelId = selChannel,
                        isLoadingGuilds = false,
                    )
                }
                if (selGuild != null) {
                    refreshChannels(selGuild, preserveChannel = selChannel)
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isLoadingGuilds = false, error = userMessageForError(e)) }
            }
        }
    }

    fun selectGuild(guildId: String) {
        selection.guildId = guildId
        selection.voiceChannelId = null
        _ui.update {
            it.copy(
                selectedGuildId = guildId,
                selectedVoiceChannelId = null,
                queue = null,
                searchResults = emptyList(),
                showGuildPicker = false,
            )
        }
        refreshChannels(guildId, preserveChannel = null)
    }

    fun selectChannel(channelId: String) {
        selection.voiceChannelId = channelId
        _ui.update { it.copy(selectedVoiceChannelId = channelId, showGuildPicker = false, error = null) }
        _ui.value.selectedGuildId?.let { refreshQueue(it) }
    }

    fun refreshChannels(guildId: String, preserveChannel: String?) {
        viewModelScope.launch {
            try {
                val channels = sessionManager.withApiForSession(sessionIdentity) { it.getVoiceChannels(guildId) }
                var selChannel = preserveChannel
                if (selChannel != null && channels.none { c -> c.id == selChannel }) {
                    selChannel = null
                    selection.voiceChannelId = null
                }
                _ui.update {
                    it.copy(
                        voiceChannels = channels.sortedBy { c -> c.position },
                        selectedVoiceChannelId = selChannel,
                    )
                }
                refreshQueue(guildId)
            } catch (e: Exception) {
                _ui.update { it.copy(error = userMessageForError(e)) }
            }
        }
    }

    fun refreshQueue(guildId: String) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoadingQueue = true) }
            try {
                val snapshot = fetchQueueSnapshot(guildId)
                val applied = applyQueueSnapshot(snapshot)
                _ui.update {
                    it.copy(
                        isLoadingQueue = false,
                        error = if (applied) null else it.error,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isLoadingQueue = false, error = userMessageForError(e)) }
            }
        }
    }

    /**
     * Single queue fetch. Suspending — the Compose polling loop awaits it, so the
     * next tick only starts after the request completes (request → delay → request).
     * Silent on failure; explicit actions surface their own errors.
     */
    suspend fun pollQueueOnce() {
        val guildId = _ui.value.selectedGuildId ?: return
        try {
            applyQueueSnapshot(fetchQueueSnapshot(guildId))
        } catch (_: Exception) {
            // Silent on poll; errors surface on explicit actions.
        }
    }

    private suspend fun fetchQueueSnapshot(guildId: String): QueueSnapshotResponse =
        queueFetchMutex.withLock {
            sessionManager.withApiForSession(sessionIdentity) { it.getQueue(guildId) }
        }

    /**
     * Applies a snapshot unless it is stale: a snapshot of another guild, or with
     * a version older than the one already shown, never overwrites UI state.
     * Returns whether the snapshot was applied.
     */
    private fun applyQueueSnapshot(snapshot: QueueSnapshotResponse): Boolean {
        var applied = false
        _ui.update { current ->
            if (shouldApplyQueueSnapshot(current.queue, snapshot, current.selectedGuildId)) {
                applied = true
                current.copy(queue = snapshot)
            } else {
                current
            }
        }
        return applied
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _ui.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        if (looksLikeUrl(trimmed)) {
            _ui.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _ui.update { it.copy(isSearching = true, error = null) }
            try {
                val response = sessionManager.withApiForSession(sessionIdentity) {
                    it.search(query = trimmed, source = "YouTube", maxResults = 10)
                }
                _ui.update { it.copy(searchResults = response.items, isSearching = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(isSearching = false, error = userMessageForError(e)) }
            }
        }
    }

    fun submitSmartInput() {
        val input = _ui.value.searchQuery.trim()
        if (input.isBlank()) return
        if (looksLikeUrl(input)) {
            // Extract first URL if pasted with extra text.
            val url = URL_REGEX.find(input)?.value ?: input
            enqueueInputs(listOf(url))
        } else {
            search(input)
        }
    }

    fun enqueueSearchResult(item: SearchItemResponse) {
        enqueueInputs(listOf(item.input))
    }

    fun enqueueInputs(inputs: List<String>) {
        val guildId = _ui.value.selectedGuildId
        val channelId = _ui.value.selectedVoiceChannelId
        if (guildId == null || channelId == null) {
            _ui.update {
                it.copy(
                    showGuildPicker = true,
                    error = "Wybierz serwer i kanał głosowy, aby dodać utwór.",
                )
            }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, error = null, info = null) }
            try {
                val version = _ui.value.queue?.version
                val response = sessionManager.withApiForSession(sessionIdentity) {
                    it.enqueue(guildId, EnqueueRequest(channelId, inputs, version))
                }
                applyQueueSnapshot(response.snapshot)
                // Query/results stay intact so the modal exit animation renders stable
                // content; PlayerRoute clears them after the modal closes.
                _ui.update { it.copy(isMutating = false) }
                _trackAdded.tryEmit(Unit)
            } catch (e: Exception) {
                handleMutationError(e)
            }
        }
    }

    fun skip() {
        mutate { api, version ->
            val guildId = _ui.value.selectedGuildId ?: return@mutate null
            api.skip(guildId, SkipQueueRequest(expectedVersion = version))
        }
    }

    fun stop() {
        mutate { api, version ->
            val guildId = _ui.value.selectedGuildId ?: return@mutate null
            api.stop(guildId, QueueMutationRequest(expectedVersion = version))
        }
    }

    fun setRepeat(enabled: Boolean) {
        mutate { api, version ->
            val guildId = _ui.value.selectedGuildId ?: return@mutate null
            api.setRepeat(guildId, SetQueueRepeatRequest(enabled, version))
        }
    }

    fun toggleRadio() {
        val queue = _ui.value.queue ?: return
        when (val action = decideRadioToggle(queue, _ui.value.selectedVoiceChannelId)) {
            RadioToggleAction.MissingVoiceChannel -> _ui.update {
                it.copy(error = "Najpierw wybierz serwer i kanał głosowy.")
            }
            is RadioToggleAction.Disable -> mutate { api, _ ->
                val guildId = _ui.value.selectedGuildId ?: return@mutate null
                api.disableRadio(guildId, action.expectedVersion)
            }
            is RadioToggleAction.Enable -> {
                val guildId = _ui.value.selectedGuildId ?: run {
                    _ui.update { it.copy(error = "Najpierw wybierz serwer i kanał głosowy.") }
                    return
                }
                viewModelScope.launch {
                    _ui.update { it.copy(isMutating = true, error = null) }
                    try {
                        val response = sessionManager.withApiForSession(sessionIdentity) { api ->
                            api.enableRadio(guildId, action.request)
                        }
                        applyQueueSnapshot(response.snapshot)
                        _ui.update { it.copy(isMutating = false) }
                    } catch (e: Exception) {
                        handleMutationError(e)
                    }
                }
            }
        }
    }

    fun removeEntry(entryId: String) {
        viewModelScope.launch {
            val guildId = _ui.value.selectedGuildId ?: return@launch
            _ui.update { it.copy(isMutating = true, error = null) }
            try {
                val version = _ui.value.queue?.version
                val response = sessionManager.withApiForSession(sessionIdentity) {
                    it.removeQueueEntry(guildId, entryId, version)
                }
                applyQueueSnapshot(response.snapshot)
                _ui.update {
                    if (response.operation.succeeded) it.copy(isMutating = false)
                    else it.copy(isMutating = false, error = response.operation.message ?: "Nie udało się usunąć utworu z kolejki.")
                }
            } catch (e: Exception) {
                handleMutationError(e)
            }
        }
    }

    fun moveEntry(entryId: String, newPosition: Int, expectedVersion: Long) {
        val snapshot = _ui.value.queue ?: return
        if (_ui.value.isMutating || snapshot.pendingEntries.none { it.entryId == entryId } ||
            newPosition !in 1..snapshot.pendingEntries.size
        ) return
        viewModelScope.launch {
            val guildId = _ui.value.selectedGuildId ?: return@launch
            _ui.update { it.copy(isMutating = true, error = null) }
            try {
                val response = sessionManager.withApiForSession(sessionIdentity) {
                    it.moveQueueEntry(
                        guildId,
                        entryId,
                        MoveQueueEntryRequest(entryId, newPosition, expectedVersion),
                    )
                }
                applyQueueSnapshot(response.snapshot)
                _ui.update {
                    if (response.operation.succeeded) it.copy(isMutating = false)
                    else it.copy(isMutating = false, error = response.operation.message ?: "Nie udało się zmienić pozycji utworu.")
                }
            } catch (e: Exception) {
                handleMutationError(e)
            }
        }
    }

    fun clearQueue() {
        mutate { api, version ->
            val guildId = _ui.value.selectedGuildId ?: return@mutate null
            api.clearPendingQueue(guildId, version)
        }
    }

    private fun mutate(
        call: suspend (com.tryniecki.kajutabot.api.client.KajutaBotApi, Long?) -> com.tryniecki.kajutabot.api.model.queue.QueueMutationResponse?,
    ) {
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, error = null) }
            try {
                val version = _ui.value.queue?.version
                val response = sessionManager.withApiForSession(sessionIdentity) { call(it, version) } ?: run {
                    _ui.update { it.copy(isMutating = false) }
                    return@launch
                }
                applyQueueSnapshot(response.snapshot)
                _ui.update { it.copy(isMutating = false) }
            } catch (e: Exception) {
                handleMutationError(e)
            }
        }
    }

    private suspend fun handleMutationError(e: Exception) {
        if (e is HttpException) {
            val problem = try {
                KajutaBotApiErrors.problemDetailsOf(e)
            } catch (_: Exception) {
                null
            }
            if (problem?.errorCode == KajutaBotApiErrors.QUEUE_VERSION_CONFLICT || e.code() == 409) {
                val guildId = _ui.value.selectedGuildId
                if (guildId != null) {
                    try {
                        val fresh = fetchQueueSnapshot(guildId)
                        val applied = applyQueueSnapshot(fresh)
                        _ui.update {
                            it.copy(
                                isMutating = false,
                                error = if (applied) {
                                    "Kolejka zmieniła się w międzyczasie. Odświeżono stan."
                                } else {
                                    it.error
                                },
                            )
                        }
                        return
                    } catch (_: Exception) {
                    }
                }
            }
        }
        _ui.update { it.copy(isMutating = false, error = userMessageForError(e)) }
    }

    companion object {
        private val URL_REGEX = Regex("""https?://[^\s]+""")

        fun looksLikeUrl(input: String): Boolean =
            input.startsWith("http://", ignoreCase = true) ||
                input.startsWith("https://", ignoreCase = true)
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(container) as T
        }
    }
}
