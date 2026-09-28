package com.tryniecki.kajutabot.ui.player

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.api.client.KajutaBotApiErrors
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.api.model.queue.EnqueueRequest
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.search.SearchItemResponse
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.data.preferences.UserPreferencesRepository
import com.tryniecki.kajutabot.data.repository.PlayerRepository
import com.tryniecki.kajutabot.ui.userMessageForError
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.ui.text.uiText
import com.tryniecki.kajutabot.ui.components.SwipeActionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
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

internal fun preferredVoiceChannelId(
    channels: List<DiscordVoiceChannelResponse>,
    savedChannelId: String?,
): String? = savedChannelId?.takeIf { id -> channels.any { it.id == id } }
    ?: channels.singleOrNull()?.id

enum class PlayerControlAction {
    STOP,
    SKIP,
    REPEAT,
    RADIO,
    REQUEUE,
}

internal fun shouldBlockPlaybackControls(
    isMutating: Boolean,
    activeControlAction: PlayerControlAction?,
    isQueueReordering: Boolean,
): Boolean = false

enum class QueueLoadState {
    IDLE,
    LOADING,
    READY,
    ERROR,
}

enum class SearchSourceOption(
    val apiValue: String,
    @StringRes val displayNameResId: Int,
) {
    YOUTUBE("YouTube", R.string.search_source_youtube),
    SOUNDCLOUD("SoundCloud", R.string.search_source_soundcloud),
    DATABASE("Database", R.string.search_source_database),
}

data class PlayerUiState(
    val guilds: List<DiscordGuildResponse> = emptyList(),
    val voiceChannels: List<DiscordVoiceChannelResponse> = emptyList(),
    val selectedGuildId: String? = null,
    val selectedVoiceChannelId: String? = null,
    val queue: QueueSnapshotResponse? = null,
    val queueObservedAtElapsedRealtimeMs: Long? = null,
    val transitionNowPlaying: NowPlayingPresentation? = null,
    val transitionStartedAtNanos: Long? = null,
    val searchQuery: String = "",
    val searchSource: SearchSourceOption = SearchSourceOption.YOUTUBE,
    val searchResults: List<SearchItemResponse> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val lastCompletedSearchQuery: String? = null,
    val isLoadingGuilds: Boolean = true,
    val isLoadingVoiceChannels: Boolean = false,
    val guildAccessState: GuildAccessState = GuildAccessState.CHECKING,
    val guildAccessError: UiText? = null,
    val isLoadingQueue: Boolean = false,
    val queueLoadState: QueueLoadState = QueueLoadState.IDLE,
    val queueLoadError: UiText? = null,
    val isSearching: Boolean = false,
    val isMutating: Boolean = false,
    val isEnqueuing: Boolean = false,
    val mutatingEntryIds: Set<String> = emptySet(),
    val removeStatuses: Map<String, SwipeActionStatus> = emptyMap(),
    val requeueStatuses: Map<String, SwipeActionStatus> = emptyMap(),
    val isQueueReordering: Boolean = false,
    val activeControlAction: PlayerControlAction? = null,
    val error: UiText? = null,
    val info: UiText? = null,
    val searchEnqueueCompleted: Boolean = false,
) {
    val selectedGuild: DiscordGuildResponse? = guilds.firstOrNull { it.id == selectedGuildId }
    val selectedChannel: DiscordVoiceChannelResponse? = voiceChannels.firstOrNull { it.id == selectedVoiceChannelId }
    val hasSelection: Boolean = selectedGuildId != null && selectedVoiceChannelId != null
    val effectiveNowPlaying: NowPlayingPresentation?
        get() = queue?.nowPlayingPresentationOrNull() ?: transitionNowPlaying
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
    val queueObservedAtElapsedRealtimeMs: Long? = null,
    val presentedNowPlaying: NowPlayingPresentation? = null,
    val isLoadingGuilds: Boolean = false,
    val isLoadingVoiceChannels: Boolean = false,
    val isLoadingQueue: Boolean = false,
    val queueLoadState: QueueLoadState = QueueLoadState.IDLE,
    val queueLoadError: UiText? = null,
    val isMutating: Boolean = false,
    val mutatingEntryIds: Set<String> = emptySet(),
    val removeStatuses: Map<String, SwipeActionStatus> = emptyMap(),
    val requeueStatuses: Map<String, SwipeActionStatus> = emptyMap(),
    val isQueueReordering: Boolean = false,
    val activeControlAction: PlayerControlAction? = null,
    val error: UiText? = null,
    val info: UiText? = null,
) {
    val selectedGuild: DiscordGuildResponse? = guilds.firstOrNull { it.id == selectedGuildId }
    val selectedChannel: DiscordVoiceChannelResponse? = voiceChannels.firstOrNull { it.id == selectedVoiceChannelId }
    val hasSelection: Boolean = selectedGuildId != null && selectedVoiceChannelId != null
    val needsVoiceChannelSelection: Boolean
        get() = selectedGuildId != null && selectedVoiceChannelId == null &&
            !isLoadingGuilds && !isLoadingVoiceChannels
    val isInitialContentLoading: Boolean
        get() = isLoadingGuilds || (selectedGuildId != null && queue == null)
}


