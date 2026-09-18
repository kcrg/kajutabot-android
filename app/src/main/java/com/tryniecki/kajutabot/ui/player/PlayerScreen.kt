package com.tryniecki.kajutabot.ui.player

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.ui.app.AppViewModel
import com.tryniecki.kajutabot.ui.components.GuildAvatar
import com.tryniecki.kajutabot.ui.components.TrackArtwork
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ADD_TRACK_ENTER_MS = 180
private const val ADD_TRACK_EXIT_MS = 150
private const val ADD_TRACK_CLEAR_DELAY_MS = 200L
private const val TRACK_CHANGE_MS = 180
private const val TRACK_CHANGE_EXIT_MS = 140

@Composable
fun PlayerRoute(
    container: AppContainer,
    appViewModel: AppViewModel,
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory(container)),
) {
    val ui by viewModel.ui.collectAsState()
    val pendingSharedUrl by appViewModel.pendingSharedUrl.collectAsState()
    val isAddTrackOpen by appViewModel.isAddTrackOpen.collectAsState()
    val guildId = ui.selectedGuildId
    val lifecycleOwner = LocalLifecycleOwner.current

    val playbackKey = ui.queue?.nowPlaying?.let { track ->
        playbackIdentity(track, ui.queue?.nowPlayingStartedAt)
    }

    // Truly lifecycle-aware polling: the loop only runs while STARTED, so no
    // requests happen in the background. Entering the foreground polls
    // immediately, then on the interval. Guild/playback change restarts it.
    // A one-shot expected-end refresh fires ~300 ms after the current track
    // should end; both paths share the ViewModel single-flight queue fetch.
    LaunchedEffect(guildId, playbackKey) {
        if (guildId == null) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.pollQueueOnce()
            val remaining = viewModel.ui.value.queue?.let { snapshot ->
                val track = snapshot.nowPlaying ?: return@let null
                remainingMs(
                    snapshot.nowPlayingStartedAt,
                    track.durationMilliseconds,
                    Instant.now().toEpochMilli(),
                )
            }
            val endRefresh = remaining?.let { ms ->
                launch {
                    delay(ms.coerceAtLeast(0) + EXPECTED_END_GRACE_MS)
                    viewModel.pollQueueOnce()
                }
            }
            try {
                while (true) {
                    delay(POLL_INTERVAL_MS)
                    viewModel.pollQueueOnce()
                }
            } finally {
                endRefresh?.cancel()
            }
        }
    }

    // Shared URL -> prefill the add-track modal and open it.
    LaunchedEffect(pendingSharedUrl) {
        val url = pendingSharedUrl
        if (url != null) {
            viewModel.setSearchQuery(url)
            appViewModel.setAddTrackOpen(true)
            appViewModel.clearPendingSharedUrl()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.trackAdded.collect {
            appViewModel.setAddTrackOpen(false)
        }
    }

    BackHandler(enabled = isAddTrackOpen) {
        appViewModel.setAddTrackOpen(false)
    }

    // Deferred modal cleanup: clear query/results only after the exit transition
    // finished, so closing never flashes an empty screen mid-animation.
    var modalWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(isAddTrackOpen) {
        if (isAddTrackOpen) {
            modalWasOpen = true
        } else if (modalWasOpen) {
            modalWasOpen = false
            delay(ADD_TRACK_CLEAR_DELAY_MS)
            viewModel.clearAddTrack()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PlayerScreen(
            ui = ui,
            onPickerOpen = { viewModel.setShowPicker(true) },
            onPickerDismiss = { viewModel.setShowPicker(false) },
            onGuildSelect = viewModel::selectGuild,
            onChannelSelect = viewModel::selectChannel,
            onSkip = viewModel::skip,
            onStop = viewModel::stop,
            onRepeatToggle = { viewModel.setRepeat(!(ui.queue?.isRepeatEnabled == true)) },
            onRadioToggle = viewModel::toggleRadio,
            onRemoveEntry = viewModel::removeEntry,
            onClearQueue = viewModel::clearQueue,
            onDismissMessage = viewModel::dismissMessage,
            onAddTrackOpen = { appViewModel.setAddTrackOpen(true) },
        )
        AnimatedVisibility(
            visible = isAddTrackOpen,
            enter = slideInVertically(
                animationSpec = tween(ADD_TRACK_ENTER_MS),
            ) { it } + fadeIn(tween(ADD_TRACK_ENTER_MS)),
            exit = slideOutVertically(
                animationSpec = tween(ADD_TRACK_EXIT_MS),
            ) { it } + fadeOut(tween(ADD_TRACK_EXIT_MS)),
        ) {
            AddTrackScreen(
                ui = ui,
                onClose = { appViewModel.setAddTrackOpen(false) },
                onQueryChange = viewModel::setSearchQuery,
                onSubmit = viewModel::submitSmartInput,
                onResultClick = viewModel::enqueueSearchResult,
                onDismissMessage = viewModel::dismissMessage,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    ui: PlayerUiState,
    onPickerOpen: () -> Unit,
    onPickerDismiss: () -> Unit,
    onGuildSelect: (String) -> Unit,
    onChannelSelect: (String) -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onRepeatToggle: () -> Unit,
    onRadioToggle: () -> Unit,
    onRemoveEntry: (String) -> Unit,
    onClearQueue: () -> Unit,
    onDismissMessage: () -> Unit,
    onAddTrackOpen: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Odtwarzacz") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTrackOpen) {
                Icon(
                    painter = painterResource(R.drawable.kb_ic_plus),
                    contentDescription = "Dodaj utwór",
                )
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                        onStop = onStop,
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
                        TextButton(onClick = onClearQueue, enabled = !ui.isMutating) {
                            Text("Wyczyść")
                        }
                    }
                }
            }

            val pending = ui.queue?.pendingEntries.orEmpty()
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
                    Card(
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(120),
                            fadeOutSpec = tween(120),
                            placementSpec = tween(150),
                        ),
                    ) {
                        ListItem(
                            leadingContent = {
                                TrackArtwork(
                                    imageUrl = entry.track.thumbnailUrl,
                                    modifier = Modifier.size(56.dp),
                                )
                            },
                            supportingContent = { Text(formatDuration(entry.track.durationMilliseconds)) },
                            trailingContent = {
                                TextButton(
                                    onClick = { onRemoveEntry(entry.entryId) },
                                    enabled = !ui.isMutating,
                                ) { Text("Usuń") }
                            },
                        ) {
                            Text(
                                "${entry.position}. ${entry.track.title}",
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
    }
}

@Composable
private fun NowPlayingCard(
    queue: QueueSnapshotResponse?,
    isMutating: Boolean,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onRepeatToggle: () -> Unit,
    onRadioToggle: () -> Unit,
) {
    val repeatEnabled = queue?.isRepeatEnabled == true
    val radioEnabled = queue?.radio?.isEnabled == true
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
            AnimatedContent(
                targetState = nowPlayingSlide(queue),
                transitionSpec = {
                    (fadeIn(tween(TRACK_CHANGE_MS)) +
                        slideInHorizontally(tween(TRACK_CHANGE_MS)) { (it * 0.08f).toInt() }) togetherWith
                        (fadeOut(tween(TRACK_CHANGE_EXIT_MS)) +
                            slideOutHorizontally(tween(TRACK_CHANGE_EXIT_MS)) { -(it * 0.08f).toInt() })
                },
                label = "nowPlaying",
            ) { slide ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TrackArtwork(
                        imageUrl = slide.thumbnailUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        shape = RoundedCornerShape(12.dp),
                        brokenIconSize = 48.dp,
                        showMissingLabel = true,
                        backgroundColor = Color.Black,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "TERAZ ODTWARZANE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = slide.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = slide.hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            // Live flags update outside the track transition.
            val flags = buildList {
                if (radioEnabled) add("Radio włączone")
                if (repeatEnabled) add("Powtarzanie")
            }
            if (queue?.nowPlaying != null && flags.isNotEmpty()) {
                Text(
                    text = flags.joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        painter = painterResource(R.drawable.kb_ic_player_stop),
                        contentDescription = "Zatrzymaj",
                    )
                }
                FilledIconButton(
                    onClick = onSkip,
                    enabled = !isMutating && queue?.nowPlaying != null,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.kb_ic_player_skip_forward),
                        contentDescription = "Pomiń",
                    )
                }
                FilledTonalIconToggleButton(
                    checked = repeatEnabled,
                    onCheckedChange = { onRepeatToggle() },
                    enabled = !isMutating && queue != null,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.kb_ic_repeat),
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
                        painter = painterResource(R.drawable.kb_ic_radio),
                        contentDescription = if (radioEnabled) {
                            "Wyłącz radio"
                        } else {
                            "Włącz radio"
                        },
                    )
                }
            }
        }
    }
}

