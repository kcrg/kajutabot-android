package com.tryniecki.kajutabot.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    // Lifecycle-aware polling: only while Player route is composed.
    LaunchedEffect(guildId) {
        if (guildId == null) return@LaunchedEffect
        while (true) {
            delay(2500)
            viewModel.pollTick()
        }
    }

    PlayerScreen(
        ui = ui,
        pendingSharedUrl = pendingSharedUrl,
        onSearchQueryChange = viewModel::setSearchQuery,
        onSearchSubmit = viewModel::submitSmartInput,
        onSearchResultClick = viewModel::enqueueSearchResult,
        onPickerOpen = { viewModel.setShowPicker(true) },
        onPickerDismiss = { viewModel.setShowPicker(false) },
        onGuildSelect = viewModel::selectGuild,
        onChannelSelect = viewModel::selectChannel,
        onSkip = viewModel::skip,
        onStop = viewModel::stop,
        onRepeatToggle = { viewModel.setRepeat(!(ui.queue?.isRepeatEnabled == true)) },
        onRemoveEntry = viewModel::removeEntry,
        onClearQueue = viewModel::clearQueue,
        onRefresh = viewModel::refreshGuilds,
        onDismissMessage = viewModel::dismissMessage,
        onAddSharedUrl = { url ->
            viewModel.enqueueSharedUrl(url)
            appViewModel.clearPendingSharedUrl()
        },
        onDismissSharedUrl = { appViewModel.clearPendingSharedUrl() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    ui: PlayerUiState,
    pendingSharedUrl: String?,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSearchResultClick: (com.tryniecki.kajutabot.api.model.search.SearchItemResponse) -> Unit,
    onPickerOpen: () -> Unit,
    onPickerDismiss: () -> Unit,
    onGuildSelect: (String) -> Unit,
    onChannelSelect: (String) -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onRepeatToggle: () -> Unit,
    onRemoveEntry: (String) -> Unit,
    onClearQueue: () -> Unit,
    onRefresh: () -> Unit,
    onDismissMessage: () -> Unit,
    onAddSharedUrl: (String) -> Unit,
    onDismissSharedUrl: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Odtwarzacz") }) },
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
                        Icon(
                            painter = painterResource(R.drawable.kb_ic_server),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            if (pendingSharedUrl != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("Udostępniono link", style = MaterialTheme.typography.titleMedium)
                            Text(
                                pendingSharedUrl,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onAddSharedUrl(pendingSharedUrl) },
                                    enabled = ui.hasSelection && !ui.isMutating,
                                ) { Text("Dodaj do kolejki") }
                                TextButton(onClick = onDismissSharedUrl) { Text("Odrzuć") }
                            }
                            if (!ui.hasSelection) {
                                Text(
                                    "Najpierw wybierz serwer i kanał.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
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
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = ui.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.kb_ic_search),
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (ui.isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    },
                    placeholder = { Text("Wklej link lub wyszukaj utwór") },
                    label = { Text("Dodaj do kolejki") },
                )
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = onSearchSubmit,
                    enabled = !ui.isMutating && ui.searchQuery.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Dodaj / Szukaj") }
            }

            if (ui.searchResults.isNotEmpty()) {
                item {
                    Text(
                        "Wyniki wyszukiwania",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(ui.searchResults, key = { it.input }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSearchResultClick(item) },
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(item.track.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = { Text(item.metricCaption) },
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
                            headlineContent = {
                                Text(
                                    "${entry.position}. ${entry.track.title}",
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = { Text(formatDuration(entry.track.durationMilliseconds)) },
                            trailingContent = {
                                TextButton(
                                    onClick = { onRemoveEntry(entry.entryId) },
                                    enabled = !ui.isMutating,
                                ) { Text("Usuń") }
                            },
                        )
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
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.kb_ic_music),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
                                if (queue.radio.isEnabled) append(" • Radio włączone")
                                if (queue.isRepeatEnabled) append(" • Powtarzanie")
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
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onStop, enabled = !isMutating && queue?.nowPlaying != null) {
                    Text("Stop")
                }
                Spacer(Modifier.size(8.dp))
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
                Spacer(Modifier.size(8.dp))
                FilterChip(
                    selected = queue?.isRepeatEnabled == true,
                    onClick = onRepeatToggle,
                    enabled = !isMutating && queue != null,
                    label = { Text("Repeat") },
                )
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
                "Utwory dodane z wyszukiwarki pojawią się tutaj.",
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

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