data class PlayerEntryState(
    val guilds: List<DiscordGuildResponse> = emptyList(),
    val voiceChannels: List<DiscordVoiceChannelResponse> = emptyList(),
    val selectedGuildId: String? = null,
    val selectedVoiceChannelId: String? = null,
    val isLoadingVoiceChannels: Boolean = false,
    val guildAccessState: GuildAccessState = GuildAccessState.CHECKING,
    val guildAccessError: UiText? = null,
)

data class SearchUiState(
    val searchQuery: String = "",
    val searchSource: SearchSourceOption = SearchSourceOption.YOUTUBE,
    val searchResults: List<SearchItemResponse> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val lastCompletedSearchQuery: String? = null,
    val isSearching: Boolean = false,
    val isMutating: Boolean = false,
    val error: UiText? = null,
    val info: UiText? = null,
    val searchEnqueueCompleted: Boolean = false,
)

sealed interface SharedEnqueueStatus {
    data object Loading : SharedEnqueueStatus
    data class Added(val tracks: List<PlaybackTrackResponse>) : SharedEnqueueStatus
    data class Failed(val message: UiText) : SharedEnqueueStatus
}

data class SharedEnqueueUiState(
    val requestId: Long? = null,
    val status: SharedEnqueueStatus? = null,
)

data class MiniPlayerState(
    val slide: NowPlayingSlide,
    val track: PlaybackTrackResponse,
    val isMutating: Boolean,
    val isQueueReordering: Boolean,
    val activeControlAction: PlayerControlAction?,
)

