package com.tryniecki.kajutabot.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.favorites.FavoriteResponse
import com.tryniecki.kajutabot.data.preferences.UserPreferencesRepository
import com.tryniecki.kajutabot.data.preferences.favoritesPreferenceOwnerKey
import com.tryniecki.kajutabot.data.repository.FavoritesRepository
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.ui.components.SwipeActionStatus
import com.tryniecki.kajutabot.ui.text.uiText
import com.tryniecki.kajutabot.ui.userMessageForError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class FavoritesUiState(
    val favorites: List<FavoriteResponse> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val isFavoriteMutating: Boolean = false,
    val isQueueMutating: Boolean = false,
    val shuffle: Boolean = false,
    val error: UiText? = null,
    val queueStatuses: Map<String, SwipeActionStatus> = emptyMap(),
)

class FavoritesViewModel(
    private val repository: FavoritesRepository,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    private val sessionIdentity = checkNotNull(repository.sessionIdentity.value)
    private val preferenceOwnerKey = favoritesPreferenceOwnerKey(checkNotNull(repository.currentSession()))

    private val _ui = MutableStateFlow(FavoritesUiState(isLoading = true))
    val ui: StateFlow<FavoritesUiState> = _ui.asStateFlow()
    private val messages = Channel<UiText>(Channel.UNLIMITED)
    val messageEvents = messages.receiveAsFlow()

    private var refreshGeneration = 0L
    private var mutationRevision = 0L
    private var favoriteIdentities: Set<String> = emptySet()
    private val shuffleWriteMutex = Mutex()
    private fun emitMessage(message: UiText) { messages.trySend(message) }

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
                        favoriteIdentities = items.asSequence()
                            .map { favoriteIdentity(it.contentUrl) }
                            .toSet()
                        _ui.update { it.copy(favorites = items, isLoading = false) }
                    } else {
                        _ui.update { it.copy(isLoading = false) }
                    }
                }
            } catch (e: Exception) {
                if (generation == refreshGeneration) {
                    _ui.update {
                        if (revision == mutationRevision) it.copy(isLoading = false, error = userMessageForError(e))
                        else it.copy(isLoading = false)
                    }
                }
            }
        }
    }

    fun dismissMessage() {
        _ui.update { it.copy(error = null) }
    }

    fun setShuffle(enabled: Boolean) {
        emitMessage(uiText(if (enabled) R.string.favorites_shuffle_on else R.string.favorites_shuffle_off))
        _ui.update { it.copy(shuffle = enabled) }
        viewModelScope.launch {
            shuffleWriteMutex.withLock {
                preferencesRepository.setFavoritesShuffle(preferenceOwnerKey, enabled)
            }
        }
    }

    fun isFavorite(track: PlaybackTrackResponse): Boolean =
        track.favoriteIdentities().any(favoriteIdentities::contains)

    fun toggle(track: PlaybackTrackResponse) = toggle(track, showFeedback = true)

    fun toggleSilently(track: PlaybackTrackResponse) = toggle(track, showFeedback = false)

    private fun toggle(track: PlaybackTrackResponse, showFeedback: Boolean) {
        if (_ui.value.isFavoriteMutating || _ui.value.isLoading) return
        val identities = track.favoriteIdentities()
        val existing = _ui.value.favorites.firstOrNull { favoriteIdentity(it.contentUrl) in identities }
        if (existing != null) {
            delete(existing.contentUrl, toggleFeedback = showFeedback)
        } else {
            add(track, showFeedback)
        }
    }

    private fun add(track: PlaybackTrackResponse, showFeedback: Boolean) {
        if (_ui.value.isFavoriteMutating) return
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, isFavoriteMutating = true, error = null) }
            try {
                val added = repository.addFavorite(
                    expectedIdentity = sessionIdentity,
                    contentType = track.contentType,
                    contentId = track.contentId,
                )
                mutationRevision++
                val identity = favoriteIdentity(added.contentUrl)
                if (showFeedback) emitMessage(uiText(R.string.favorites_added))
                favoriteIdentities = favoriteIdentities + identity
                _ui.update { current ->
                    current.copy(
                        favorites = listOf(added) + current.favorites.filterNot {
                            favoriteIdentity(it.contentUrl) == identity
                        },
                        isMutating = false,
                        isFavoriteMutating = false,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = false, isFavoriteMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    fun delete(contentUrl: String) = delete(contentUrl, toggleFeedback = false)

    private fun delete(contentUrl: String, toggleFeedback: Boolean) {
        if (_ui.value.isFavoriteMutating) return
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, isFavoriteMutating = true, error = null) }
            try {
                repository.deleteFavorite(sessionIdentity, contentUrl)
                mutationRevision++
                val identity = favoriteIdentity(contentUrl)
                if (toggleFeedback) emitMessage(uiText(R.string.favorites_removed))
                favoriteIdentities = favoriteIdentities - identity
                _ui.update { current ->
                    current.copy(
                        favorites = current.favorites.filterNot { favoriteIdentity(it.contentUrl) == identity },
                        isMutating = false,
                        isFavoriteMutating = false,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = false, isFavoriteMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    fun queueAll() {
        if (_ui.value.isQueueMutating) return
        _ui.update { it.copy(isQueueMutating = true, error = null) }
        viewModelScope.launch {
            val selection = preferencesRepository.currentGuildSelection()
            val guildId = selection.guildId
            val channelId = selection.voiceChannelId
            if (guildId == null || channelId == null) {
                _ui.update { it.copy(isQueueMutating = false, error = uiText(R.string.favorites_selection_required_all)) }
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
                emitMessage(uiText(R.string.favorites_queued_all))
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = it.isFavoriteMutating, isQueueMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    fun playSingle(contentUrl: String) {
        if (_ui.value.queueStatuses[contentUrl] == SwipeActionStatus.PENDING) return
        _ui.update { it.copy(queueStatuses = it.queueStatuses + (contentUrl to SwipeActionStatus.PENDING)) }
        viewModelScope.launch {
            val selection = preferencesRepository.currentGuildSelection()
            val guildId = selection.guildId
            val channelId = selection.voiceChannelId
            if (guildId == null || channelId == null) {
                _ui.update { it.copy(error = uiText(R.string.favorites_selection_required_play)) }
                _ui.update { it.copy(queueStatuses = it.queueStatuses + (contentUrl to SwipeActionStatus.FAILURE)) }
                delay(1800)
                _ui.update { it.copy(queueStatuses = it.queueStatuses - contentUrl) }
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
                emitMessage(uiText(R.string.favorites_queued_one))
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = it.isFavoriteMutating, error = userMessageForError(e)) }
                _ui.update { it.copy(queueStatuses = it.queueStatuses + (contentUrl to SwipeActionStatus.FAILURE)) }
            }
            delay(1800)
            _ui.update { it.copy(queueStatuses = it.queueStatuses - contentUrl) }
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
