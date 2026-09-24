package com.tryniecki.kajutabot.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tryniecki.kajutabot.BuildConfig
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.common.SearchTrackResponse
import com.tryniecki.kajutabot.api.model.search.SearchItemResponse
import com.tryniecki.kajutabot.ui.components.ExpressiveLoadingIndicator
import com.tryniecki.kajutabot.ui.components.SkeletonBlock
import com.tryniecki.kajutabot.ui.components.rememberSkeletonPulse
import com.tryniecki.kajutabot.ui.components.resolveArtworkUrl
import com.tryniecki.kajutabot.ui.favorites.FavoriteTrackButton
import com.tryniecki.kajutabot.ui.text.asString
import java.text.NumberFormat
import java.util.Locale

/**
 * Full-screen modal "add track" page shown above the player.
 * Reuses [PlayerViewModel] smart input: URLs enqueue directly, text searches via
 * `GET /search`. Search-result artwork comes exclusively from the backend-provided
 * `SearchItemResponse.track.artworkUrl`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTrackScreen(
    ui: AddTrackUiState,
    onClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchSourceChange: (SearchSourceOption) -> Unit,
    onSubmit: () -> Unit,
    onHistoryClick: (String) -> Unit,
    onResultClick: (SearchItemResponse) -> Unit,
    isFavorite: (PlaybackTrackResponse) -> Boolean,
    onToggleFavorite: (PlaybackTrackResponse) -> Unit,
    favoritesBusy: Boolean,
    onDismissMessage: () -> Unit,
) {
    val trimmed = ui.searchQuery.trim()
    val isUrlInput = PlayerViewModel.looksLikeUrl(trimmed)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val appLocale = LocalConfiguration.current.locales[0]
    val submitAndHideKeyboard = remember(onSubmit, keyboardController, focusManager) {
        {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            onSubmit()
        }
    }
    val historySearchAndHideKeyboard = remember(onHistoryClick, keyboardController, focusManager) {
        { query: String ->
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            onHistoryClick(query)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.add_track_title)) },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_x_outline),
                                contentDescription = stringResource(R.string.action_close),
                            )
                        }
                    },
                    actions = {
                        SearchSourceDropdown(
                            selected = ui.searchSource,
                            onSelected = onSearchSourceChange,
                        )
                    },
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding(),
                    tonalElevation = 3.dp,
                ) {
                    Button(
                        onClick = submitAndHideKeyboard,
                        enabled = !ui.isMutating && !ui.isSearching && trimmed.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(if (isUrlInput) R.string.action_add_to_queue else R.string.action_search))
                    }
                }
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 16.dp,
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
                                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_search_outline),
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (ui.isSearching) {
                                ExpressiveLoadingIndicator(modifier = Modifier.size(24.dp))
                            }
                        },
                        placeholder = { Text(stringResource(R.string.add_track_hint)) },
                        label = { Text(stringResource(R.string.add_track_title)) },
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (isUrlInput) ImeAction.Go else ImeAction.Search,
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { submitAndHideKeyboard() },
                            onGo = { submitAndHideKeyboard() },
                        ),
                    )
                }

                if (trimmed.isBlank() && ui.searchHistory.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            ui.searchHistory.forEach { query ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { historySearchAndHideKeyboard(query) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            com.composables.icons.tabler.outline.R.drawable.tabler_ic_history_outline,
                                        ),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = query,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
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
                                    text = (ui.error ?: ui.info)?.asString().orEmpty(),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                TextButton(onClick = onDismissMessage) { Text(stringResource(R.string.action_ok)) }
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
                            ExpressiveLoadingIndicator(modifier = Modifier.size(40.dp))
                        }
                    }
                }

                if (ui.isSearching && ui.searchResults.isEmpty() && !isUrlInput) {
                    items(3, key = { "search-skeleton-$it" }) {
                        SearchResultSkeleton(
                            modifier = Modifier.animateItem(),
                        )
                    }
                } else if (ui.searchResults.isNotEmpty()) {
                    items(ui.searchResults, key = { it.input }) { item ->
                        val favoriteTrack = item.track.asFavoriteTrack()
                        Card(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .clickable(
                                    enabled = !ui.isMutating,
                                    onClick = { onResultClick(item) },
                                ),
                        ) {
                            ListItem(
                                verticalAlignment = Alignment.CenterVertically,
                                leadingContent = {
                                    SearchResultArtwork(
                                        imageUrl = item.track.artworkUrl,
                                        modifier = Modifier.size(64.dp),
                                    )
                                },
                                supportingContent = {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp),
                                    ) {
                                        searchResultUploadLabel(item)?.let { uploadLabel ->
                                            Text(
                                                text = uploadLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Text(
                                            text = formatSearchResultMetric(item, appLocale),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                },
                                trailingContent = {
                                    FavoriteTrackButton(
                                        track = favoriteTrack,
                                        checked = isFavorite(favoriteTrack),
                                        enabled = !favoritesBusy,
                                        onToggle = onToggleFavorite,
                                    )
                                },
                            ) {
                                Text(
                                    item.track.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                } else if (shouldShowSearchEmptyState(ui, trimmed, isUrlInput)) {
                    item {
                        Column(
                            modifier = Modifier
                                .animateItem()
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                stringResource(R.string.add_track_no_results_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                stringResource(R.string.add_track_no_results_body),
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

@Composable
private fun SearchSourceDropdown(
    selected: SearchSourceOption,
    onSelected: (SearchSourceOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "searchSourceChevronRotation",
    )

    Box {
        TextButton(
            onClick = { expanded = true },
        ) {
            Text(
                text = stringResource(selected.displayNameResId),
                maxLines = 1,
            )

            Spacer(Modifier.width(4.dp))

            Icon(
                painter = painterResource(
                    com.composables.icons.tabler.outline.R.drawable.tabler_ic_chevron_down_outline,
                ),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            SearchSourceOption.entries.forEach { source ->
                DropdownMenuItem(
                    text = { Text(stringResource(source.displayNameResId)) },
                    onClick = {
                        expanded = false
                        onSelected(source)
                    },
                )
            }
        }
    }
}

internal fun shouldShowSearchEmptyState(
    ui: AddTrackUiState,
    trimmedQuery: String,
    isUrlInput: Boolean,
): Boolean = !ui.isSearching &&
    !isUrlInput &&
    trimmedQuery.isNotBlank() &&
    ui.searchResults.isEmpty() &&
    ui.lastCompletedSearchQuery == trimmedQuery

internal fun searchResultUploadLabel(item: SearchItemResponse): String? =
    item.dateLabel?.takeIf { it.isNotBlank() }

internal fun formatSearchResultMetric(
    item: SearchItemResponse,
    locale: Locale = Locale.getDefault(),
): String {
    val count = NumberFormat.getIntegerInstance(locale).format(item.metricCount)
    return item.metricCaption
        .takeIf { it.isNotBlank() }
        ?.let { "$count $it" }
        ?: count
}

/**
 * Uses the backend URL directly. Coil is the authority on whether the returned value can be
 * fetched/decoded; the search UI does not pre-reject valid CDN URLs or manufacture provider
 * fallbacks. Protocol-relative CDN URLs are normalized to HTTPS.
 */