fun PlayerUiState.toPlayerScreenState(): PlayerScreenState = PlayerScreenState(
    guilds = guilds,
    voiceChannels = voiceChannels,
    selectedGuildId = selectedGuildId,
    selectedVoiceChannelId = selectedVoiceChannelId,
    queue = queue,
    queueObservedAtElapsedRealtimeMs = queueObservedAtElapsedRealtimeMs,
    presentedNowPlaying = effectiveNowPlaying,
    isLoadingGuilds = isLoadingGuilds,
    isLoadingVoiceChannels = isLoadingVoiceChannels,
    isLoadingQueue = isLoadingQueue,
    queueLoadState = queueLoadState,
    queueLoadError = queueLoadError,
    isMutating = isMutating,
    mutatingEntryIds = mutatingEntryIds,
    removeStatuses = removeStatuses,
    requeueStatuses = requeueStatuses,
    isQueueReordering = isQueueReordering,
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

fun PlayerUiState.toSearchUiState(): SearchUiState = SearchUiState(
    searchQuery = searchQuery,
    searchSource = searchSource,
    searchResults = searchResults,
    searchHistory = searchHistory,
    lastCompletedSearchQuery = lastCompletedSearchQuery,
    isSearching = isSearching,
    isMutating = isEnqueuing,
    error = error,
    info = info,
    searchEnqueueCompleted = searchEnqueueCompleted,
)

fun PlayerUiState.toMiniPlayerState(): MiniPlayerState? {
    val presentation = effectiveNowPlaying ?: return null
    return MiniPlayerState(
        slide = nowPlayingSlide(
            presentation, hasQueue = queue != null,
            positionObservedAtElapsedRealtimeMs = queueObservedAtElapsedRealtimeMs,
        ),
        track = presentation.track,
        isMutating = isMutating,
        isQueueReordering = isQueueReordering,
        activeControlAction = activeControlAction,
    )
}

class PlayerViewModel(
    private val repository: PlayerRepository,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    private val sessionIdentity = checkNotNull(repository.sessionIdentity.value)

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()
    private val _sharedEnqueue = MutableStateFlow(SharedEnqueueUiState())
    val sharedEnqueueState: StateFlow<SharedEnqueueUiState> = _sharedEnqueue.asStateFlow()
    private val realtimeGuildId = _ui.map { it.selectedGuildId }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.selectedGuildId)
    private val realtime = PlayerRealtime(
        scope = viewModelScope,
        expectedIdentity = sessionIdentity,
        sessionIdentity = repository.sessionIdentity,
        guildId = realtimeGuildId,
        token = { repository.accessToken(sessionIdentity) },
        connect = repository::createRealtimeClient,
        onSnapshot = { snapshot -> applyQueueSnapshot(snapshot) },
        recoverQueue = ::recoverQueueOnce,
        elapsedRealtimeMs = SystemClock::elapsedRealtime,
    )
    val realtimeDiagnostics: StateFlow<RealtimeDiagnostics> = realtime.diagnostics

    /**
     * Narrow projections so collectors only recompose on their own slice:
     * typing on the Search screen must not recompose the hidden Player screen,
     * the shell or the MiniPlayer.
     */

    val entryState: StateFlow<PlayerEntryState> = _ui
        .map { it.toPlayerEntryState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.toPlayerEntryState())

    val playerScreenState: StateFlow<PlayerScreenState> = _ui
        .map { it.toPlayerScreenState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.toPlayerScreenState())

    val searchState: StateFlow<SearchUiState> = _ui
        .map { it.toSearchUiState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.toSearchUiState())

    val miniPlayerState: StateFlow<MiniPlayerState?> = _ui
        .map { it.toMiniPlayerState() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.toMiniPlayerState())

    /**
     * Authoritative remote playback state from the latest queue snapshot.
     * Unlike [miniPlayerState], this intentionally ignores the short-lived
     * presentation hold used to smooth track transitions in the app UI.
     */
    val remotePlaybackActive: StateFlow<Boolean> = _ui
        .map { it.queue?.nowPlaying != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.queue?.nowPlaying != null)

    val playerError: StateFlow<UiText?> = _ui
        .map { it.error ?: it.queueLoadError }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, _ui.value.error ?: _ui.value.queueLoadError)


    private var searchJob: Job? = null

    /**
     * Serializes recovery, explicit refresh and conflict refetch GETs.
     */
    private val queueFetchMutex = Mutex()

    private val pendingControlActions = mutableSetOf<PlayerControlAction>()


    init {
        viewModelScope.launch {
            repository.apiSnapshots.collect { applyQueueSnapshot(it) }
        }
        viewModelScope.launch {
            val selection = preferencesRepository.currentGuildSelection()
            val history = preferencesRepository.searchHistory.first()
            _ui.update {
                it.copy(
                    selectedGuildId = selection.guildId,
                    selectedVoiceChannelId = selection.voiceChannelId,
                    searchHistory = history,
                )
            }
            refreshGuilds()
        }
    }

    fun setRealtimeOwner(owner: RealtimeOwner, active: Boolean) = realtime.setOwner(owner, active)

    override fun onCleared() {
        realtime.close()
    }

    fun setSearchQuery(query: String) {
        if (query == _ui.value.searchQuery) return
        searchJob?.cancel()
        _ui.update {
            it.copy(
                searchQuery = query,
                searchResults = emptyList(),
                lastCompletedSearchQuery = null,
                isSearching = false,
            )
        }
    }

    fun setSearchSource(source: SearchSourceOption) {
        if (source == _ui.value.searchSource) return
        searchJob?.cancel()
        _ui.update {
            it.copy(
                searchSource = source,
                searchResults = emptyList(),
                lastCompletedSearchQuery = null,
                isSearching = false,
                error = null,
            )
        }
    }

    fun dismissMessage() {
        _ui.update { it.copy(error = null, info = null) }
    }

    fun acknowledgeSearchEnqueueCompleted() {
        _ui.update { it.copy(searchEnqueueCompleted = false) }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _ui.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                lastCompletedSearchQuery = null,
                isSearching = false,
                error = null,
                info = null,
                searchEnqueueCompleted = false,
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
                val guilds = repository.getGuilds(sessionIdentity)
                var selGuild = _ui.value.selectedGuildId
                var selChannel = _ui.value.selectedVoiceChannelId
                if (selGuild != null && guilds.none { g -> g.id == selGuild }) {
                    selGuild = null
                    selChannel = null
                    preferencesRepository.setGuildSelection(null, null)
                }
                if (selGuild == null && guilds.size == 1) {
                    selGuild = guilds.first().id
                    preferencesRepository.setGuildId(selGuild)
                }
                _ui.update {
                    val keepsCurrentQueue = it.selectedGuildId == selGuild && it.queue != null
                    it.copy(
                        guilds = guilds,
                        selectedGuildId = selGuild,
                        selectedVoiceChannelId = selChannel,
                        queue = if (it.selectedGuildId == selGuild) it.queue else null,
                        queueObservedAtElapsedRealtimeMs = if (it.selectedGuildId == selGuild) it.queueObservedAtElapsedRealtimeMs else null,
                        transitionNowPlaying = if (it.selectedGuildId == selGuild) it.transitionNowPlaying else null,
                        transitionStartedAtNanos = if (it.selectedGuildId == selGuild) it.transitionStartedAtNanos else null,
                        isLoadingGuilds = false,
                        isLoadingVoiceChannels = selGuild != null,
                        isLoadingQueue = selGuild != null && !keepsCurrentQueue,
                        queueLoadState = when {
                            selGuild == null -> QueueLoadState.IDLE
                            keepsCurrentQueue -> QueueLoadState.READY
                            else -> QueueLoadState.LOADING
                        },
                        queueLoadError = null,
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
                        isLoadingVoiceChannels = false,
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
        _ui.update {
            it.copy(
                selectedGuildId = guildId,
                selectedVoiceChannelId = null,
                voiceChannels = emptyList(),
                isLoadingVoiceChannels = true,
                queue = null,
                queueObservedAtElapsedRealtimeMs = null,
                transitionNowPlaying = null,
                transitionStartedAtNanos = null,
                isLoadingQueue = true,
                queueLoadState = QueueLoadState.LOADING,
                queueLoadError = null,
                searchResults = emptyList(),
            )
        }
        viewModelScope.launch {
            preferencesRepository.setGuildSelection(guildId, null)
            if (_ui.value.selectedGuildId == guildId) refreshChannels(guildId, preserveChannel = null)
        }
    }

    fun selectChannel(channelId: String) {
        viewModelScope.launch { preferencesRepository.setVoiceChannelId(channelId) }
        _ui.update { it.copy(selectedVoiceChannelId = channelId, error = null) }
    }

    fun refreshChannels(guildId: String, preserveChannel: String?) {
        viewModelScope.launch {
            if (_ui.value.selectedGuildId != guildId) return@launch
            _ui.update { it.copy(isLoadingVoiceChannels = true, error = null) }
            try {
                val channels = repository.getVoiceChannels(sessionIdentity, guildId)
                if (_ui.value.selectedGuildId != guildId) return@launch
                val selChannel = preferredVoiceChannelId(channels, preserveChannel)
                if (selChannel != preserveChannel) {
                    preferencesRepository.setVoiceChannelId(selChannel)
                }
                _ui.update {
                    if (it.selectedGuildId != guildId) it else it.copy(
                        voiceChannels = channels.sortedBy { c -> c.position },
                        selectedVoiceChannelId = selChannel,
                        isLoadingVoiceChannels = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    if (it.selectedGuildId != guildId) it else it.copy(
                        isLoadingVoiceChannels = false,
                        error = userMessageForError(e),
                    )
                }
            }
        }
    }

    fun refreshQueue(guildId: String) {
        viewModelScope.launch {
            if (_ui.value.selectedGuildId != guildId) return@launch
            _ui.update {
                it.copy(
                    isLoadingQueue = true,
                    queueLoadState = if (it.queue == null) QueueLoadState.LOADING else QueueLoadState.READY,
                    queueLoadError = null,
                )
            }
            try {
                val snapshot = fetchQueueSnapshot(guildId)
                val applied = applyQueueSnapshot(snapshot)
                _ui.update {
                    if (it.selectedGuildId != guildId) it else it.copy(
                        isLoadingQueue = false,
                        error = if (applied) null else it.error,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    if (it.selectedGuildId != guildId) it
                    else if (it.queue == null) {
                        it.copy(
                            isLoadingQueue = false,
                            queueLoadState = QueueLoadState.ERROR,
                            queueLoadError = userMessageForError(e),
                        )
                    } else {
                        it.copy(isLoadingQueue = false, queueLoadState = QueueLoadState.READY, error = userMessageForError(e))
                    }
                }
            }
        }
    }

    /** One silent REST recovery when the hub cannot supply a concrete snapshot. */
    private suspend fun recoverQueueOnce(guildId: String) {
        if (_ui.value.selectedGuildId != guildId || repository.sessionIdentity.value != sessionIdentity) return
        try {
            applyQueueSnapshot(fetchQueueSnapshot(guildId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.update { current ->
                if (current.selectedGuildId != guildId || current.queue != null) current
                else current.copy(
                    isLoadingQueue = false,
                    queueLoadState = QueueLoadState.ERROR,
                    queueLoadError = userMessageForError(e),
                )
            }
        }
    }

    private suspend fun fetchQueueSnapshot(guildId: String): QueueSnapshotResponse =
        queueFetchMutex.withLock {
            repository.getQueue(sessionIdentity, guildId)
        }

    /**
     * Applies a snapshot unless it is stale: a snapshot of another guild, or with
     * a version older than the one already shown, never overwrites UI state.
     * Returns whether the snapshot was applied.
     */
    private fun applyQueueSnapshot(
        snapshot: QueueSnapshotResponse,
    ): Boolean {
        repository.observe(snapshot)
        // Enqueue-only result metadata is not part of the shared playback state.
        val playbackSnapshot = if (snapshot.addedTracks == null) snapshot else snapshot.copy(addedTracks = null)
        val observedAtElapsedRealtimeMs = SystemClock.elapsedRealtime()
        val transitionToken = SystemClock.elapsedRealtimeNanos()
        var applied = false
        var startedTransitionToken: Long? = null
        _ui.update { current ->
            if (shouldApplyQueueSnapshot(current.queue, playbackSnapshot, current.selectedGuildId)) {
                if (current.queue == playbackSnapshot) {
                    applied = true
                    return@update current.copy(
                        isLoadingQueue = false,
                        queueLoadState = QueueLoadState.READY,
                        queueLoadError = null,
                    )
                }
                val holdPrevious = shouldHoldNowPlayingForTransition(playbackSnapshot, current.effectiveNowPlaying)
                startedTransitionToken = if (holdPrevious && current.transitionStartedAtNanos == null) {
                    transitionToken
                } else null
                applied = true
                current.copy(
                    queue = playbackSnapshot,
                    queueObservedAtElapsedRealtimeMs = if (holdPrevious) {
                        current.queueObservedAtElapsedRealtimeMs
                    } else {
                        observedAtElapsedRealtimeMs
                    },
                    transitionNowPlaying = if (holdPrevious) current.effectiveNowPlaying else null,
                    transitionStartedAtNanos = if (holdPrevious) {
                        current.transitionStartedAtNanos ?: transitionToken
                    } else {
                        null
                    },
                    isLoadingQueue = false,
                    queueLoadState = QueueLoadState.READY,
                    queueLoadError = null,
                )
            } else {
                current
            }
        }
        val startedToken = startedTransitionToken
        val guildId = _ui.value.selectedGuildId
        if (applied && startedToken != null && guildId != null) {
            viewModelScope.launch {
                delay(8_000)
                if (_ui.value.selectedGuildId != guildId || _ui.value.transitionStartedAtNanos != startedToken) {
                    return@launch
                }
                try {
                    applyQueueSnapshot(fetchQueueSnapshot(guildId))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A failed recovery must not leave the old track visible indefinitely.
                }
                _ui.update { current ->
                    if (current.selectedGuildId == guildId && current.transitionStartedAtNanos == startedToken) {
                        current.copy(transitionNowPlaying = null, transitionStartedAtNanos = null)
                    } else current
                }
            }
        }
        return applied
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || looksLikeUrl(trimmed)) {
            searchJob?.cancel()
            _ui.update {
                it.copy(
                    searchResults = emptyList(),
                    lastCompletedSearchQuery = null,
                    isSearching = false,
                )
            }
            return
        }

        searchJob?.cancel()
        val source = _ui.value.searchSource
        searchJob = viewModelScope.launch {
            _ui.update {
                it.copy(
                    searchResults = emptyList(),
                    lastCompletedSearchQuery = null,
                    isSearching = true,
                    error = null,
                )
            }
            try {
                val updatedHistory = preferencesRepository.addSearchHistory(trimmed)
                _ui.update { current ->
                    if (current.searchQuery.trim() == trimmed && current.searchSource == source) {
                        current.copy(searchHistory = updatedHistory)
                    } else {
                        current
                    }
                }
                val response = repository.search(
                    expectedIdentity = sessionIdentity,
                    query = trimmed,
                    source = source.apiValue,
                    maxResults = 10,
                )
                _ui.update { current ->
                    if (current.searchQuery.trim() != trimmed || current.searchSource != source) current
                    else current.copy(
                        searchResults = response.items,
                        lastCompletedSearchQuery = trimmed,
                        isSearching = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { current ->
                    if (current.searchQuery.trim() != trimmed || current.searchSource != source) current
                    else current.copy(
                        isSearching = false,
                        lastCompletedSearchQuery = null,
                        error = userMessageForError(e),
                    )
                }
            }
        }
    }

    fun searchFromHistory(query: String) {
        setSearchQuery(query)
        search(query)
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

    fun enqueueSharedUrl(requestId: Long, url: String) {
        if (_sharedEnqueue.value.requestId == requestId) return
        _sharedEnqueue.value = SharedEnqueueUiState(requestId, SharedEnqueueStatus.Loading)
        viewModelScope.launch {
            val state = _ui.value
            val guildId = state.selectedGuildId
            val channelId = state.selectedVoiceChannelId
            if (guildId == null || channelId == null) {
                _sharedEnqueue.update { current ->
                    if (current.requestId == requestId) {
                        current.copy(status = SharedEnqueueStatus.Failed(uiText(R.string.player_select_target_to_add)))
                    } else current
                }
                return@launch
            }

            try {
                val response = repository.enqueue(
                    expectedIdentity = sessionIdentity,
                    guildId = guildId,
                    request = EnqueueRequest(channelId, listOf(url)),
                )
                applyQueueSnapshot(response)
                val addedTracks = response.addedTracks.orEmpty()
                _sharedEnqueue.update { current ->
                    if (current.requestId == requestId) {
                        current.copy(status = SharedEnqueueStatus.Added(addedTracks))
                    } else current
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _sharedEnqueue.update { current ->
                    if (current.requestId == requestId) {
                        current.copy(status = SharedEnqueueStatus.Failed(userMessageForError(e)))
                    } else current
                }
            }
        }
    }

    fun enqueueInputs(inputs: List<String>) {
        if (_ui.value.isEnqueuing) return
        val guildId = _ui.value.selectedGuildId
        val channelId = _ui.value.selectedVoiceChannelId
        if (guildId == null || channelId == null) {
            _ui.update { it.copy(error = uiText(R.string.player_select_target_to_add)) }
            return
        }
        _ui.update { it.copy(isEnqueuing = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                val response = repository.enqueue(
                    expectedIdentity = sessionIdentity,
                    guildId = guildId,
                    request = EnqueueRequest(channelId, inputs),
                )
                applyQueueSnapshot(response)
                // Query/results stay intact so the Search exit transition renders stable
                // content; the shell clears them after the Search route closes.
                _ui.update { it.copy(isEnqueuing = false) }
                _ui.update { it.copy(searchEnqueueCompleted = true) }
            } catch (e: Exception) {
                _ui.update { it.copy(isEnqueuing = false) }
                handleMutationError(e)
            }
        }
    }

    fun skip() {
        mutate(controlAction = PlayerControlAction.SKIP) {
            val guildId = _ui.value.selectedGuildId ?: return@mutate null
            repository.skip(sessionIdentity, guildId)
        }
    }

    fun requeueNowPlaying() {
        val state = _ui.value
        val trackUrl = state.effectiveNowPlaying?.track?.url?.takeIf { it.isNotBlank() } ?: return
        val guildId = state.selectedGuildId ?: return
        val channelId = state.queue?.voiceChannelId ?: state.selectedVoiceChannelId ?: return
        mutate(controlAction = PlayerControlAction.REQUEUE) {
            repository.enqueue(
                expectedIdentity = sessionIdentity,
                guildId = guildId,
                request = EnqueueRequest(channelId, listOf(trackUrl)),
            )
        }
    }

    fun requeueEntry(entryId: String) {
        val state = _ui.value
        val queue = state.queue ?: return
        val trackUrl = queue.pendingEntries.firstOrNull { it.entryId == entryId }
            ?.track?.url?.takeIf { it.isNotBlank() } ?: return
        val guildId = state.selectedGuildId ?: return
        val channelId = queue.voiceChannelId ?: state.selectedVoiceChannelId ?: return
        if (state.requeueStatuses[entryId] == SwipeActionStatus.PENDING) return
        _ui.update { it.copy(requeueStatuses = it.requeueStatuses + (entryId to SwipeActionStatus.PENDING)) }
        viewModelScope.launch {
            try {
                applyQueueSnapshot(repository.enqueue(sessionIdentity, guildId, EnqueueRequest(channelId, listOf(trackUrl))))
                _ui.update { it.copy(requeueStatuses = it.requeueStatuses + (entryId to SwipeActionStatus.SUCCESS)) }
            } catch (e: Exception) {
                handleMutationError(e)
                _ui.update { it.copy(requeueStatuses = it.requeueStatuses + (entryId to SwipeActionStatus.FAILURE)) }
            }
            delay(1800)
            _ui.update { it.copy(requeueStatuses = it.requeueStatuses - entryId) }
        }
    }

    fun stop() {
        mutate(controlAction = PlayerControlAction.STOP) {
            val guildId = _ui.value.selectedGuildId ?: return@mutate null
            repository.stop(sessionIdentity, guildId)
        }
    }

    fun setRepeat(enabled: Boolean) {
        mutate(controlAction = PlayerControlAction.REPEAT) {
            val guildId = _ui.value.selectedGuildId ?: return@mutate null
            repository.setRepeat(sessionIdentity, guildId, enabled)
        }
    }

    fun toggleRadio() {
        mutate(controlAction = PlayerControlAction.RADIO) {
            val guildId = _ui.value.selectedGuildId
            val queue = _ui.value.queue
            if (guildId == null || queue == null) {
                _ui.update { it.copy(error = uiText(R.string.player_select_target_first)) }
                return@mutate null
            }
            when (val action = decideRadioToggle(queue, _ui.value.selectedVoiceChannelId)) {
                RadioToggleAction.MissingVoiceChannel -> {
                    _ui.update { it.copy(error = uiText(R.string.player_select_target_first)) }
                    null
                }
                is RadioToggleAction.Disable ->
                    repository.disableRadio(sessionIdentity, guildId)
                is RadioToggleAction.Enable ->
                    repository.enableRadio(sessionIdentity, guildId, action.request)
            }
        }
    }

    fun removeEntry(entryId: String) {
        if (entryId in _ui.value.mutatingEntryIds) return
        val guildId = _ui.value.selectedGuildId ?: return
        _ui.update { it.copy(
            mutatingEntryIds = it.mutatingEntryIds + entryId,
            removeStatuses = it.removeStatuses + (entryId to SwipeActionStatus.PENDING),
            error = null,
        ) }
        viewModelScope.launch {
            try {
                val response = repository.removeQueueEntry(
                    expectedIdentity = sessionIdentity,
                    guildId = guildId,
                    entryId = entryId,
                )
                applyQueueSnapshot(response)
                _ui.update { it.copy(
                    mutatingEntryIds = it.mutatingEntryIds - entryId,
                    removeStatuses = it.removeStatuses + (entryId to SwipeActionStatus.SUCCESS),
                ) }
            } catch (e: Exception) {
                handleMutationError(e)
                _ui.update { it.copy(
                    mutatingEntryIds = it.mutatingEntryIds - entryId,
                    removeStatuses = it.removeStatuses + (entryId to SwipeActionStatus.FAILURE),
                ) }
            }
            delay(1_200)
            _ui.update { it.copy(removeStatuses = it.removeStatuses - entryId) }
        }
    }

    fun swapEntries(sourceEntryId: String, targetEntryId: String, expectedSnapshotVersion: Long): Boolean {
        val state = _ui.value
        val snapshot = state.queue ?: return false
        val guildId = state.selectedGuildId ?: return false
        if (snapshot.version != expectedSnapshotVersion || snapshot.guildId != guildId) return false
        if (swappedQueueEntries(snapshot.pendingEntries, sourceEntryId, targetEntryId) == null) return false

        _ui.update { it.copy(isMutating = true, isQueueReordering = true, error = null) }
        viewModelScope.launch {
            try {
                val response = repository.swapQueueEntries(
                        expectedIdentity = sessionIdentity,
                        guildId = guildId,
                        firstEntryId = sourceEntryId,
                        secondEntryId = targetEntryId,
                    )
                applyQueueSnapshot(response)
                _ui.update { it.copy(isMutating = false, isQueueReordering = false) }
            } catch (e: Exception) {
                handleMutationError(e)
            }
        }
        return true
    }

    fun clearQueue() {
        mutate {
            val guildId = _ui.value.selectedGuildId ?: return@mutate null
            repository.clearQueue(sessionIdentity, guildId)
        }
    }

    private fun mutate(
        controlAction: PlayerControlAction? = null,
        call: suspend () -> QueueSnapshotResponse?,
    ) {
        if (controlAction != null) {
            val accepted = synchronized(pendingControlActions) {
                pendingControlActions.add(controlAction)
            }
            if (!accepted) return
        }

        viewModelScope.launch {
            try {
                performMutation(controlAction, call)
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
        call: suspend () -> QueueSnapshotResponse?,
    ) {
        _ui.update {
            it.copy(
                isMutating = controlAction == null,
                activeControlAction = controlAction,
                error = null,
            )
        }
        try {
            val response = call() ?: run {
                _ui.update { it.copy(isMutating = false, activeControlAction = null) }
                return
            }
            applyQueueSnapshot(response)
            _ui.update { current ->
                current.copy(
                    isMutating = false,
                    activeControlAction = null,
                )
            }
        } catch (e: Exception) {
            handleMutationError(e)
        }
    }

    private suspend fun handleMutationError(e: Exception) {
        var parsedProblem: com.tryniecki.kajutabot.api.model.error.KajutaBotProblemDetails? = null
        if (e is HttpException) {
            val problem = try {
                KajutaBotApiErrors.problemDetailsOf(e)
            } catch (_: Exception) {
                null
            }
            parsedProblem = problem
            if (e.code() == 409 && problem?.errorCode == KajutaBotApiErrors.QUEUE_VERSION_CONFLICT) {
                val guildId = _ui.value.selectedGuildId
                if (guildId != null) {
                    try {
                        val fresh = fetchQueueSnapshot(guildId)
                        applyQueueSnapshot(fresh)
                        _ui.update {
                            it.copy(
                                isMutating = false,
                                isQueueReordering = false,
                                activeControlAction = null,
                                error = uiText(R.string.player_queue_conflict_refreshed),
                            )
                        }
                        return
                    } catch (_: Exception) {
                    }
                }
            }
        }
        // Persistence conflicts and transport failures have ambiguous outcomes.
        // Reconcile from the server, but never repeat the mutation automatically.
        _ui.value.selectedGuildId?.let { guildId ->
            try {
                applyQueueSnapshot(fetchQueueSnapshot(guildId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the original failure visible.
            }
        }
        _ui.update {
            it.copy(
                isMutating = false,
                isQueueReordering = false,
                activeControlAction = null,
                error = userMessageForError(e, parsedProblem),
            )
        }
    }

    companion object {
        private val URL_REGEX = Regex("""https?://[^\s]+""")

        fun looksLikeUrl(input: String): Boolean =
            input.startsWith("http://", ignoreCase = true) ||
                input.startsWith("https://", ignoreCase = true)

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PlayerViewModel(
                        repository = container.playerRepository,
                        preferencesRepository = container.preferencesRepository,
                    )
                }
            }
    }
}
