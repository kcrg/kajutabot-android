package com.tryniecki.kajutabot.ui.favorites

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.browser.rememberOpenCustomTab
import com.tryniecki.kajutabot.ui.components.SkeletonBlock
import com.tryniecki.kajutabot.ui.text.asString
import com.tryniecki.kajutabot.ui.components.TrackArtwork
import com.tryniecki.kajutabot.ui.components.rememberScrollAwareFabVisible
import com.tryniecki.kajutabot.ui.components.rememberSkeletonPulse
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


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
    val motion = MaterialTheme.motionScheme
    val listState = rememberLazyListState()
    val locale = LocalConfiguration.current.locales[0]
    val favoriteDateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val showFloatingAction = rememberScrollAwareFabVisible(listState)

    Scaffold(
        floatingActionButton = {
            AnimatedVisibility(
                visible = showFloatingAction,
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
                        onClick = { addDialogOpen = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_heart_plus_outline),
                            contentDescription = stringResource(R.string.action_add_favorite_link),
                        )
                    }

                    if (ui.favorites.isNotEmpty()) {
                        val shuffleContainerColor = ToggleFloatingActionButtonDefaults.containerColor(
                            initialColor = MaterialTheme.colorScheme.secondaryContainer,
                            finalColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                        val shuffleUncheckedIconColor = MaterialTheme.colorScheme.onSecondaryContainer
                        val shuffleCheckedIconColor = MaterialTheme.colorScheme.onPrimaryContainer

                        ToggleFloatingActionButton(
                            checked = ui.shuffle,
                            onCheckedChange = onShuffleChange,
                            containerColor = shuffleContainerColor,
                        ) {
                            Icon(
                                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_arrows_shuffle_outline),
                                contentDescription = stringResource(if (ui.shuffle) R.string.favorites_shuffle_disable else R.string.favorites_shuffle_enable),
                                tint = lerp(
                                    shuffleUncheckedIconColor,
                                    shuffleCheckedIconColor,
                                    checkedProgress,
                                ),
                            )
                        }

                        ExtendedFloatingActionButton(
                            text = { Text(stringResource(R.string.favorites_add_all_count, ui.favorites.size)) },
                            icon = {
                                Icon(
                                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_add_outline),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                if (!ui.isMutating) onQueueAll()
                            },
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
                start = 12.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 12.dp,
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
                    Text(stringResource(R.string.nav_favorites), style = MaterialTheme.typography.titleLarge)
                    TextButton(
                        onClick = onRefresh,
                        enabled = !ui.isLoading && !ui.isMutating,
                    ) { Text(stringResource(R.string.action_refresh)) }
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
                            Text((ui.error ?: ui.info)?.asString().orEmpty(), modifier = Modifier.weight(1f))
                            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
                        }
                    }
                }
            }
            if (ui.isLoading && ui.favorites.isEmpty()) {
                items(4, key = { "favorite-skeleton-$it" }) {
                    FavoriteSkeletonCard(
                        modifier = Modifier.animateItem(
                            fadeInSpec = motion.fastEffectsSpec(),
                            fadeOutSpec = motion.defaultEffectsSpec(),
                            placementSpec = motion.defaultSpatialSpec(),
                        ),
                    )
                }
            } else if (ui.favorites.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_heart_outline),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(stringResource(R.string.favorites_empty_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.favorites_empty_body))
                        OutlinedButton(onClick = onRefresh) { Text(stringResource(R.string.action_refresh)) }
                    }
                }
            } else {
                items(ui.favorites, key = { it.contentUrl }) { fav ->
                    Card(
                        modifier = Modifier.animateItem(
                            fadeInSpec = motion.fastEffectsSpec(),
                            fadeOutSpec = motion.fastEffectsSpec(),
                            placementSpec = motion.fastSpatialSpec(),
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                top = 8.dp,
                                end = 8.dp,
                                bottom = 8.dp,
                            ),
                            leadingContent = {
                                TrackArtwork(
                                    imageUrl = fav.thumbnailUrl,
                                    modifier = Modifier.size(64.dp),
                                )
                            },
                            supportingContent = {
                                val zoneId = ZoneId.systemDefault()
                                val date = remember(fav.addedAt, zoneId) {
                                    runCatching {
                                        OffsetDateTime.parse(fav.addedAt).atZoneSameInstant(zoneId).toLocalDate()
                                            .format(favoriteDateFormatter)
                                    }.getOrDefault(fav.addedAt)
                                }
                                Text(stringResource(R.string.favorites_saved_date, date))
                            },
                            trailingContent = {
                                Row(
                                    modifier = Modifier.height(64.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconButton(onClick = { onPlaySingle(fav.contentUrl) }, enabled = !ui.isMutating) {
                                        Icon(
                                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_add_outline),
                                            contentDescription = stringResource(R.string.action_add_to_queue),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(onClick = { onDelete(fav.contentUrl) }, enabled = !ui.isMutating) {
                                        Icon(
                                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_trash_outline),
                                            contentDescription = stringResource(R.string.action_remove_favorite),
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
            title = { Text(stringResource(R.string.action_add_favorite_link)) },
            text = {
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    label = { Text(stringResource(R.string.favorites_track_url)) },
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
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { addDialogOpen = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun FavoriteSkeletonCard(modifier: Modifier = Modifier) {
    val pulse = rememberSkeletonPulse()
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkeletonBlock(pulse, Modifier.size(64.dp), MaterialTheme.shapes.medium)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkeletonBlock(pulse, Modifier.fillMaxWidth(0.88f).height(17.dp))
                SkeletonBlock(pulse, Modifier.fillMaxWidth(0.62f).height(17.dp))
                SkeletonBlock(pulse, Modifier.width(96.dp).height(12.dp))
            }
            SkeletonBlock(pulse, Modifier.size(38.dp), MaterialTheme.shapes.extraLarge)
        }
    }
}