internal fun normalizeSearchThumbnailUrl(
    raw: String?,
    apiBaseUrl: String = BuildConfig.KAJUTABOT_API_BASE_URL,
): String? = resolveArtworkUrl(raw, apiBaseUrl)

@Composable
private fun SearchResultArtwork(
    imageUrl: String?,
    modifier: Modifier = Modifier,
) {
    val model = remember(imageUrl) { normalizeSearchThumbnailUrl(imageUrl) }
    var failed by remember(model) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null && !failed) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { failed = false },
                onError = { failed = true },
            )
        } else {
            Icon(
                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_file_music_outline),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchResultSkeleton(modifier: Modifier = Modifier) {
    val pulse = rememberSkeletonPulse()
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkeletonBlock(pulse, Modifier.size(64.dp), MaterialTheme.shapes.medium)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                SkeletonBlock(pulse, Modifier.fillMaxWidth(0.9f).height(17.dp))
                SkeletonBlock(pulse, Modifier.fillMaxWidth(0.65f).height(17.dp))
                SkeletonBlock(pulse, Modifier.width(104.dp).height(12.dp))
            }
            SkeletonBlock(pulse, Modifier.size(38.dp), MaterialTheme.shapes.extraLarge)
        }
    }
}

private fun SearchTrackResponse.asFavoriteTrack(): PlaybackTrackResponse = PlaybackTrackResponse(
    contentId, contentType, title, url, durationMilliseconds, artworkUrl, playCount = 0,
)
