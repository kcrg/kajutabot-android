package com.tryniecki.kajutabot.ui.player

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
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
import com.tryniecki.kajutabot.api.model.common.TrackResponse
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
import coil3.compose.AsyncImage

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
) {
    val ui by viewModel.playerScreenState.collectAsStateWithLifecycle()
    val favoritesUi by favoritesViewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel, context) {
        viewModel.controlMessages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    PlayerScreen(
        ui = ui,
        onDiscordSelectionOpen = onDiscordSelectionOpen,
        onSkip = viewModel::skip,
        onStop = viewModel::stop,
        onRepeatToggle = { viewModel.setRepeat(ui.queue?.isRepeatEnabled != true) },
        onRadioToggle = viewModel::toggleRadio,
        onRemoveEntry = viewModel::removeEntry,
        onMoveEntry = viewModel::moveEntry,
        onClearQueue = viewModel::clearQueue,
        isFavorite = favoritesViewModel::isFavorite,
        onToggleFavorite = favoritesViewModel::toggle,
        favoritesBusy = favoritesUi.isMutating || favoritesUi.isLoading,
        onDismissMessage = viewModel::dismissMessage,
        onAddTrackOpen = onAddTrackOpen,
        onRetryQueue = {
            ui.selectedGuildId?.let(viewModel::refreshQueue)
        },
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

    LaunchedEffect(viewModel) {
        viewModel.trackAdded.collect { onClose() }
    }

    AddTrackScreen(
        ui = ui,
        onClose = onClose,
        onQueryChange = viewModel::setSearchQuery,
        onSearchSourceChange = viewModel::setSearchSource,
        onSubmit = viewModel::submitSmartInput,
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
    onMoveEntry: (String, Int, Long) -> Unit,
    onClearQueue: () -> Unit,
    isFavorite: (TrackResponse) -> Boolean,
    onToggleFavorite: (TrackResponse) -> Unit,
    favoritesBusy: Boolean,
    onDismissMessage: () -> Unit,
    onAddTrackOpen: () -> Unit,
    onRetryQueue: () -> Unit = {},
) {
    val motion = MaterialTheme.motionScheme
    var confirmStop by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val showFloatingActions = rememberScrollAwareFabVisible(listState)
    var draggingEntryId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var dragTargetIndex by remember { mutableStateOf(-1) }
    var dragExpectedVersion by remember { mutableStateOf<Long?>(null) }
    var previewOrder by remember(ui.queue?.guildId, ui.queue?.version) {
        mutableStateOf<List<QueueEntryResponse>?>(null)
    }
    LaunchedEffect(ui.error) {
        if (ui.error != null) previewOrder = null
    }
    Scaffold(
        //topBar = { TopAppBar(title = { Text("Odtwarzacz") }) },
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
                            contentDescription = "Zmień serwer i kanał głosowy"
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
                            contentDescription = "Dodaj utwór",
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
                                text = ui.error ?: ui.info.orEmpty(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = onDismissMessage) { Text("OK") }
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
                            message = ui.queueLoadError ?: "Nie udało się pobrać stanu odtwarzacza.",
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
                        "Kolejka",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!ui.queue?.pendingEntries.isNullOrEmpty()) {
                        IconButton(onClick = { confirmClear = true }, enabled = !ui.isMutating) {
                            Icon(
                                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_x_outline),
                                contentDescription = "Wyczyść kolejkę",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            val pending = previewOrder ?: ui.queue?.pendingEntries.orEmpty()
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
                    val dragged = draggingEntryId == entry.entryId
                    val target = dragTargetIndex == pending.indexOfFirst { it.entryId == entry.entryId }
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
                                                contentDescription = "Przeciągnij, aby zmienić pozycję utworu ${entry.track.title}"
                                                val index = pending.indexOfFirst { it.entryId == entry.entryId }
                                                customActions = listOf(
                                                    CustomAccessibilityAction("Przenieś w górę") {
                                                        if (index > 0 && !ui.isMutating && ui.queue != null) {
                                                            onMoveEntry(entry.entryId, index, ui.queue.version)
                                                            true
                                                        } else false
                                                    },
                                                    CustomAccessibilityAction("Przenieś w dół") {
                                                        if (index in 0 until pending.lastIndex && !ui.isMutating && ui.queue != null) {
                                                            onMoveEntry(entry.entryId, index + 2, ui.queue.version)
                                                            true
                                                        } else false
                                                    },
                                                )
                                            }
                                            .pointerInput(entry.entryId, pending.size, ui.queue?.version, ui.isMutating) {
                                                if (ui.isMutating || pending.size < 2) return@pointerInput
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        draggingEntryId = entry.entryId
                                                        dragOffsetPx = 0f
                                                        dragTargetIndex = pending.indexOfFirst { it.entryId == entry.entryId }
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
                                                            .filter { info -> pending.any { it.entryId == info.key } }
                                                            .minByOrNull { info ->
                                                                kotlin.math.abs(center - (info.offset + info.size / 2f))
                                                            }
                                                        if (closest != null) {
                                                            dragTargetIndex = pending.indexOfFirst { it.entryId == closest.key }
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        val sourceIndex = pending.indexOfFirst { it.entryId == entry.entryId }
                                                        val newIndex = dragTargetIndex
                                                        val version = dragExpectedVersion
                                                        if (sourceIndex >= 0 && newIndex >= 0 && sourceIndex != newIndex && version != null) {
                                                            previewOrder = pending.toMutableList().apply {
                                                                add(newIndex, removeAt(sourceIndex))
                                                            }
                                                            onMoveEntry(entry.entryId, newIndex + 1, version)
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
                                                text = "${pending.indexOfFirst { it.entryId == entry.entryId } + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    TrackArtwork(
                                        imageUrl = entry.track.thumbnailUrl,
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
                                                contentDescription = "Usuń z kolejki",
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
                title = { Text("Zatrzymać odtwarzanie?") },
                text = { Text("Utwór zostanie przerwany, a bot opuści kanał głosowy.") },
                confirmButton = {
                    TextButton(onClick = { confirmStop = false; onStop() }, enabled = !ui.isMutating) {
                        Text("Zatrzymaj", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Anuluj") } },
            )
        }
        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("Wyczyścić kolejkę?") },
                text = { Text("Wszystkie oczekujące utwory zostaną usunięte. Aktualny utwór będzie grał dalej.") },
                confirmButton = {
                    TextButton(onClick = { confirmClear = false; onClearQueue() }, enabled = !ui.isMutating) {
                        Text("Wyczyść", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Anuluj") } },
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
    isFavorite: (TrackResponse) -> Boolean,
    onToggleFavorite: (TrackResponse) -> Unit,
    favoritesBusy: Boolean,
    onRepeatToggle: () -> Unit,
    onRadioToggle: () -> Unit,
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
                        NowPlayingArtwork(slide)

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "TERAZ ODTWARZANE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            // Fixed two-line slot: 1-line and 2-line titles occupy
                            // the same height at any font scale, so the card never
                            // jumps on track -> track.
                            Text(
                                text = slide.title,
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
                        contentDescription = "Zatrzymaj",
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
                        contentDescription = "Pomiń",
                    )
                }
                TonalToggleIconButton(
                    checked = repeatEnabled,
                    onCheckedChange = { onRepeatToggle() },
                    iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_repeat_outline,
                    checkedContentDescription = "Wyłącz powtarzanie",
                    uncheckedContentDescription = "Włącz powtarzanie",
                    enabled = !playbackControlsBlocked && queue != null,
                )
                TonalToggleIconButton(
                    checked = radioEnabled,
                    onCheckedChange = { onRadioToggle() },
                    iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_radio_outline,
                    checkedContentDescription = "Wyłącz radio",
                    uncheckedContentDescription = "Włącz radio",
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
    val latestSlide by rememberUpdatedState(slide)
    var displayedSlide by remember { mutableStateOf(slide) }

    // Color-backed glows can transition immediately. Image-backed glows first
    // warm Coil's cache at the real render size so the old glow stays visible
    // until the new artwork is actually ready, avoiding a fade-through-black gap.
    val targetAccent = slide.artworkAccentColor.toArtworkAccentColor()
    val preloadImage = slide != displayedSlide &&
        slide.hasTrack &&
        slide.thumbnailUrl != null &&
        targetAccent == null

    LaunchedEffect(slide, preloadImage) {
        if (!preloadImage) displayedSlide = slide
    }

    Box(modifier = modifier) {
        if (preloadImage) {
            val candidate = slide
            AsyncImage(
                model = candidate.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0f),
                onSuccess = {
                    if (latestSlide == candidate) displayedSlide = candidate
                },
                onError = {
                    // Do not pin the previous track forever if artwork fails.
                    if (latestSlide == candidate) displayedSlide = candidate
                },
            )
        }

        Crossfade(
            targetState = displayedSlide,
            animationSpec = motion.slowEffectsSpec(),
            modifier = Modifier.fillMaxSize(),
            label = "nowPlayingGlow",
        ) { current ->
            if (!current.hasTrack || current.thumbnailUrl == null) return@Crossfade

            val accent = current.artworkAccentColor.toArtworkAccentColor()
            if (accent != null) {
                AccentArtworkGlow(
                    accent = accent,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ImageArtworkGlow(
                    imageUrl = current.thumbnailUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private fun String?.toArtworkAccentColor(): Color? =
    this
        ?.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }
        ?.let { Color(0xFF000000L or it.substring(1).toLong(16)) }

@Composable
private fun NowPlayingArtwork(slide: NowPlayingSlide) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        contentAlignment = Alignment.Center,
    ) {
        TrackArtwork(
            imageUrl = slide.thumbnailUrl,
            modifier = Modifier.fillMaxSize(),
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
        // Główny ambient wychodzący poza artwork ze wszystkich stron.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .blur(
                    radius = 44.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                )
                .background(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to accent.copy(alpha = 0.42f),
                            0.42f to accent.copy(alpha = 0.26f),
                            0.72f to accent.copy(alpha = 0.10f),
                            1.00f to Color.Transparent,
                        ),
                    ),
                ),
        )

        // Lekko mocniejsze światło w dolnej połowie.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .fillMaxHeight(0.62f)
                .align(Alignment.BottomCenter)
                .offset(y = 12.dp)
                .blur(
                    radius = 34.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                )
                .background(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.00f to accent.copy(alpha = 0.38f),
                            0.55f to accent.copy(alpha = 0.16f),
                            1.00f to Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun ImageArtworkGlow(
    imageUrl: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .blur(
                    radius = 46.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                )
                .alpha(0.40f),
        )

        // Druga, słabsza warstwa daje bardziej miękkie wygaszenie.
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.78f)
                .align(Alignment.Center)
                .blur(
                    radius = 60.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                )
                .alpha(0.18f),
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
            Text("Kolejka jest pusta", style = MaterialTheme.typography.titleMedium)
            Text(
                "Użyj przycisku wyszukiwania, aby dodać utwory.",
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
    message: String,
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
                "Nie udało się pobrać stanu odtwarzacza",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) { Text("Spróbuj ponownie") }
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
