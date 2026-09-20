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
import kotlinx.coroutines.delay
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

enum class GuildAccessState {
    CHECKING,
    AVAILABLE,
    NONE,
    ERROR,
}

enum class PlayerControlAction {
    STOP,
    SKIP,
    REPEAT,
    RADIO,
}

data class PlayerUiState(
    val guilds: List<DiscordGuildResponse> = emptyList(),
    val voiceChannels: List<DiscordVoiceChannelResponse> = emptyList(),
    val selectedGuildId: String? = null,
    val selectedVoiceChannelId: String? = null,
    val queue: QueueSnapshotResponse? = null,
    val presentedNowPlaying: NowPlayingPresentation? = null,
    val searchQuery: String = "",
    val searchResults: List<SearchItemResponse> = emptyList(),
    val isLoadingGuilds: Boolean = true,
    val isLoadingVoiceChannels: Boolean = false,
    val guildAccessState: GuildAccessState = GuildAccessState.CHECKING,
    val guildAccessError: String? = null,
    val isLoadingQueue: Boolean = false,
    val isSearching: Boolean = false,
    val isMutating: Boolean = false,
    val activeControlAction: PlayerControlAction? = null,
    val error: String? = null,
    val info: String? = null,
) {
    val selectedGuild: DiscordGuildResponse? = guilds.firstOrNull { it.id == selectedGuildId }
    val selectedChannel: DiscordVoiceChannelResponse? = voiceChannels.firstOrNull { it.id == selectedVoiceChannelId }
    val hasSelection: Boolean = selectedGuildId != null && selectedVoiceChannelId != null
    val effectiveNowPlaying: NowPlayingPresentation?
        get() = presentedNowPlaying ?: queue?.nowPlayingPresentationOrNull()
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
    val presentedNowPlaying: NowPlayingPresentation? = null,
    val isLoadingGuilds: Boolean = false,
    val isLoadingQueue: Boolean = false,
    val isMutating: Boolean = false,
    val activeControlAction: PlayerControlAction? = null,
    val error: String? = null,
    val info: String? = null,
) {
    val selectedGuild: DiscordGuildResponse? = guilds.firstOrNull { it.id == selectedGuildId }
    val selectedChannel: DiscordVoiceChannelResponse? = voiceChannels.firstOrNull { it.id == selectedVoiceChannelId }
    val hasSelection: Boolean = selectedGuildId != null && selectedVoiceChannelId != null
}


data class PlayerEntryState(
    val guilds: List<DiscordGuildResponse> = emptyList(),
    val voiceChannels: List<DiscordVoiceChannelResponse> = emptyList(),
    val selectedGuildId: String? = null,
    val selectedVoiceChannelId: String? = null,
    val isLoadingVoiceChannels: Boolean = false,
    val guildAccessState: GuildAccessState = GuildAccessState.CHECKING,
    val guildAccessError: String? = null,
)

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
    val activeControlAction: PlayerControlAction?,
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
    presentedNowPlaying = effectiveNowPlaying,
    isLoadingGuilds = isLoadingGuilds,
    isLoadingQueue = isLoadingQueue,
    isMutating = isMutating,
    activeControlAction = activeControlAction,
    error = error,
    info = info,
)


