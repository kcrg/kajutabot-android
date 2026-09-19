package com.tryniecki.kajutabot.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.api.model.favorites.FavoriteResponse
import com.tryniecki.kajutabot.api.model.favorites.AddFavoriteRequest
import com.tryniecki.kajutabot.api.model.common.TrackResponse
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
    val shuffle: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class FavoritesViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val sessionManager = container.sessionManager
    private val sessionIdentity = checkNotNull(sessionManager.sessionIdentity.value)
    private val selection = container.selectionStore
    private val userId = checkNotNull(sessionManager.currentUserSession()).user.discordUserId
    private val preferences = container.favoritesPreferences

    private val _ui = MutableStateFlow(FavoritesUiState(shuffle = preferences.shuffle(userId), isLoading = true))
    val ui: StateFlow<FavoritesUiState> = _ui.asStateFlow()
    private var refreshGeneration = 0L
    private var mutationRevision = 0L

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
                    _ui.update {
                        if (revision == mutationRevision) it.copy(favorites = items, isLoading = false)
                        else it.copy(isLoading = false)
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
        preferences.setShuffle(userId, enabled)
        _ui.update { it.copy(shuffle = enabled) }
    }

    fun isFavorite(track: TrackResponse): Boolean {
        val identities = track.favoriteIdentities()
        return _ui.value.favorites.any { favoriteIdentity(it.contentUrl) in identities }
    }

    fun toggle(track: TrackResponse) {
        if (_ui.value.isMutating || _ui.value.isLoading) return
        val identities = track.favoriteIdentities()
        val existing = _ui.value.favorites.firstOrNull { favoriteIdentity(it.contentUrl) in identities }
        if (existing != null) {
            delete(existing.contentUrl)
        } else {
            add(track.url, track.title, track.thumbnailUrl)
        }
    }

    fun addByUrl(contentUrl: String) = add(contentUrl.trim(), null, null)

    private fun add(contentUrl: String, title: String?, thumbnailUrl: String?) {
        if (_ui.value.isMutating || contentUrl.isBlank()) return
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, error = null, info = null) }
            try {
                val added = sessionManager.withApiForSession(sessionIdentity) {
                    it.addFavorite(AddFavoriteRequest(contentUrl, title, thumbnailUrl))
                }
                mutationRevision++
                _ui.update { current ->
                    val identity = favoriteIdentity(added.contentUrl)
                    current.copy(
                        favorites = listOf(added) + current.favorites.filterNot {
                            favoriteIdentity(it.contentUrl) == identity
                        },
                        isMutating = false,
                        isLoading = false,
                        info = "Zapisano utwór w ulubionych.",
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    fun delete(contentUrl: String) {
        if (_ui.value.isMutating) return
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, error = null) }
            try {
                sessionManager.withApiForSession(sessionIdentity) { it.deleteFavorite(contentUrl) }
                mutationRevision++
                val identity = favoriteIdentity(contentUrl)
                _ui.update { current ->
                    current.copy(
                        favorites = current.favorites.filterNot { favoriteIdentity(it.contentUrl) == identity },
                        isMutating = false,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    fun queueAll() {
        if (_ui.value.isMutating) return
        val guildId = selection.guildId
        val channelId = selection.voiceChannelId
        val shuffle = _ui.value.shuffle
        if (guildId == null || channelId == null) {
            _ui.update { it.copy(error = "Wybierz serwer i kanał głosowy w Odtwarzaczu, aby dodać ulubione.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, error = null, info = null) }
            try {
                val response = sessionManager.withApiForSession(sessionIdentity) {
                    it.queueFavorites(QueueFavoritesRequest(guildId, channelId, shuffle = shuffle))
                }
                _ui.update {
                    if (response.operation.succeeded) it.copy(isMutating = false, info = "Dodano ulubione do kolejki.")
                    else it.copy(isMutating = false, error = response.operation.message ?: "Nie udało się dodać ulubionych.")
                }
            } catch (e: Exception) {
                _ui.update { it.copy(isMutating = false, error = userMessageForError(e)) }
            }
        }
    }

    fun playSingle(contentUrl: String) {
        if (_ui.value.isMutating) return
        val guildId = selection.guildId
        val channelId = selection.voiceChannelId
        if (guildId == null || channelId == null) {
            _ui.update { it.copy(error = "Wybierz serwer i kanał głosowy w Odtwarzaczu, aby odtworzyć.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isMutating = true, error = null, info = null) }
            try {
                val response = sessionManager.withApiForSession(sessionIdentity) { api ->
                    val queue = api.getQueue(guildId)
                    api.enqueue(guildId, EnqueueRequest(channelId, listOf(contentUrl), queue.version))
                }
                _ui.update {
                    if (response.operation.succeeded) it.copy(isMutating = false, info = "Dodano do kolejki.")
                    else it.copy(isMutating = false, error = response.operation.message ?: "Nie udało się dodać utworu.")
                }
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
