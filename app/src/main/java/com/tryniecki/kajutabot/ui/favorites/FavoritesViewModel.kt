package com.tryniecki.kajutabot.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.api.model.favorites.FavoriteResponse
import com.tryniecki.kajutabot.api.model.favorites.QueueFavoritesRequest
import com.tryniecki.kajutabot.api.model.queue.EnqueueRequest
import com.tryniecki.kajutabot.ui.userMessageForError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val favorites: List<FavoriteResponse> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class FavoritesViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val sessionManager = container.sessionManager
    private val sessionIdentity = checkNotNull(sessionManager.sessionIdentity.value)
    private val selection = container.selectionStore

    private val _ui = MutableStateFlow(FavoritesUiState())
    val ui: StateFlow<FavoritesUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, error = null) }
            try {
                val items = sessionManager.withApiForSession(sessionIdentity) { it.getFavorites() }
                _ui.update { it.copy(favorites = items, isLoading = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(isLoading = false, error = userMessageForError(e)) }
            }
        }
    }

    fun dismissMessage() {
        _ui.update { it.copy(error = null, info = null) }
    }

    fun delete(contentUrl: String) {
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, error = null) }
            try {
                sessionManager.withApiForSession(sessionIdentity) { it.deleteFavorite(contentUrl) }
                val items = sessionManager.withApiForSession(sessionIdentity) { it.getFavorites() }
                _ui.update { it.copy(favorites = items, isMutating = false) }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    fun queueAll() {
        val guildId = selection.guildId
        val channelId = selection.voiceChannelId
        if (guildId == null || channelId == null) {
            _ui.update { it.copy(error = "Wybierz serwer i kanał głosowy w Odtwarzaczu, aby dodać ulubione.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, error = null, info = null) }
            try {
                sessionManager.withApiForSession(sessionIdentity) {
                    it.queueFavorites(QueueFavoritesRequest(guildId, channelId))
                }
                _ui.update { it.copy(isMutating = false, info = "Dodano ulubione do kolejki.") }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    fun playSingle(contentUrl: String) {
        val guildId = selection.guildId
        val channelId = selection.voiceChannelId
        if (guildId == null || channelId == null) {
            _ui.update { it.copy(error = "Wybierz serwer i kanał głosowy w Odtwarzaczu, aby odtworzyć.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, error = null, info = null) }
            try {
                sessionManager.withApiForSession(sessionIdentity) { api ->
                    val queue = api.getQueue(guildId)
                    api.enqueue(guildId, EnqueueRequest(channelId, listOf(contentUrl), queue.version))
                }
                _ui.update { it.copy(isMutating = false, info = "Dodano do kolejki.") }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FavoritesViewModel(container) as T
        }
    }
}