fun PlayerUiState.toPlayerEntryState(): PlayerEntryState = PlayerEntryState(
    guilds = guilds,
    voiceChannels = voiceChannels,
    selectedGuildId = selectedGuildId,
    selectedVoiceChannelId = selectedVoiceChannelId,
    isLoadingVoiceChannels = isLoadingVoiceChannels,
    guildAccessState = guildAccessState,
    guildAccessError = guildAccessError,
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
    val presentation = effectiveNowPlaying ?: return null
    return MiniPlayerState(
        slide = nowPlayingSlide(presentation, hasQueue = queue != null),
        track = presentation.track,
        isMutating = isMutating,
        activeControlAction = activeControlAction,
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

    val entryState: StateFlow<PlayerEntryState> = _ui
        .map { it.toPlayerEntryState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.toPlayerEntryState())

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

    private val _controlMessages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val controlMessages: SharedFlow<String> = _controlMessages.asSharedFlow()

    private var searchJob: Job? = null
    private var transitionRefreshJob: Job? = null
    private var presentationIdleJob: Job? = null

    /**
     * Serializes all queue GETs (regular poll, expected-end refresh, explicit
     * refresh, conflict refetch) so two fetches never run in parallel and the
     * expected-end one-shot can't overlap the interval poll.
     */
    private val queueFetchMutex = Mutex()

    /** Serializes remote player controls so versioned queue mutations never race. */
    private val controlMutationMutex = Mutex()
    private val pendingControlActions = mutableSetOf<PlayerControlAction>()

    init {
        refreshGuilds()
    }

    fun setSearchQuery(query: String) {
        _ui.update { it.copy(searchQuery = query) }
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
            _ui.update {
                it.copy(
                    isLoadingGuilds = true,
                    guildAccessState = GuildAccessState.CHECKING,
                    guildAccessError = null,
                )
            }
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
                        guildAccessState = if (guilds.isEmpty()) {
                            GuildAccessState.NONE
                        } else {
                            GuildAccessState.AVAILABLE
                        },
                        guildAccessError = null,
                    )
                }
                if (selGuild != null) {
                    refreshChannels(selGuild, preserveChannel = selChannel)
                }
            } catch (e: Exception) {
                val noGuildAccess = e is HttpException &&
                    e.code() == 403 &&
                    KajutaBotApiErrors.errorCodeOf(e) == "discord_guild_access_denied"
                _ui.update {
                    it.copy(
                        isLoadingGuilds = false,
                        guildAccessState = if (noGuildAccess) {
                            GuildAccessState.NONE
                        } else {
                            GuildAccessState.ERROR
                        },
                        guildAccessError = if (noGuildAccess) null else userMessageForError(e),
                    )
                }
            }
        }
    }

    fun selectGuild(guildId: String) {
        cancelPresentationRecovery()
        selection.guildId = guildId
        selection.voiceChannelId = null
        _ui.update {
            it.copy(
                selectedGuildId = guildId,
                selectedVoiceChannelId = null,
                queue = null,
                presentedNowPlaying = null,
                searchResults = emptyList(),
            )
        }
        refreshChannels(guildId, preserveChannel = null)
    }

    fun selectChannel(channelId: String) {
        selection.voiceChannelId = channelId
        _ui.update { it.copy(selectedVoiceChannelId = channelId, error = null) }
        _ui.value.selectedGuildId?.let { refreshQueue(it) }
    }

    fun refreshChannels(guildId: String, preserveChannel: String?) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoadingVoiceChannels = true, error = null) }
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
                        isLoadingVoiceChannels = false,
                    )
                }
                refreshQueue(guildId)
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        isLoadingVoiceChannels = false,
                        error = userMessageForError(e),
                    )
                }
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
    private fun applyQueueSnapshot(
        snapshot: QueueSnapshotResponse,
        forcePresentationIdle: Boolean = false,
    ): Boolean {
        var applied = false
        var holdsPreviousTrack = false
        var likelyTrackTransition = false
        _ui.update { current ->
            if (shouldApplyQueueSnapshot(current.queue, snapshot, current.selectedGuildId)) {
                applied = true
                val nextPresentation = when {
                    snapshot.nowPlaying != null -> snapshot.nowPlayingPresentationOrNull()
                    forcePresentationIdle -> null
                    current.effectiveNowPlaying != null -> {
                        holdsPreviousTrack = true
                        likelyTrackTransition =
                            current.queue?.pendingEntries?.isNotEmpty() == true ||
                            current.queue?.isRepeatEnabled == true ||
                            current.queue?.radio?.isEnabled == true ||
                            snapshot.pendingEntries.isNotEmpty() ||
                            snapshot.isRepeatEnabled ||
                            snapshot.radio.isEnabled
                        current.effectiveNowPlaying
                    }
                    else -> null
                }
                current.copy(
                    queue = snapshot,
                    presentedNowPlaying = nextPresentation,
                )
            } else {
                current
            }
        }
        if (applied) {
            if (holdsPreviousTrack) {
                schedulePresentationRecovery(
                    guildId = snapshot.guildId,
                    likelyTrackTransition = likelyTrackTransition,
                )
            } else {
                cancelPresentationRecovery()
            }
        }
        return applied
    }

    private fun schedulePresentationRecovery(
        guildId: String,
        likelyTrackTransition: Boolean,
    ) {
        if (transitionRefreshJob?.isActive != true) {
            transitionRefreshJob = viewModelScope.launch {
                val delays = if (likelyTrackTransition) {
                    TRACK_TRANSITION_REFRESH_DELAYS_MS
                } else {
                    IDLE_CONFIRM_REFRESH_DELAYS_MS
                }
                for (waitMs in delays) {
                    delay(waitMs)
                    if (!isAwaitingNextTrack(guildId)) return@launch
                    pollQueueOnce()
                    if (!isAwaitingNextTrack(guildId)) return@launch
                }
            }
        }

        if (presentationIdleJob?.isActive != true) {
            presentationIdleJob = viewModelScope.launch {
                delay(
                    if (likelyTrackTransition) {
                        TRACK_TRANSITION_GRACE_MS
                    } else {
                        PRESENTATION_IDLE_GRACE_MS
                    },
                )
                _ui.update { current ->
                    if (
                        current.selectedGuildId == guildId &&
                        current.queue?.nowPlaying == null &&
                        current.presentedNowPlaying != null
                    ) {
                        current.copy(presentedNowPlaying = null)
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun isAwaitingNextTrack(guildId: String): Boolean {
        val current = _ui.value
        return current.selectedGuildId == guildId &&
            current.queue?.nowPlaying == null &&
            current.presentedNowPlaying != null
    }

    private fun cancelPresentationRecovery() {
        transitionRefreshJob?.cancel()
        transitionRefreshJob = null
        presentationIdleJob?.cancel()
        presentationIdleJob = null
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
                    error = "Wybierz serwer i kanał głosowy na ekranie odtwarzacza, aby dodać utwór.",
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
                // Query/results stay intact so the AddTrack exit transition renders stable
                // content; the shell clears them after the AddTrack route closes.
                _ui.update { it.copy(isMutating = false) }
                _trackAdded.tryEmit(Unit)
            } catch (e: Exception) {
                handleMutationError(e)
            }
        }
    }

    fun skip() {
        mutate(controlAction = PlayerControlAction.SKIP) { api, version ->
            val guildId = _ui.value.selectedGuildId ?: return@mutate null
            api.skip(guildId, SkipQueueRequest(expectedVersion = version))
        }
    }

    fun stop() {
        mutate(
            controlAction = PlayerControlAction.STOP,
            forcePresentationIdleOnSuccess = true,
        ) { api, version ->
            val guildId = _ui.value.selectedGuildId ?: return@mutate null
            api.stop(guildId, QueueMutationRequest(expectedVersion = version))
        }
    }

    fun setRepeat(enabled: Boolean) {
        mutate(
            controlAction = PlayerControlAction.REPEAT,
            successMessage = { response ->
                if (response.snapshot.isRepeatEnabled) "Powtarzanie włączone" else "Powtarzanie wyłączone"
            },
        ) { api, version ->
            val guildId = _ui.value.selectedGuildId ?: return@mutate null
            api.setRepeat(guildId, SetQueueRepeatRequest(enabled, version))
        }
    }

    fun toggleRadio() {
        mutate(
            controlAction = PlayerControlAction.RADIO,
            successMessage = { response ->
                if (response.snapshot.radio?.isEnabled == true) "Radio włączone" else "Radio wyłączone"
            },
        ) { api, _ ->
            val queue = _ui.value.queue ?: return@mutate null
            val guildId = _ui.value.selectedGuildId ?: return@mutate null
            when (val action = decideRadioToggle(queue, _ui.value.selectedVoiceChannelId)) {
                RadioToggleAction.MissingVoiceChannel -> {
                    _ui.update { it.copy(error = "Najpierw wybierz serwer i kanał głosowy.") }
                    null
                }
                is RadioToggleAction.Disable -> api.disableRadio(guildId, action.expectedVersion)
                is RadioToggleAction.Enable -> api.enableRadio(guildId, action.request)
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
        controlAction: PlayerControlAction? = null,
        forcePresentationIdleOnSuccess: Boolean = false,
        successMessage: ((com.tryniecki.kajutabot.api.model.queue.QueueMutationResponse) -> String?)? = null,
        call: suspend (com.tryniecki.kajutabot.api.client.KajutaBotApi, Long?) -> com.tryniecki.kajutabot.api.model.queue.QueueMutationResponse?,
    ) {
        if (controlAction != null) {
            val accepted = synchronized(pendingControlActions) {
                pendingControlActions.add(controlAction)
            }
            if (!accepted) return
        }

        viewModelScope.launch {
            try {
                if (controlAction != null) {
                    controlMutationMutex.withLock {
                        performMutation(controlAction, forcePresentationIdleOnSuccess, successMessage, call)
                    }
                } else {
                    performMutation(null, forcePresentationIdleOnSuccess, successMessage, call)
                }
            } finally {
                if (controlAction != null) {
                    synchronized(pendingControlActions) {
                        pendingControlActions.remove(controlAction)
                    }
                }
            }
        }
    }

    private suspend fun performMutation(
        controlAction: PlayerControlAction?,
        forcePresentationIdleOnSuccess: Boolean,
        successMessage: ((com.tryniecki.kajutabot.api.model.queue.QueueMutationResponse) -> String?)?,
        call: suspend (com.tryniecki.kajutabot.api.client.KajutaBotApi, Long?) -> com.tryniecki.kajutabot.api.model.queue.QueueMutationResponse?,
    ) {
        _ui.update {
            it.copy(
                isMutating = true,
                activeControlAction = controlAction,
                error = null,
            )
        }
        try {
            val version = _ui.value.queue?.version
            val response = sessionManager.withApiForSession(sessionIdentity) { call(it, version) } ?: run {
                _ui.update { it.copy(isMutating = false, activeControlAction = null) }
                return
            }
            applyQueueSnapshot(
                response.snapshot,
                forcePresentationIdle = forcePresentationIdleOnSuccess,
            )
            _ui.update { it.copy(isMutating = false, activeControlAction = null) }
            successMessage?.invoke(response)?.let(_controlMessages::tryEmit)
        } catch (e: Exception) {
            handleMutationError(e)
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
                                activeControlAction = null,
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
        _ui.update {
            it.copy(
                isMutating = false,
                activeControlAction = null,
                error = userMessageForError(e),
            )
        }
    }

    companion object {
        private val URL_REGEX = Regex("""https?://[^\s]+""")
        private val TRACK_TRANSITION_REFRESH_DELAYS_MS = longArrayOf(80L, 180L, 350L, 700L)
        private val IDLE_CONFIRM_REFRESH_DELAYS_MS = longArrayOf(120L, 300L)
        private const val TRACK_TRANSITION_GRACE_MS = 2_000L
        private const val PRESENTATION_IDLE_GRACE_MS = 900L

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