/**
 * Local playback progress: anchored once per playback identity from wall clock,
 * then advanced on the monotonic clock with a small local ticker. Never touches
 * [PlayerUiState], so the rest of the screen doesn't recompose 5x per second.
 */
@Composable
private fun PlaybackProgressIndicator(
    playbackKey: String,
    startedAtRaw: String?,
    durationMs: Long,
) {
    val anchor = remember(playbackKey) {
        PlaybackProgressAnchor(
            positionAtAnchorMs = initialPositionMs(
                startedAtRaw,
                durationMs,
                Instant.now().toEpochMilli(),
            ),
            elapsedRealtimeAnchorMs = SystemClock.elapsedRealtime(),
        )
    }
    var nowElapsedRealtime by remember(playbackKey) {
        mutableLongStateOf(anchor.elapsedRealtimeAnchorMs)
    }
    LaunchedEffect(playbackKey) {
        while (true) {
            delay(PROGRESS_TICK_MS)
            nowElapsedRealtime = SystemClock.elapsedRealtime()
        }
    }
    val positionMs = anchor.positionAtAnchorMs?.let {
        currentPositionMs(it, anchor.elapsedRealtimeAnchorMs, nowElapsedRealtime, durationMs)
    }
    val fraction = if (positionMs == null) 0f else progressFraction(positionMs, durationMs)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = PROGRESS_TICK_MS.toInt(), easing = LinearEasing),
        label = "playbackProgress",
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearWavyProgressIndicator(
            progress = { animatedFraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "${formatPlaybackElapsed(positionMs)} / ${formatPlaybackTotal(durationMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatPlaybackElapsed(positionMs: Long?): String =
    if (positionMs == null) "--:--" else formatDuration(positionMs)

private fun formatPlaybackTotal(durationMs: Long): String =
    if (durationMs <= 0) "--:--" else formatDuration(durationMs)

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
                painter = painterResource(R.drawable.kb_ic_playlist),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Kolejka jest pusta", style = MaterialTheme.typography.titleMedium)
            Text(
                "Użyj przycisku +, aby dodać utwory.",
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
