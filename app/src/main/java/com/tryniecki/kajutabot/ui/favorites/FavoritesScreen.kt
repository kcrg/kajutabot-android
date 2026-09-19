package com.tryniecki.kajutabot.ui.favorites

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.ui.components.TrackArtwork
import com.tryniecki.kajutabot.ui.theme.fadeThrough

@Composable
fun FavoritesRoute(
    container: AppContainer,
    viewModel: FavoritesViewModel = viewModel(factory = FavoritesViewModel.Factory(container)),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    FavoritesScreen(
        ui = ui,
        onRefresh = viewModel::refresh,
        onDelete = viewModel::delete,
        onQueueAll = viewModel::queueAll,
        onPlaySingle = viewModel::playSingle,
        onDismiss = viewModel::dismissMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    ui: FavoritesUiState,
    onRefresh: () -> Unit,
    onDelete: (String) -> Unit,
    onQueueAll: () -> Unit,
    onPlaySingle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Ulubione") }) },
    ) { innerPadding ->
        val motion = MaterialTheme.motionScheme
        // Gentle fade between the initial loading state and content only —
        // steady list updates (delete/refresh with items present) don't animate.
        AnimatedContent(
            targetState = ui.isLoading && ui.favorites.isEmpty(),
            transitionSpec = {
                fadeThrough(
                    enterSpec = motion.defaultEffectsSpec(),
                    exitSpec = motion.fastEffectsSpec(),
                )
            },
            label = "favoritesContent",
        ) { loading ->
        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
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
                                ui.error ?: ui.info.orEmpty(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = onDismiss) { Text("OK") }
                        }
                    }
                }
            }

            if (ui.favorites.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.kb_ic_heart),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text("Brak ulubionych", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Zapisane utwory pojawią się tutaj.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = onRefresh) { Text("Odśwież") }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Zapisane (${ui.favorites.size})",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Button(onClick = onQueueAll, enabled = !ui.isMutating) {
                            Text("Dodaj wszystkie")
                        }
                    }
                }
                items(ui.favorites, key = { it.contentUrl }) { fav ->
                    Card(
                        modifier = Modifier.animateItem(
                            fadeInSpec = motion.fastEffectsSpec(),
                            fadeOutSpec = motion.fastEffectsSpec(),
                            placementSpec = motion.fastSpatialSpec(),
                        ),
                    ) {
                        ListItem(
                            leadingContent = {
                                TrackArtwork(
                                    imageUrl = fav.thumbnailUrl,
                                    modifier = Modifier.size(56.dp),
                                )
                            },
                            supportingContent = { Text(fav.contentUrl) },
                            trailingContent = {
                                Row {
                                    TextButton(
                                        onClick = { onPlaySingle(fav.contentUrl) },
                                        enabled = !ui.isMutating,
                                    ) { Text("Graj") }
                                    TextButton(
                                        onClick = { onDelete(fav.contentUrl) },
                                        enabled = !ui.isMutating,
                                    ) { Text("Usuń") }
                                }
                            },
                        ) {
                            Text(fav.title)
                        }
                    }
                }
            }
        }
        }
        }
    }
}
