package com.tryniecki.kajutabot.ui.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.ui.app.AppViewModel
import com.tryniecki.kajutabot.ui.components.GuildAvatar
import com.tryniecki.kajutabot.ui.components.TrackArtwork
import kotlinx.coroutines.delay

@Composable
fun PlayerRoute(
    container: AppContainer,
    appViewModel: AppViewModel,
    viewModel: PlayerViewModel = viewModel(factory = PlayerViewModel.Factory(container)),
) {
    val ui by viewModel.ui.collectAsState()
    val pendingSharedUrl by appViewModel.pendingSharedUrl.collectAsState()
    val guildId = ui.selectedGuildId
    var isAddTrackOpen by rememberSaveable { mutableStateOf(false) }

    // Lifecycle-aware polling: only while Player route is composed.
    LaunchedEffect(guildId) {
        if (guildId == null) return@LaunchedEffect
        while (true) {
            delay(2500)
            viewModel.pollTick()
        }
    }

    // Shared URL -> prefill the add-track modal and open it.
    LaunchedEffect(pendingSharedUrl) {
        val url = pendingSharedUrl
        if (url != null) {
            viewModel.setSearchQuery(url)
            isAddTrackOpen = true
            appViewModel.clearPendingSharedUrl()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.trackAdded.collect {
            if (isAddTrackOpen) isAddTrackOpen = false
        }
    }

    BackHandler(enabled = isAddTrackOpen) {
        viewModel.clearAddTrack()
        isAddTrackOpen = false
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
            onAddTrackOpen = { isAddTrackOpen = true },
        )
        if (isAddTrackOpen) {
            AddTrackScreen(
                ui = ui,
                onClose = {
                    viewModel.clearAddTrack()
                    isAddTrackOpen = false
                },
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
                    Card {
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
            TrackArtwork(
                imageUrl = queue?.nowPlaying?.thumbnailUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                shape = RoundedCornerShape(12.dp),
                brokenIconSize = 48.dp,
                showMissingLabel = true,
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "TERAZ ODTWARZANE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = queue?.nowPlaying?.title ?: "Nic nie gra",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = run {
                        val nowPlaying = queue?.nowPlaying
                        when {
                            queue == null -> "Połącz aplikację z serwerem i wybierz kanał głosowy"
                            nowPlaying == null -> "Kolejka oczekuje na utwory"
                            else -> buildString {
                                append(formatDuration(nowPlaying.durationMilliseconds))
                                if (radioEnabled) append(" • Radio włączone")
                                if (repeatEnabled) append(" • Powtarzanie")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LinearWavyProgressIndicator(
                progress = { 0.85f },
                modifier = Modifier.fillMaxWidth(),
            )

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
