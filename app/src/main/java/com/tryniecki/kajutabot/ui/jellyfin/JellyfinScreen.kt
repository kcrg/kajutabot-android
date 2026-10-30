package com.tryniecki.kajutabot.ui.jellyfin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.api.model.jellyfin.JellyfinAudioItemResponse
import com.tryniecki.kajutabot.ui.components.SwipeActionCard
import com.tryniecki.kajutabot.ui.components.SwipeActionHints
import com.tryniecki.kajutabot.ui.components.SwipeActionStatus
import com.tryniecki.kajutabot.ui.components.TrackListCardContent
import com.tryniecki.kajutabot.ui.favorites.FavoriteSkeletonCard
import com.tryniecki.kajutabot.ui.navigation.ScrollToTopOnReselect
import com.tryniecki.kajutabot.ui.player.formatDuration
import com.tryniecki.kajutabot.ui.text.asString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun JellyfinRoute(viewModel: JellyfinViewModel, reselected: Flow<Unit>) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    SideEffect(viewModel, ui.isConnected) { viewModel.ensureLibraryLoaded() }
    JellyfinScreen(
        ui = ui,
        onSearchQueryChange = viewModel::setSearchQuery,
        onLoadMore = viewModel::loadMore,
        onRetry = viewModel::retryLoad,
        onEnqueue = viewModel::enqueue,
        onDismiss = viewModel::dismissMessage,
        reselected = reselected,
    )
}

@Composable
fun JellyfinScreen(
    ui: JellyfinUiState,
    onSearchQueryChange: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onEnqueue: (String) -> Unit,
    onDismiss: () -> Unit,
    reselected: Flow<Unit>,
) {
    val listState = rememberLazyListState()
    val motion = MaterialTheme.motionScheme
    ScrollToTopOnReselect(listState, reselected)
    val currentUi by rememberUpdatedState(ui)
    val loadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            currentUi.canLoadMore && lastVisible >= listState.layoutInfo.totalItemsCount - 6
        }.distinctUntilChanged().collect { if (it) loadMore() }
    }

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 12.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header", contentType = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.nav_jellyfin), style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = ui.searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.jellyfin_search)) },
                        singleLine = true,
                    )
                    if (ui.items.isNotEmpty()) SwipeActionHints(showRemove = false)
                }
            }
            if (ui.error != null) {
                item(key = "error", contentType = "error") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ui.error.asString(), modifier = Modifier.weight(1f))
                            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
                        }
                    }
                }
            }
            if (ui.isLoading && !ui.isLibraryLoaded) {
                items(4, key = { "jellyfin-skeleton-$it" }, contentType = { "jellyfin-skeleton" }) {
                    FavoriteSkeletonCard(
                        modifier = Modifier.animateItem(
                            fadeInSpec = motion.fastEffectsSpec(),
                            fadeOutSpec = motion.defaultEffectsSpec(),
                            placementSpec = motion.defaultSpatialSpec(),
                        ),
                    )
                }
            }
            items(ui.items, key = { it.itemId }, contentType = { "jellyfin-track" }) { track ->
                JellyfinTrackRow(
                    track = track,
                    canEnqueue = ui.canEnqueue,
                    queueStatus = ui.queueStatuses[track.itemId] ?: SwipeActionStatus.IDLE,
                    onEnqueue = onEnqueue,
                    modifier = Modifier.animateItem(
                        fadeInSpec = motion.fastEffectsSpec(),
                        fadeOutSpec = motion.fastEffectsSpec(),
                        placementSpec = motion.fastSpatialSpec(),
                    ),
                )
            }
            item(key = "footer", contentType = "footer") {
                when {
                    ui.isLoadingMore -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                    ui.loadFailed -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                    }
                    ui.isLibraryLoaded && ui.items.isEmpty() -> Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_music_outline),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(
                                if (ui.searchQuery.isBlank()) R.string.jellyfin_library_empty else R.string.jellyfin_no_results,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JellyfinTrackRow(
    track: JellyfinAudioItemResponse,
    canEnqueue: Boolean,
    queueStatus: SwipeActionStatus,
    onEnqueue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val unknownArtist = stringResource(R.string.jellyfin_unknown_artist)
    val details = remember(track.artist, track.duration, unknownArtist) {
        val duration = parseTimeSpanMillis(track.duration)?.takeIf { it > 0 }?.let(::formatDuration)
        listOfNotNull(track.artist ?: unknownArtist, duration).joinToString(" · ")
    }
    // No onRemove: the library is read-only here, so the card only swipes right.
    SwipeActionCard(
        addLabel = stringResource(R.string.action_add_to_queue),
        enabled = canEnqueue,
        addStatus = queueStatus,
        onAdd = { onEnqueue(track.itemId) },
        modifier = modifier,
    ) { foregroundModifier ->
        Card(
            modifier = foregroundModifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            TrackListCardContent(
                artworkUrl = jellyfinArtworkPath(track.artworkItemId),
                titleContent = {
                    Text(text = track.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = { Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}
