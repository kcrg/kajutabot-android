package com.tryniecki.kajutabot.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.auth.SessionManager
import com.tryniecki.kajutabot.prefs.favoritesPreferenceOwnerKey
import com.tryniecki.kajutabot.api.model.favorites.FavoriteResponse
import com.tryniecki.kajutabot.api.model.favorites.AddFavoriteRequest
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.favorites.QueueFavoritesRequest
import com.tryniecki.kajutabot.api.model.queue.EnqueueRequest
import com.tryniecki.kajutabot.ui.userMessageForError
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.ui.text.uiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val favorites: List<FavoriteResponse> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val shuffle: Boolean = false,
    val error: UiText? = null,
    val info: UiText? = null,
)

class FavoritesViewModel(
    private val sessionManager: SessionManager,
    private val selectedGuildId: () -> String?,
    private val selectedChannelId: () -> String?,
    private val loadShuffle: (String) -> Boolean,
    private val saveShuffle: (String, Boolean) -> Unit,
) : ViewModel() {
    constructor(container: AppContainer) : this(
        container.sessionManager,
        { container.selectionStore.guildId },
        { container.selectionStore.voiceChannelId },
        container.favoritesPreferences::shuffle,
        container.favoritesPreferences::setShuffle,
    )

    private val sessionIdentity = checkNotNull(sessionManager.sessionIdentity.value)
    private val preferenceOwnerKey = favoritesPreferenceOwnerKey(checkNotNull(sessionManager.currentUserSession()))

    private val _ui = MutableStateFlow(FavoritesUiState(shuffle = loadShuffle(preferenceOwnerKey), isLoading = true))
    val ui: StateFlow<FavoritesUiState> = _ui.asStateFlow()

    private val _toggleMessages = MutableSharedFlow<UiText>(extraBufferCapacity = 4)
    val toggleMessages: SharedFlow<UiText> = _toggleMessages.asSharedFlow()
    private var refreshGeneration = 0L
    private var mutationRevision = 0L
    private var favoriteIdentities: Set<String> = emptySet()

    init {
        refresh()
    }

    fun refresh() {
        if (_ui.value.isMutating) return
        val generation = ++refreshGeneration
        val revision = mutationRevision
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                val items = sessionManager.withApiForSession(sessionIdentity) { it.getFavorites() }
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
        _ui.update { it.copy(error = null, info = null) }
    }

    fun setShuffle(enabled: Boolean) {
        saveShuffle(preferenceOwnerKey, enabled)
        _ui.update { it.copy(shuffle = enabled) }
        _toggleMessages.tryEmit(uiText(if (enabled) R.string.favorites_shuffle_on else R.string.favorites_shuffle_off))
    }

    fun isFavorite(track: PlaybackTrackResponse): Boolean =
        track.favoriteIdentities().any(favoriteIdentities::contains)

    fun toggle(track: PlaybackTrackResponse) {
        if (_ui.value.isMutating || _ui.value.isLoading) return
        val identities = track.favoriteIdentities()
        val existing = _ui.value.favorites.firstOrNull { favoriteIdentity(it.contentUrl) in identities }
        if (existing != null) {
            delete(existing.contentUrl, toggleFeedback = true)
        } else {
            add(track.url, track.title, track.artworkUrl, toggleFeedback = true)
        }
    }

    fun addByUrl(contentUrl: String) = add(contentUrl.trim(), null, null, toggleFeedback = false)

    private fun add(
        contentUrl: String,
        title: String?,
        thumbnailUrl: String?,
        toggleFeedback: Boolean,
    ) {
        if (_ui.value.isMutating || contentUrl.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, error = null, info = null) }
            try {
                val added = sessionManager.withApiForSession(sessionIdentity) {
                    it.addFavorite(AddFavoriteRequest(contentUrl, title, thumbnailUrl))
                }
                mutationRevision++
                val identity = favoriteIdentity(added.contentUrl)
                favoriteIdentities = favoriteIdentities + identity
                _ui.update { current ->
                    current.copy(
                        favorites = listOf(added) + current.favorites.filterNot {
                            favoriteIdentity(it.contentUrl) == identity
                        },
                        isMutating = false,
                        isLoading = false,
                        info = if (toggleFeedback) null else uiText(R.string.favorites_saved),
                    )
                }
                if (toggleFeedback) {
                    _toggleMessages.emit(uiText(R.string.favorites_added))
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    fun delete(contentUrl: String) = delete(contentUrl, toggleFeedback = false)

    private fun delete(contentUrl: String, toggleFeedback: Boolean) {
        if (_ui.value.isMutating) return
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, error = null) }
            try {
                sessionManager.withApiForSession(sessionIdentity) { it.deleteFavorite(contentUrl) }
                mutationRevision++
                val identity = favoriteIdentity(contentUrl)
                favoriteIdentities = favoriteIdentities - identity
                _ui.update { current ->
                    current.copy(
                        favorites = current.favorites.filterNot { favoriteIdentity(it.contentUrl) == identity },
                        isMutating = false,
                        isLoading = false,
                    )
                }
                if (toggleFeedback) {
                    _toggleMessages.emit(uiText(R.string.favorites_removed))
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    fun queueAll() {
        if (_ui.value.isMutating) return
        val guildId = selectedGuildId()
        val channelId = selectedChannelId()
        val shuffle = _ui.value.shuffle
        if (guildId == null || channelId == null) {
            _ui.update { it.copy(error = uiText(R.string.favorites_selection_required_all)) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, error = null, info = null) }
            try {
                val response = sessionManager.withApiForSession(sessionIdentity) {
                    it.queueFavorites(QueueFavoritesRequest(guildId, channelId, shuffle = shuffle))
                }
                _ui.update { it.copy(isMutating = false, info = uiText(R.string.favorites_queued_all)) }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    fun playSingle(contentUrl: String) {
        if (_ui.value.isMutating) return
        val guildId = selectedGuildId()
        val channelId = selectedChannelId()
        if (guildId == null || channelId == null) {
            _ui.update { it.copy(error = uiText(R.string.favorites_selection_required_play)) }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, error = null, info = null) }
            try {
                val response = sessionManager.withApiForSession(sessionIdentity) { api ->
                    val queue = api.getQueue(guildId)
                    api.enqueue(guildId, EnqueueRequest(channelId, listOf(contentUrl), queue.version))
                }
                _ui.update { it.copy(isMutating = false, info = uiText(R.string.favorites_queued_one)) }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    FavoritesViewModel(container)
                }
            }
    }
}
