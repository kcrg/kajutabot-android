package com.tryniecki.kajutabot.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.favorites.FavoriteResponse
import com.tryniecki.kajutabot.data.preferences.UserPreferencesRepository
import com.tryniecki.kajutabot.data.preferences.favoritesPreferenceOwnerKey
import com.tryniecki.kajutabot.data.repository.FavoritesRepository
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.ui.components.SwipeActionStatus
import com.tryniecki.kajutabot.ui.components.SWIPE_RESULT_HOLD_MS
import com.tryniecki.kajutabot.ui.components.RetainedSwipeItem
import com.tryniecki.kajutabot.ui.userMessageForError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FavoritesUiState(
    val favorites: List<FavoriteResponse> = emptyList(),
    val favoriteIdentities: Set<String> = emptySet(),
    val retainedDeletedFavorites: Map<String, RetainedSwipeItem<FavoriteResponse>> = emptyMap(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val isFavoriteMutating: Boolean = false,
    val isQueueMutating: Boolean = false,
    val shuffle: Boolean = false,
    val error: UiText? = null,
    val queueStatuses: Map<String, SwipeActionStatus> = emptyMap(),
    val favoriteStatuses: Map<String, SwipeActionStatus> = emptyMap(),
    val queueAllStatus: SwipeActionStatus = SwipeActionStatus.IDLE,
) {
    fun isFavorite(track: PlaybackTrackResponse): Boolean =
        track.favoriteIdentities().any(favoriteIdentities::contains)

    fun statusFor(track: PlaybackTrackResponse): SwipeActionStatus =
        track.favoriteIdentities().firstNotNullOfOrNull { favoriteStatuses[it] }
            ?: SwipeActionStatus.IDLE
}

class FavoritesViewModel(
    private val repository: FavoritesRepository,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    private val sessionIdentity = checkNotNull(repository.sessionIdentity.value)
    private val preferenceOwnerKey = favoritesPreferenceOwnerKey(checkNotNull(repository.currentSession()))

    private val _ui = MutableStateFlow(FavoritesUiState(isLoading = true))
    val ui: StateFlow<FavoritesUiState> = _ui.asStateFlow()

    private var refreshGeneration = 0L
    private var mutationRevision = 0L
    private val shuffleWriteMutex = Mutex()
    private val favoriteFeedbackJobs = mutableMapOf<String, Job>()
    private val favoriteFeedbackGenerations = mutableMapOf<String, Long>()
    private val queueFeedbackJobs = mutableMapOf<String, Job>()
    private var queueAllFeedbackJob: Job? = null

    init {
        viewModelScope.launch {
            val shuffle = preferencesRepository.favoritesShuffle(preferenceOwnerKey).first()
            _ui.update { it.copy(shuffle = shuffle) }
            refresh()
        }
    }

    fun refresh() {
        if (_ui.value.isMutating) return
        val generation = ++refreshGeneration
        val revision = mutationRevision
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                val items = repository.getFavorites(sessionIdentity)
                if (generation == refreshGeneration) {
                    if (revision == mutationRevision) {
                        val identities = items.asSequence()
                            .map { favoriteIdentity(it.contentUrl) }
                            .toSet()
                        _ui.update { it.copy(favorites = items, favoriteIdentities = identities, isLoading = false) }
                    } else {
                        _ui.update { it.copy(isLoading = false) }
                    }
                }
            } catch (e: Exception) {
                if (generation == refreshGeneration) {
                    _ui.update {
                        if (revision == mutationRevision) it.copy(
                            isLoading = false,
                            error = userMessageForError(e),
                        ) else it.copy(isLoading = false)
                    }
                }
            }
        }
    }

    fun dismissMessage() {
        _ui.update { it.copy(error = null) }
    }

    fun setShuffle(enabled: Boolean) {
        _ui.update { it.copy(shuffle = enabled) }
        viewModelScope.launch {
            shuffleWriteMutex.withLock {
                preferencesRepository.setFavoritesShuffle(preferenceOwnerKey, enabled)
            }
        }
    }

    fun isFavorite(track: PlaybackTrackResponse): Boolean = _ui.value.isFavorite(track)

    fun statusFor(track: PlaybackTrackResponse): SwipeActionStatus = _ui.value.statusFor(track)

    fun toggle(track: PlaybackTrackResponse) = toggleFavorite(track)

    fun toggleSilently(track: PlaybackTrackResponse) = toggleFavorite(track)

    private fun toggleFavorite(track: PlaybackTrackResponse) {
        if (_ui.value.isLoading || statusFor(track) == SwipeActionStatus.PENDING) return
        val identities = track.favoriteIdentities()
        val existing = _ui.value.favorites.firstOrNull { favoriteIdentity(it.contentUrl) in identities }
        if (isFavorite(track) && existing != null) {
            delete(existing.contentUrl)
        } else {
            add(track)
        }
    }

    private fun add(track: PlaybackTrackResponse) {
        val key = favoriteIdentity(track.url)
        if (_ui.value.favoriteStatuses[key] == SwipeActionStatus.PENDING) return
        val generation = nextFavoriteFeedbackGeneration(key)
        favoriteFeedbackJobs.remove(key)?.cancel()
        viewModelScope.launch {
            _ui.update { it.copy(
                isMutating = true,
                isFavoriteMutating = true,
                favoriteStatuses = it.favoriteStatuses + (key to SwipeActionStatus.PENDING),
                retainedDeletedFavorites = it.retainedDeletedFavorites - key,
            ) }
            try {
                val added = repository.addFavorite(
                    expectedIdentity = sessionIdentity,
                    contentType = track.contentType,
                    contentId = track.contentId,
                )
                mutationRevision++
                val identity = favoriteIdentity(added.contentUrl)
                _ui.update { current ->
                    val statuses = current.favoriteStatuses + (key to SwipeActionStatus.SUCCESS)
                    val otherPending = statuses.any { (identity, status) ->
                        identity != key && status == SwipeActionStatus.PENDING
                    }
                    current.copy(
                        favorites = listOf(added) + current.favorites.filterNot {
                            favoriteIdentity(it.contentUrl) == identity
                        },
                        favoriteIdentities = current.favoriteIdentities + identity,
                        retainedDeletedFavorites = current.retainedDeletedFavorites - identity,
                        isMutating = otherPending,
                        isFavoriteMutating = otherPending,
                        isLoading = false,
                        favoriteStatuses = statuses,
                    )
                }
            } catch (e: Exception) {
                _ui.update { current ->
                    val statuses = current.favoriteStatuses + (key to SwipeActionStatus.FAILURE)
                    val otherPending = statuses.values.any { it == SwipeActionStatus.PENDING }
                    current.copy(isMutating = otherPending, isFavoriteMutating = otherPending, favoriteStatuses = statuses)
                }
            }
            clearFavoriteFeedbackLater(key, generation)
        }
    }

    fun delete(contentUrl: String) {
        val key = favoriteIdentity(contentUrl)
        if (_ui.value.favoriteStatuses[key] == SwipeActionStatus.PENDING) return
        val position = _ui.value.favorites.indexOfFirst { favoriteIdentity(it.contentUrl) == key }
        if (position < 0) return
        val favorite = _ui.value.favorites[position]
        val generation = nextFavoriteFeedbackGeneration(key)
        favoriteFeedbackJobs.remove(key)?.cancel()
        viewModelScope.launch {
            _ui.update { it.copy(
                isMutating = true,
                isFavoriteMutating = true,
                favoriteStatuses = it.favoriteStatuses + (key to SwipeActionStatus.PENDING),
            ) }
            try {
                repository.deleteFavorite(sessionIdentity, contentUrl)
                mutationRevision++
                _ui.update { current ->
                    val statuses = current.favoriteStatuses + (key to SwipeActionStatus.SUCCESS)
                    val otherPending = statuses.any { (identity, status) ->
                        identity != key && status == SwipeActionStatus.PENDING
                    }
                    current.copy(
                        favorites = current.favorites.filterNot { favoriteIdentity(it.contentUrl) == key },
                        isMutating = otherPending,
                        isFavoriteMutating = otherPending,
                        isLoading = false,
                        favoriteIdentities = current.favoriteIdentities - key,
                        retainedDeletedFavorites = current.retainedDeletedFavorites +
                            (key to RetainedSwipeItem(favorite, position)),
                        favoriteStatuses = statuses,
                    )
                }
                delay(SWIPE_RESULT_HOLD_MS)
                _ui.update { current ->
                    if (favoriteFeedbackGenerations[key] == generation) {
                        current.copy(retainedDeletedFavorites = current.retainedDeletedFavorites - key)
                    } else current
                }
            } catch (e: Exception) {
                _ui.update { current ->
                    val statuses = current.favoriteStatuses + (key to SwipeActionStatus.FAILURE)
                    val otherPending = statuses.values.any { it == SwipeActionStatus.PENDING }
                    current.copy(isMutating = otherPending, isFavoriteMutating = otherPending, favoriteStatuses = statuses)
                }
            }
            clearFavoriteFeedbackLater(key, generation)
        }
    }

    private fun nextFavoriteFeedbackGeneration(key: String): Long =
        (favoriteFeedbackGenerations[key] ?: 0L).inc().also { favoriteFeedbackGenerations[key] = it }

    private fun clearFavoriteFeedbackLater(key: String, generation: Long) {
        if (favoriteFeedbackGenerations[key] != generation) return
        favoriteFeedbackJobs[key] = viewModelScope.launch {
            delay(1_200)
            if (favoriteFeedbackGenerations[key] != generation) return@launch
            _ui.update { it.copy(favoriteStatuses = it.favoriteStatuses - key) }
            favoriteFeedbackJobs.remove(key)
            favoriteFeedbackGenerations.remove(key)
        }
    }

    fun queueAll() {
        if (_ui.value.isQueueMutating) return
        queueAllFeedbackJob?.cancel()
        _ui.update { it.copy(isQueueMutating = true, queueAllStatus = SwipeActionStatus.PENDING) }
        viewModelScope.launch {
            val selection = preferencesRepository.currentGuildSelection()
            val guildId = selection.guildId
            val channelId = selection.voiceChannelId
            if (guildId == null || channelId == null) {
                _ui.update { it.copy(isQueueMutating = false, queueAllStatus = SwipeActionStatus.FAILURE) }
                clearQueueAllFeedbackLater()
                return@launch
            }
            try {
                repository.queueFavorites(
                    expectedIdentity = sessionIdentity,
                    guildId = guildId,
                    channelId = channelId,
                    shuffle = _ui.value.shuffle,
                )
                _ui.update { it.copy(isMutating = it.isFavoriteMutating, isQueueMutating = false) }
                _ui.update { it.copy(queueAllStatus = SwipeActionStatus.SUCCESS) }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = it.isFavoriteMutating, isQueueMutating = false, queueAllStatus = SwipeActionStatus.FAILURE) }
            }
            clearQueueAllFeedbackLater()
        }
    }

    private fun clearQueueAllFeedbackLater() {
        queueAllFeedbackJob = viewModelScope.launch {
            delay(1_200)
            _ui.update { it.copy(queueAllStatus = SwipeActionStatus.IDLE) }
            queueAllFeedbackJob = null
        }
    }

    fun playSingle(contentUrl: String) {
        if (_ui.value.queueStatuses[contentUrl] == SwipeActionStatus.PENDING) return
        queueFeedbackJobs.remove(contentUrl)?.cancel()
        _ui.update { it.copy(queueStatuses = it.queueStatuses + (contentUrl to SwipeActionStatus.PENDING)) }
        viewModelScope.launch {
            val selection = preferencesRepository.currentGuildSelection()
            val guildId = selection.guildId
            val channelId = selection.voiceChannelId
            if (guildId == null || channelId == null) {
                _ui.update { it.copy(queueStatuses = it.queueStatuses + (contentUrl to SwipeActionStatus.FAILURE)) }
                clearQueueFeedbackLater(contentUrl)
                return@launch
            }
            _ui.update { it.copy(error = null) }
            try {
                repository.playSingle(
                    expectedIdentity = sessionIdentity,
                    guildId = guildId,
                    channelId = channelId,
                    contentUrl = contentUrl,
                )
                _ui.update { it.copy(isMutating = it.isFavoriteMutating) }
                _ui.update { it.copy(queueStatuses = it.queueStatuses + (contentUrl to SwipeActionStatus.SUCCESS)) }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = it.isFavoriteMutating) }
                _ui.update { it.copy(queueStatuses = it.queueStatuses + (contentUrl to SwipeActionStatus.FAILURE)) }
            }
            clearQueueFeedbackLater(contentUrl)
        }
    }

    private fun clearQueueFeedbackLater(contentUrl: String) {
        queueFeedbackJobs[contentUrl] = viewModelScope.launch {
            delay(1_200)
            _ui.update { it.copy(queueStatuses = it.queueStatuses - contentUrl) }
            queueFeedbackJobs.remove(contentUrl)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    FavoritesViewModel(
                        repository = container.favoritesRepository,
                        preferencesRepository = container.preferencesRepository,
                    )
                }
            }
    }
}
