package com.tryniecki.kajutabot.ui.player

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.tryniecki.kajutabot.ui.components.TrackArtwork
import com.tryniecki.kajutabot.ui.favorites.FavoriteTrackButton
import com.tryniecki.kajutabot.ui.favorites.FavoritesViewModel
import com.tryniecki.kajutabot.ui.theme.fadeThrough
import com.tryniecki.kajutabot.ui.theme.forwardSharedAxisX
import coil3.compose.AsyncImage

@Composable
fun PlayerRoute(
    viewModel: PlayerViewModel,
    favoritesViewModel: FavoritesViewModel,
    onAddTrackOpen: () -> Unit,
) {
    val ui by viewModel.playerScreenState.collectAsStateWithLifecycle()
    val favoritesUi by favoritesViewModel.ui.collectAsStateWithLifecycle()

    PlayerScreen(
        ui = ui,
        onPickerOpen = { viewModel.setShowPicker(true) },
        onPickerDismiss = { viewModel.setShowPicker(false) },
        onGuildSelect = viewModel::selectGuild,
        onChannelSelect = viewModel::selectChannel,
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
    onPickerOpen: () -> Unit,
    onPickerDismiss: () -> Unit,
    onGuildSelect: (String) -> Unit,
    onChannelSelect: (String) -> Unit,
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
) {
    val motion = MaterialTheme.motionScheme
    var confirmStop by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
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
            FloatingActionButton(onClick = onAddTrackOpen) {
                Icon(
                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_search_outline),
                    contentDescription = "Dodaj utwór",
                )
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
            item {
                val label = if (ui.selectedGuildId != null) {
                    val guild = ui.selectedGuild?.name ?: "Serwer"
                    val channel = ui.selectedChannel?.name
                    if (channel != null) "$guild • $channel" else guild
                } else {
                    "Wybierz serwer i kanał"
                }
                AssistChip(
                    onClick = onPickerOpen,
                    label = { Text(label) },
                    leadingIcon = {
                        GuildAvatar(
                            iconUrl = ui.selectedGuild?.iconUrl,
                            modifier = Modifier.size(20.dp),
                            iconSize = 14.dp,
                        )
                    },
                )
            }

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
                if (ui.isLoadingGuilds && ui.guilds.isEmpty()) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    NowPlayingCard(
                        queue = ui.queue,
                        isMutating = ui.isMutating,
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
            if (ui.isLoadingQueue && ui.queue == null) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (pending.isEmpty()) {
                item { EmptyQueueCard() }
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

        if (ui.showGuildPicker) {
            GuildChannelDialog(
                guilds = ui.guilds,
                channels = ui.voiceChannels,
                selectedGuildId = ui.selectedGuildId,
                selectedChannelId = ui.selectedVoiceChannelId,
                onGuildSelect = onGuildSelect,
                onChannelSelect = onChannelSelect,
                onDismiss = onPickerDismiss,
            )
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
    isMutating: Boolean,
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
            // Track -> track keeps a directional shared axis; idle <-> playing
            // has no "next" semantics, so it uses a calm fade-through instead.
            // Both rely on the reserved title/flags slots below: equal heights
            // mean no card jump on track change, and the built-in size animation
            // only runs for genuine idle <-> playing height deltas.
            AnimatedContent(
                targetState = nowPlayingSlide(queue),
                transitionSpec = {
                    if (initialState.hasTrack == targetState.hasTrack) {
                        forwardSharedAxisX(
                            fadeSpec = motion.defaultEffectsSpec(),
                            slideSpec = motion.defaultSpatialSpec(),
                        )
                    } else {
                        fadeThrough(
                            enterSpec = motion.defaultEffectsSpec(),
                            exitSpec = motion.fastEffectsSpec(),
                        )
                    }
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

            // Live flags update outside the track transition. The line is always
            // reserved (non-breaking space when empty) so toggling radio/repeat
            // never changes the card height.
            val flags = buildList {
                if (radioEnabled) add("Radio włączone")
                if (repeatEnabled) add("Powtarzanie")
            }
            if (queue?.nowPlaying != null) {
                Text(
                    text = flags.joinToString(" • ").ifEmpty { NBSP },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 1,
                    maxLines = 1,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedIconButton(
                    onClick = onStop,
                    enabled = !isMutating && queue?.nowPlaying != null,
                ) {
                    Icon(
                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_player_stop_outline),
                        contentDescription = "Zatrzymaj",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                FilledIconButton(
                    onClick = onSkip,
                    enabled = !isMutating && queue?.nowPlaying != null,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_player_skip_forward_outline),
                        contentDescription = "Pomiń",
                    )
                }
                FilledTonalIconToggleButton(
                    checked = repeatEnabled,
                    onCheckedChange = { onRepeatToggle() },
                    enabled = !isMutating && queue != null,
                ) {
                    Icon(
                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_repeat_outline),
                        contentDescription = if (repeatEnabled) {
                            "Wyłącz powtarzanie"
                        } else {
                            "Włącz powtarzanie"
                        },
                    )
                }
                FilledTonalIconToggleButton(
                    checked = radioEnabled,
                    onCheckedChange = { onRadioToggle() },
                    enabled = !isMutating && queue != null,
                ) {
                    Icon(
                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_radio_outline),
                        contentDescription = if (radioEnabled) {
                            "Wyłącz radio"
                        } else {
                            "Włącz radio"
                        },
                    )
                }
                queue?.nowPlaying?.let { track ->
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
private fun NowPlayingArtwork(slide: NowPlayingSlide) {
    val accent = remember(slide.artworkAccentColor) {
        slide.artworkAccentColor
            ?.takeIf { it.matches(Regex("#[0-9a-fA-F]{6}")) }
            ?.let { Color(0xFF000000L or it.substring(1).toLong(16)) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        contentAlignment = Alignment.Center,
    ) {
        if (slide.hasTrack && slide.thumbnailUrl != null) {
            if (accent != null) {
                AccentArtworkGlow(
                    accent = accent,
                    modifier = Modifier
                        .matchParentSize()
                        .align(Alignment.Center),
                )
            } else {
                ImageArtworkGlow(
                    imageUrl = slide.thumbnailUrl,
                    modifier = Modifier
                        .matchParentSize()
                        .align(Alignment.Center),
                )
            }
        }

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
private fun EmptyQueueCard() {
    Card(
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
private fun GuildChannelDialog(
    guilds: List<DiscordGuildResponse>,
    channels: List<DiscordVoiceChannelResponse>,
    selectedGuildId: String?,
    selectedChannelId: String?,
    onGuildSelect: (String) -> Unit,
    onChannelSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wybierz serwer i kanał") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { Text("Serwer", style = MaterialTheme.typography.labelLarge) }
                if (guilds.isEmpty()) {
                    item { Text("Brak dostępnych serwerów.") }
                }
                items(guilds, key = { it.id }) { guild ->
                    FilterChip(
                        selected = guild.id == selectedGuildId,
                        onClick = { onGuildSelect(guild.id) },
                        label = { Text(guild.name) },
                    )
                }
                item {
                    Spacer(Modifier.size(8.dp))
                    Text("Kanał głosowy", style = MaterialTheme.typography.labelLarge)
                }
                if (selectedGuildId == null) {
                    item { Text("Najpierw wybierz serwer.") }
                } else if (channels.isEmpty()) {
                    item { Text("Brak kanałów głosowych.") }
                }
                items(channels, key = { it.id }) { channel ->
                    FilterChip(
                        selected = channel.id == selectedChannelId,
                        onClick = { onChannelSelect(channel.id) },
                        label = { Text("${channel.name} (${channel.userCount})") },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij") } },
    )
}

internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** Non-breaking space keeping reserved single-line slots at full line height. */
private const val NBSP = " "
