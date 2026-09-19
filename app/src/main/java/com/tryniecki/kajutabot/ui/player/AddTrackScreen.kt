package com.tryniecki.kajutabot.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.api.model.search.SearchItemResponse
import com.tryniecki.kajutabot.ui.components.TrackArtwork

/**
 * Full-screen modal "add track" page shown above the player.
 * Reuses [PlayerViewModel] smart input: URLs enqueue directly, text searches via
 * `GET /search`. Artwork comes exclusively from `SearchItemResponse.track.thumbnailUrl`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTrackScreen(
    ui: AddTrackUiState,
    onClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onResultClick: (SearchItemResponse) -> Unit,
    onDismissMessage: () -> Unit,
) {
    val trimmed = ui.searchQuery.trim()
    val isUrlInput = PlayerViewModel.looksLikeUrl(trimmed)

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Dodaj do kolejki") },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                painter = painterResource(R.drawable.kb_ic_x),
                                contentDescription = "Zamknij",
                            )
                        }
                    },
                )
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = ui.searchQuery,
                        onValueChange = onQueryChange,
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
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        },
                        placeholder = { Text("Wklej link lub wyszukaj utwór") },
                        label = { Text("Dodaj do kolejki") },
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (isUrlInput) ImeAction.Go else ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { onSubmit() },
                            onGo = { onSubmit() },
                        ),
                    )
                }

                item {
                    Button(
                        onClick = onSubmit,
                        enabled = !ui.isMutating && trimmed.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isUrlInput) "Dodaj do kolejki" else "Szukaj")
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

                if (ui.isMutating) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }

                if (ui.searchResults.isNotEmpty()) {
                    items(ui.searchResults, key = { it.input }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = !ui.isMutating,
                                    onClick = { onResultClick(item) },
                                ),
                        ) {
                            ListItem(
                                leadingContent = {
                                    TrackArtwork(
                                        imageUrl = item.track.thumbnailUrl,
                                        modifier = Modifier.size(72.dp),
                                    )
                                },
                                supportingContent = { Text(item.metricCaption) },
                            ) {
                                Text(
                                    item.track.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else if (!ui.isSearching && !isUrlInput && trimmed.isNotBlank()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "Brak wyników",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "Spróbuj innej frazy lub wklej bezpośredni link.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
