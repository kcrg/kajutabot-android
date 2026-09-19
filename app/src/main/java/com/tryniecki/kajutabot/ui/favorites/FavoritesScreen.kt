package com.tryniecki.kajutabot.ui.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tryniecki.kajutabot.browser.rememberOpenCustomTab
import com.tryniecki.kajutabot.ui.components.TrackArtwork
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FavoritesRoute(viewModel: FavoritesViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    FavoritesScreen(
        ui = ui,
        onRefresh = viewModel::refresh,
        onDelete = viewModel::delete,
        onQueueAll = viewModel::queueAll,
        onPlaySingle = viewModel::playSingle,
        onAddByUrl = viewModel::addByUrl,
        onShuffleChange = viewModel::setShuffle,
        onDismiss = viewModel::dismissMessage,
    )
}

@Composable
fun FavoritesScreen(
    ui: FavoritesUiState,
    onRefresh: () -> Unit,
    onDelete: (String) -> Unit,
    onQueueAll: () -> Unit,
    onPlaySingle: (String) -> Unit,
    onAddByUrl: (String) -> Unit,
    onShuffleChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var addDialogOpen by remember { mutableStateOf(false) }
    var newUrl by remember { mutableStateOf("") }
    val openUrl = rememberOpenCustomTab()

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ulubione (${ui.favorites.size})", style = MaterialTheme.typography.titleLarge)
                    FilledTonalIconButton(onClick = { addDialogOpen = true }, enabled = !ui.isMutating) {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_plus_outline),
                            contentDescription = "Dodaj ulubiony przez link",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (ui.error != null || ui.info != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (ui.error != null) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ui.error ?: ui.info.orEmpty(), modifier = Modifier.weight(1f))
                            TextButton(onClick = onDismiss) { Text("OK") }
                        }
                    }
                }
            }
            if (ui.isLoading && ui.favorites.isEmpty()) {
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (ui.favorites.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_heart_outline),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text("Brak ulubionych", style = MaterialTheme.typography.titleLarge)
                        Text("Zapisz utwór sercem lub dodaj go przez link.")
                        OutlinedButton(onClick = onRefresh) { Text("Odśwież") }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = ui.shuffle,
                            enabled = !ui.isMutating,
                            onClick = { onShuffleChange(!ui.shuffle) },
                            label = { Text("Losowo") },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_arrows_shuffle_outline),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        Button(onClick = onQueueAll, enabled = !ui.isMutating) {
                            Text("Dodaj wszystkie")
                        }
                    }
                }
                items(ui.favorites, key = { it.contentUrl }) { fav ->
                    Card {
                        ListItem(
                            leadingContent = {
                                TrackArtwork(
                                    imageUrl = fav.thumbnailUrl,
                                    fallbackImageUrl = favoriteArtworkFallbackUrl(fav.contentUrl),
                                    modifier = Modifier.size(64.dp),
                                )
                            },
                            supportingContent = {
                                val date = runCatching {
                                    OffsetDateTime.parse(fav.addedAt).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                                        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                                }.getOrDefault(fav.addedAt)
                                Text("Zapisano $date")
                            },
                            trailingContent = {
                                Row(
                                    modifier = Modifier.height(64.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(onClick = { onPlaySingle(fav.contentUrl) }, enabled = !ui.isMutating) {
                                        Icon(
                                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_add_outline),
                                            contentDescription = "Dodaj do kolejki",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(onClick = { onDelete(fav.contentUrl) }, enabled = !ui.isMutating) {
                                        Icon(
                                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_trash_outline),
                                            contentDescription = "Usuń z ulubionych",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                        ) {
                            val canOpen = fav.contentUrl.startsWith("https://") || fav.contentUrl.startsWith("http://")
                            Text(
                                text = fav.title.ifBlank { fav.contentUrl },
                                modifier = if (canOpen) Modifier.clickable { openUrl(fav.contentUrl) } else Modifier,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = if (canOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }

    if (addDialogOpen) {
        AlertDialog(
            onDismissRequest = { addDialogOpen = false },
            title = { Text("Dodaj ulubiony przez link") },
            text = {
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    label = { Text("Adres utworu") },
                    placeholder = { Text("https://www.youtube.com/watch?v=…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newUrl.isNotBlank() && !ui.isMutating,
                    onClick = {
                        onAddByUrl(newUrl)
                        newUrl = ""
                        addDialogOpen = false
                    },
                ) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { addDialogOpen = false }) { Text("Anuluj") } },
        )
    }
}
