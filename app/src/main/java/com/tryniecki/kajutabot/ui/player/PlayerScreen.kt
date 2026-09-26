package com.tryniecki.kajutabot.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.queue.QueueEntryResponse
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.ui.components.GuildAvatar
import com.tryniecki.kajutabot.ui.components.SkeletonBlock
import com.tryniecki.kajutabot.ui.components.rememberScrollAwareFabVisible
import com.tryniecki.kajutabot.ui.components.rememberSkeletonPulse
import com.tryniecki.kajutabot.ui.components.TrackArtwork
import com.tryniecki.kajutabot.ui.components.TonalToggleIconButton
import com.tryniecki.kajutabot.ui.favorites.FavoriteTrackButton
import com.tryniecki.kajutabot.ui.favorites.FavoritesViewModel
import com.tryniecki.kajutabot.ui.theme.fadeThrough
import com.tryniecki.kajutabot.ui.theme.forwardSharedAxisY
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.ui.text.asString

private val artworkAccentColorRegex = Regex("#[0-9a-fA-F]{6}")

private enum class PlayerSurfaceState {
    LOADING,
    ERROR,
    READY,
}

@Composable
fun PlayerRoute(
    viewModel: PlayerViewModel,
    favoritesViewModel: FavoritesViewModel,
    onAddTrackOpen: () -> Unit,
    onDiscordSelectionOpen: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val ui by viewModel.playerScreenState.collectAsStateWithLifecycle()
    val favoritesUi by favoritesViewModel.ui.collectAsStateWithLifecycle()

    PlayerScreen(
        ui = ui,
        onDiscordSelectionOpen = onDiscordSelectionOpen,
        onSkip = viewModel::skip,
        onStop = viewModel::stop,
        onRepeatToggle = { viewModel.setRepeat(ui.queue?.isRepeatEnabled != true) },
        onRadioToggle = viewModel::toggleRadio,
        onRemoveEntry = viewModel::removeEntry,
        onSwapEntries = viewModel::swapEntries,
        onClearQueue = viewModel::clearQueue,
        isFavorite = favoritesViewModel::isFavorite,
        onToggleFavorite = favoritesViewModel::toggle,
        favoritesBusy = favoritesUi.isMutating || favoritesUi.isLoading,
        onDismissMessage = viewModel::dismissMessage,
        onAddTrackOpen = onAddTrackOpen,
        onRetryQueue = {
            ui.selectedGuildId?.let(viewModel::refreshQueue)
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

@Composable
fun AddTrackRoute(
    viewModel: PlayerViewModel,
    favoritesViewModel: FavoritesViewModel,
    onClose: () -> Unit,
) {
    val ui by viewModel.addTrackState.collectAsStateWithLifecycle()
    val favoritesUi by favoritesViewModel.ui.collectAsStateWithLifecycle()

    var savedQuery by rememberSaveable { mutableStateOf(ui.searchQuery) }
    var savedSourceName by rememberSaveable { mutableStateOf(ui.searchSource.name) }
    val savedSource = SearchSourceOption.entries.firstOrNull { it.name == savedSourceName }
        ?: SearchSourceOption.YOUTUBE

    // Rehydrate ViewModel search input after process recreation. The route's
    // rememberSaveable state survives because Navigation 3 owns a saveable-state holder.
    LaunchedEffect(Unit) {
        if (viewModel.addTrackState.value.searchQuery != savedQuery) {
            viewModel.setSearchQuery(savedQuery)
        }
        if (viewModel.addTrackState.value.searchSource != savedSource) {
            viewModel.setSearchSource(savedSource)
        }
    }

    LaunchedEffect(ui.addTrackCompleted) {
        if (ui.addTrackCompleted) {
            viewModel.acknowledgeAddTrackCompleted()
            onClose()
        }
    }

    AddTrackScreen(
        ui = ui.copy(searchQuery = savedQuery, searchSource = savedSource),
        onClose = onClose,
        onQueryChange = { query ->
            savedQuery = query
            viewModel.setSearchQuery(query)
        },
        onSearchSourceChange = { source ->
            savedSourceName = source.name
            viewModel.setSearchSource(source)
        },
        onSubmit = viewModel::submitSmartInput,
        onHistoryClick = { query ->
            savedQuery = query
            viewModel.searchFromHistory(query)
        },
        onResultClick = viewModel::enqueueSearchResult,
        isFavorite = favoritesViewModel::isFavorite,
        onToggleFavorite = favoritesViewModel::toggle,
        favoritesBusy = favoritesUi.isMutating || favoritesUi.isLoading,
        onDismissMessage = viewModel::dismissMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    ui: PlayerScreenState,
    onDiscordSelectionOpen: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onRepeatToggle: () -> Unit,
    onRadioToggle: () -> Unit,
    onRemoveEntry: (String) -> Unit,
    onSwapEntries: (String, String, Long) -> Boolean,
    onClearQueue: () -> Unit,
    isFavorite: (PlaybackTrackResponse) -> Boolean,
    onToggleFavorite: (PlaybackTrackResponse) -> Unit,
    favoritesBusy: Boolean,
    onDismissMessage: () -> Unit,
    onAddTrackOpen: () -> Unit,
    onRetryQueue: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val motion = MaterialTheme.motionScheme
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val showFloatingActions = rememberScrollAwareFabVisible(listState)
    var draggingEntryId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var dragTargetIndex by remember { mutableStateOf(-1) }
    var dragExpectedVersion by remember { mutableStateOf<Long?>(null) }
    var previewOrder by remember(ui.queue?.guildId) {
        mutableStateOf<List<QueueEntryResponse>?>(null)
    }
    LaunchedEffect(ui.isMutating, ui.error) {
        if (!ui.isMutating || ui.error != null) previewOrder = null
    }
    val pending = previewOrder ?: ui.queue?.pendingEntries.orEmpty()
    val pendingIndexById = remember(pending) {
        buildMap(pending.size) {
            pending.forEachIndexed { index, entry ->
                if (!containsKey(entry.entryId)) put(entry.entryId, index)
            }
        }
    }
    val pendingEntryIds = pendingIndexById.keys
    val changeServerChannelDesc = stringResource(R.string.player_change_server_channel)
    Scaffold(
        floatingActionButton = {
            AnimatedVisibility(
                visible = showFloatingActions,
                enter = fadeIn(animationSpec = motion.fastEffectsSpec()) +
                        scaleIn(animationSpec = motion.fastSpatialSpec(), initialScale = 0.82f),
                exit = fadeOut(animationSpec = motion.fastEffectsSpec()) +
                        scaleOut(animationSpec = motion.fastSpatialSpec(), targetScale = 0.82f),
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallFloatingActionButton(
                        onClick = onDiscordSelectionOpen,
                        modifier = Modifier.semantics {
                            contentDescription = changeServerChannelDesc
                        },
                    ) {
                        if (ui.selectedGuild != null) {
                            GuildAvatar(
                                iconUrl = ui.selectedGuild.iconUrl,
                                modifier = Modifier.size(32.dp),
                                iconSize = 18.dp,
                            )
                        } else {
                            Icon(
                                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_brand_discord_outline),
                                contentDescription = null,
                            )
                        }
                    }
                    FloatingActionButton(onClick = onAddTrackOpen) {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_search_outline),
                            contentDescription = stringResource(R.string.action_add_track),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (ui.error != null || ui.info != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (ui.error != null) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = (ui.error ?: ui.info)?.asString().orEmpty(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = onDismissMessage) { Text(stringResource(R.string.action_ok)) }
                        }
                    }
                }
            }

            item {
                val surfaceState = when {
                    ui.queue == null && ui.queueLoadState == QueueLoadState.ERROR -> PlayerSurfaceState.ERROR
                    ui.isInitialContentLoading -> PlayerSurfaceState.LOADING
                    else -> PlayerSurfaceState.READY
                }
                AnimatedContent(
                    targetState = surfaceState,
                    transitionSpec = {
                        fadeThrough(
                            enterSpec = motion.defaultEffectsSpec(),
                            exitSpec = motion.fastEffectsSpec(),
                        ).using(SizeTransform { _, _ -> motion.defaultSpatialSpec() })
                    },
                    label = "playerInitialState",
                ) { state ->
                    when (state) {
                        PlayerSurfaceState.LOADING -> NowPlayingSkeletonCard()
                        PlayerSurfaceState.ERROR -> PlayerLoadErrorCard(
                            message = ui.queueLoadError,
                            onRetry = onRetryQueue,
                        )
                        PlayerSurfaceState.READY -> NowPlayingCard(
                            queue = ui.queue,
                            presentedNowPlaying = ui.presentedNowPlaying
                                ?: ui.queue?.nowPlayingPresentationOrNull(),
                            isMutating = ui.isMutating,
                            activeControlAction = ui.activeControlAction,
                            onSkip = onSkip,
                            onStop = { confirmStop = true },
                            isFavorite = isFavorite,
                            onToggleFavorite = onToggleFavorite,
                            favoritesBusy = favoritesBusy,
                            onRepeatToggle = onRepeatToggle,
                            onRadioToggle = onRadioToggle,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.player_queue_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!ui.queue?.pendingEntries.isNullOrEmpty()) {
                        IconButton(onClick = { confirmClear = true }, enabled = !ui.isMutating) {
                            Icon(
                                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_x_outline),
                                contentDescription = stringResource(R.string.player_clear_queue),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            if (ui.isInitialContentLoading) {
                item(key = "queue-loading") {
                    QueueSkeleton(
                        modifier = Modifier.animateItem(
                            fadeInSpec = motion.fastEffectsSpec(),
                            fadeOutSpec = motion.defaultEffectsSpec(),
                            placementSpec = motion.defaultSpatialSpec(),
                        ),
                    )
                }
            } else if (pending.isEmpty()) {
                item(key = "queue-empty") {
                    EmptyQueueCard(
                        modifier = Modifier.animateItem(
                            fadeInSpec = motion.defaultEffectsSpec(),
                            fadeOutSpec = motion.fastEffectsSpec(),
                            placementSpec = motion.defaultSpatialSpec(),
                        ),
                    )
                }
            } else {
                items(pending, key = { it.entryId }) { entry ->
                    val entryIndex = pendingIndexById[entry.entryId] ?: -1
                    val dragged = draggingEntryId == entry.entryId
                    val target = dragTargetIndex == entryIndex
                    val dragDescription = stringResource(R.string.player_drag_track_a11y, entry.track.title)
                    val moveUpDescription = stringResource(R.string.action_move_up)
                    val moveDownDescription = stringResource(R.string.action_move_down)
                    Card(
                        modifier = Modifier
                            .zIndex(if (dragged) 1f else 0f)
                            .graphicsLayer { translationY = if (dragged) dragOffsetPx else 0f }
                            .shadow(if (dragged) 8.dp else 0.dp, RoundedCornerShape(12.dp))
                            .animateItem(
                                fadeInSpec = motion.fastEffectsSpec(),
                                fadeOutSpec = motion.fastEffectsSpec(),
                                placementSpec = motion.fastSpatialSpec(),
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (target && !dragged) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        ListItem(
                            verticalAlignment = Alignment.CenterVertically,
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .offset(x = -8.dp)
                                            .testTag("queue-drag-${entry.entryId}")
                                            .semantics {
                                                contentDescription = dragDescription
                                                val index = entryIndex
                                                customActions = listOf(
                                                    CustomAccessibilityAction(moveUpDescription) {
                                                        if (index > 0 && !ui.isMutating && ui.queue != null) {
                                                            onSwapEntries(entry.entryId, pending[index - 1].entryId, ui.queue.version)
                                                        } else false
                                                    },
                                                    CustomAccessibilityAction(moveDownDescription) {
                                                        if (index in 0 until pending.lastIndex && !ui.isMutating && ui.queue != null) {
                                                            onSwapEntries(entry.entryId, pending[index + 1].entryId, ui.queue.version)
                                                        } else false
                                                    },
                                                )
                                            }
                                            .pointerInput(entry.entryId, pendingIndexById, ui.queue?.version, ui.isMutating) {
                                                if (ui.isMutating || pending.size < 2) return@pointerInput
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        draggingEntryId = entry.entryId
                                                        dragOffsetPx = 0f
                                                        dragTargetIndex = entryIndex
                                                        dragExpectedVersion = ui.queue?.version
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        dragOffsetPx += amount.y
                                                        val source = listState.layoutInfo.visibleItemsInfo
                                                            .firstOrNull { it.key == entry.entryId }
                                                            ?: return@detectDragGesturesAfterLongPress
                                                        val center = source.offset + source.size / 2f + dragOffsetPx
                                                        val closest = listState.layoutInfo.visibleItemsInfo
                                                            .filter { info ->
                                                                val key = info.key as? String
                                                                key != null && key in pendingEntryIds
                                                            }
                                                            .minByOrNull { info ->
                                                                kotlin.math.abs(center - (info.offset + info.size / 2f))
                                                            }
                                                        if (closest != null) {
                                                            val closestEntryId = closest.key as? String
                                                            dragTargetIndex = closestEntryId
                                                                ?.let { pendingIndexById[it] }
                                                                ?: -1
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        val targetEntryId = pending.getOrNull(dragTargetIndex)?.entryId
                                                        val version = dragExpectedVersion
                                                        if (targetEntryId != null && version != null) {
                                                            val swapped = swappedQueueEntries(pending, entry.entryId, targetEntryId)
                                                            if (swapped != null && onSwapEntries(entry.entryId, targetEntryId, version)) {
                                                                previewOrder = swapped
                                                            }
                                                        }
                                                        draggingEntryId = null
                                                        dragOffsetPx = 0f
                                                        dragTargetIndex = -1
                                                        dragExpectedVersion = null
                                                    },
                                                    onDragCancel = {
                                                        draggingEntryId = null
                                                        dragOffsetPx = 0f
                                                        dragTargetIndex = -1
                                                        dragExpectedVersion = null
                                                    },
                                                )
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_grip_vertical_outline),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Text(
                                                text = "${entryIndex + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    TrackArtwork(
                                        imageUrl = entry.track.artworkUrl,
                                        modifier = Modifier.size(56.dp),
                                    )
                                }
                            },
                            supportingContent = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(formatDuration(entry.track.durationMilliseconds))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FavoriteTrackButton(
                                            track = entry.track,
                                            checked = isFavorite(entry.track),
                                            enabled = !favoritesBusy,
                                            onToggle = onToggleFavorite,
                                        )
                                        IconButton(
                                            onClick = { onRemoveEntry(entry.entryId) },
                                            enabled = !ui.isMutating,
                                        ) {
                                            Icon(
                                                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_trash_outline),
                                                contentDescription = stringResource(R.string.action_remove_from_queue),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            },
                        ) {
                            Text(
                                entry.track.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (confirmStop) {
            AlertDialog(
                onDismissRequest = { confirmStop = false },
                title = { Text(stringResource(R.string.player_stop_dialog_title)) },
                text = { Text(stringResource(R.string.player_stop_dialog_body)) },
                confirmButton = {
                    TextButton(onClick = { confirmStop = false; onStop() }, enabled = !ui.isMutating) {
                        Text(stringResource(R.string.action_stop), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text(stringResource(R.string.player_clear_dialog_title)) },
                text = { Text(stringResource(R.string.player_clear_dialog_body)) },
                confirmButton = {
                    TextButton(onClick = { confirmClear = false; onClearQueue() }, enabled = !ui.isMutating) {
                        Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

@Composable
private fun NowPlayingCard(
    queue: QueueSnapshotResponse?,
    presentedNowPlaying: NowPlayingPresentation?,
    isMutating: Boolean,
    activeControlAction: PlayerControlAction?,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    isFavorite: (PlaybackTrackResponse) -> Boolean,
    onToggleFavorite: (PlaybackTrackResponse) -> Unit,
    favoritesBusy: Boolean,
    onRepeatToggle: () -> Unit,
    onRadioToggle: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val repeatEnabled = queue?.isRepeatEnabled == true
    val radioEnabled = queue?.radio?.isEnabled == true
    val motion = MaterialTheme.motionScheme
    val currentSlide = nowPlayingSlide(
        presentation = presentedNowPlaying,
        hasQueue = queue != null,
    )
    // Queue mutations such as reorder/remove still block playback controls, but a
    // control mutation must not make its sibling buttons flash disabled.
    val playbackControlsBlocked = isMutating && activeControlAction == null
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Artwork, title, meta and progress transition as one unit keyed by
            // playback identity (track + start moment), so a repeated track counts
            // as a new playback and never animates 95% -> 2% on one progress bar.
            // Track -> track keeps a vertical directional shared axis; idle <-> playing
            // has no "next" semantics, so it uses a calm fade-through instead.
            // Both rely on the reserved title/flags slots below: equal heights
            // mean no card jump on track change, and the built-in size animation
            // only runs for genuine idle <-> playing height deltas.
            Box {
                SmoothArtworkGlow(
                    slide = currentSlide,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .align(Alignment.TopCenter),
                )

                AnimatedContent(
                    targetState = currentSlide,
                    contentKey = { it.identity },
                    transitionSpec = {
                        val contentTransition = if (initialState.hasTrack == targetState.hasTrack) {
                            forwardSharedAxisY(
                                fadeSpec = motion.defaultEffectsSpec(),
                                slideSpec = motion.defaultSpatialSpec(),
                            )
                        } else {
                            fadeThrough(
                                enterSpec = motion.defaultEffectsSpec(),
                                exitSpec = motion.fastEffectsSpec(),
                            )
                        }
                        contentTransition.using(
                            SizeTransform { _, _ -> motion.defaultSpatialSpec() },
                        )
                    },
                    label = "nowPlaying",
                ) { slide ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        NowPlayingArtwork(
                            slide = slide,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(R.string.player_now_playing),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            // Fixed two-line slot: 1-line and 2-line titles occupy
                            // the same height at any font scale, so the card never
                            // jumps on track -> track.
                            Text(
                                text = if (slide.hasTrack) slide.title else stringResource(R.string.player_nothing_playing),
                                modifier = if (slide.hasTrack) {
                                    Modifier.playerTitleSharedBounds(
                                        playbackIdentity = slide.identity,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    )
                                } else {
                                    Modifier
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                minLines = 2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (slide.hasTrack) {
                            PlaybackProgressIndicator(
                                playbackKey = slide.identity,
                                startedAtRaw = slide.startedAt,
                                durationMs = slide.durationMs,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedIconButton(
                    onClick = onStop,
                    enabled = !playbackControlsBlocked && presentedNowPlaying != null,
                ) {
                    Icon(
                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_player_stop_outline),
                        contentDescription = stringResource(R.string.action_stop),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                FilledIconButton(
                    onClick = onSkip,
                    enabled = !playbackControlsBlocked && presentedNowPlaying != null,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_player_skip_forward_outline),
                        contentDescription = stringResource(R.string.action_skip),
                    )
                }
                TonalToggleIconButton(
                    checked = repeatEnabled,
                    onCheckedChange = { onRepeatToggle() },
                    iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_repeat_outline,
                    checkedContentDescription = stringResource(R.string.player_repeat_disable),
                    uncheckedContentDescription = stringResource(R.string.player_repeat_enable),
                    enabled = !playbackControlsBlocked && queue != null,
                )
                TonalToggleIconButton(
                    checked = radioEnabled,
                    onCheckedChange = { onRadioToggle() },
                    iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_radio_outline,
                    checkedContentDescription = stringResource(R.string.player_radio_disable),
                    uncheckedContentDescription = stringResource(R.string.player_radio_enable),
                    enabled = !playbackControlsBlocked && queue != null,
                )
                presentedNowPlaying?.track?.let { track ->
                    FavoriteTrackButton(
                        track = track,
                        checked = isFavorite(track),
                        enabled = !favoritesBusy,
                        onToggle = onToggleFavorite,
                    )
                }
            }
        }
    }
}

@Composable
private fun SmoothArtworkGlow(
    slide: NowPlayingSlide,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val fallbackAccent = lerp(
        MaterialTheme.colorScheme.primary,
        Color.White,
        0.18f,
    )

    AnimatedContent(
        targetState = slide,
        contentKey = { it.identity },
        transitionSpec = {
            fadeThrough(
                enterSpec = motion.slowEffectsSpec(),
                exitSpec = motion.slowEffectsSpec(),
            )
        },
        modifier = modifier,
        label = "nowPlayingGlow",
    ) { current ->
        if (!current.hasTrack) return@AnimatedContent

        AccentArtworkGlow(
            accent = current.artworkAccentColor.toArtworkAccentColor() ?: fallbackAccent,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun String?.toArtworkAccentColor(): Color? =
    this
        ?.takeIf { it.matches(artworkAccentColorRegex) }
        ?.let { Color(0xFF000000L or it.substring(1).toLong(16)) }

@Composable
private fun NowPlayingArtwork(
    slide: NowPlayingSlide,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        contentAlignment = Alignment.Center,
    ) {
        TrackArtwork(
            imageUrl = slide.thumbnailUrl,
            modifier = Modifier
                .fillMaxSize()
                .let { artworkModifier ->
                    if (slide.hasTrack) {
                        artworkModifier.playerArtworkSharedElement(
                            playbackIdentity = slide.identity,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    } else {
                        artworkModifier
                    }
                },
            shape = RoundedCornerShape(12.dp),
            brokenIconSize = 48.dp,
            showMissingLabel = true,
            backgroundColor = Color.Black,
        )
    }
}

@Composable
private fun AccentArtworkGlow(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .offset(y = 4.dp)
                .graphicsLayer {
                    scaleX = 1.03f
                    scaleY = 1.04f
                }
                .blur(
                    radius = 40.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                )
                .background(
                    color = accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(14.dp),
                ),
        )
    }
}

@Composable
private fun EmptyQueueCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_list_outline),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.player_queue_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.player_queue_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NowPlayingSkeletonCard() {
    val pulse = rememberSkeletonPulse()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SkeletonBlock(
                pulse = pulse,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                shape = RoundedCornerShape(12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SkeletonBlock(pulse, Modifier.width(116.dp).height(12.dp))
                SkeletonBlock(pulse, Modifier.fillMaxWidth(0.78f).height(26.dp))
                SkeletonBlock(pulse, Modifier.fillMaxWidth(0.56f).height(26.dp))
            }
            SkeletonBlock(
                pulse,
                Modifier.fillMaxWidth().height(6.dp),
                RoundedCornerShape(999.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(pulse, Modifier.size(40.dp), RoundedCornerShape(999.dp))
                SkeletonBlock(pulse, Modifier.size(56.dp), RoundedCornerShape(999.dp))
                SkeletonBlock(pulse, Modifier.size(40.dp), RoundedCornerShape(999.dp))
                SkeletonBlock(pulse, Modifier.size(40.dp), RoundedCornerShape(999.dp))
            }
        }
    }
}

@Composable
private fun QueueSkeleton(modifier: Modifier = Modifier) {
    val pulse = rememberSkeletonPulse()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(3) { index ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SkeletonBlock(pulse, Modifier.size(56.dp), RoundedCornerShape(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        SkeletonBlock(
                            pulse,
                            Modifier
                                .fillMaxWidth(if (index == 1) 0.72f else 0.88f)
                                .height(16.dp),
                        )
                        SkeletonBlock(pulse, Modifier.width(72.dp).height(12.dp))
                    }
                    SkeletonBlock(pulse, Modifier.size(36.dp), RoundedCornerShape(999.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerLoadErrorCard(
    message: UiText?,
    onRetry: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_server_outline),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(R.string.player_load_failed).removeSuffix("."),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                message?.asString() ?: stringResource(R.string.player_load_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"

    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
