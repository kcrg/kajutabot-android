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
import com.tryniecki.kajutabot.ui.text.UiMessage
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.ui.text.uiText
import com.tryniecki.kajutabot.ui.userMessageForError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val favorites: List<FavoriteResponse> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val shuffle: Boolean = false,
    val error: UiText? = null,
    val info: UiText? = null,
    val transientMessage: UiMessage? = null,
)

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
    private var favoriteIdentities: Set<String> = emptySet()
    private var messageSequence = 0L

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
        _ui.update { it.copy(error = null, info = null) }
    }

    fun acknowledgeTransientMessage(id: Long) {
        _ui.update { current ->
            if (current.transientMessage?.id == id) current.copy(transientMessage = null) else current
        }
    }

    fun setShuffle(enabled: Boolean) {
        val message = UiMessage(
            id = ++messageSequence,
            text = uiText(if (enabled) R.string.favorites_shuffle_on else R.string.favorites_shuffle_off),
        )
        _ui.update { it.copy(shuffle = enabled, transientMessage = message) }
        viewModelScope.launch {
            preferencesRepository.setFavoritesShuffle(preferenceOwnerKey, enabled)
        }
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
                val added = repository.addFavorite(
                    expectedIdentity = sessionIdentity,
                    contentUrl = contentUrl,
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                )
                mutationRevision++
                val identity = favoriteIdentity(added.contentUrl)
                val transientMessage = if (toggleFeedback) {
                    UiMessage(++messageSequence, uiText(R.string.favorites_added))
                } else {
                    null
                }
                favoriteIdentities = favoriteIdentities + identity
                _ui.update { current ->
                    current.copy(
                        favorites = listOf(added) + current.favorites.filterNot {
                            favoriteIdentity(it.contentUrl) == identity
                        },
                        isMutating = false,
                        isLoading = false,
                        info = if (toggleFeedback) null else uiText(R.string.favorites_saved),
                        transientMessage = transientMessage ?: current.transientMessage,
                    )
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
                repository.deleteFavorite(sessionIdentity, contentUrl)
                mutationRevision++
                val identity = favoriteIdentity(contentUrl)
                val transientMessage = if (toggleFeedback) {
                    UiMessage(++messageSequence, uiText(R.string.favorites_removed))
                } else {
                    null
                }
                favoriteIdentities = favoriteIdentities - identity
                _ui.update { current ->
                    current.copy(
                        favorites = current.favorites.filterNot { favoriteIdentity(it.contentUrl) == identity },
                        isMutating = false,
                        isLoading = false,
                        transientMessage = transientMessage ?: current.transientMessage,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    fun queueAll() {
        if (_ui.value.isMutating) return
        viewModelScope.launch {
            val selection = preferencesRepository.currentGuildSelection()
            val guildId = selection.guildId
            val channelId = selection.voiceChannelId
            if (guildId == null || channelId == null) {
                _ui.update { it.copy(error = uiText(R.string.favorites_selection_required_all)) }
                return@launch
            }
            _ui.update { it.copy(isMutating = true, error = null, info = null) }
            try {
                repository.queueFavorites(
                    expectedIdentity = sessionIdentity,
                    guildId = guildId,
                    channelId = channelId,
                    shuffle = _ui.value.shuffle,
                )
                _ui.update { it.copy(isMutating = false, info = uiText(R.string.favorites_queued_all)) }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    fun playSingle(contentUrl: String) {
        if (_ui.value.isMutating) return
        viewModelScope.launch {
            val selection = preferencesRepository.currentGuildSelection()
            val guildId = selection.guildId
            val channelId = selection.voiceChannelId
            if (guildId == null || channelId == null) {
                _ui.update { it.copy(error = uiText(R.string.favorites_selection_required_play)) }
                return@launch
            }
            _ui.update { it.copy(isMutating = true, error = null, info = null) }
            try {
                repository.playSingle(
                    expectedIdentity = sessionIdentity,
                    guildId = guildId,
                    channelId = channelId,
                    contentUrl = contentUrl,
                )
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
                    FavoritesViewModel(
                        repository = container.favoritesRepository,
                        preferencesRepository = container.preferencesRepository,
                    )
                }
            }
    }
}
