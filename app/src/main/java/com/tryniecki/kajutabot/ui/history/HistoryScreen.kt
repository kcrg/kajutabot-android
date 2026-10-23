package com.tryniecki.kajutabot.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.ui.feedback.ObserveUserFeedback
import com.tryniecki.kajutabot.api.model.history.PlaybackHistoryEntryResponse
import com.tryniecki.kajutabot.browser.rememberOpenCustomTab
import com.tryniecki.kajutabot.ui.components.SwipeActionCard
import com.tryniecki.kajutabot.ui.components.SwipeActionHints
import com.tryniecki.kajutabot.ui.components.SwipeActionStatus
import com.tryniecki.kajutabot.ui.components.TrackListCardContent
import com.tryniecki.kajutabot.ui.text.asString
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun HistoryRoute(viewModel: HistoryViewModel) {
    ObserveUserFeedback(viewModel.feedbackEvents)
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    HistoryScreen(ui, viewModel::refresh, viewModel::loadMore, viewModel::retry, viewModel::enqueue, viewModel::dismissMessage)
}

@Composable
fun HistoryScreen(
    ui: HistoryUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onEnqueue: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val showBackToTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 4 ||
                (listState.firstVisibleItemIndex > 0 && listState.firstVisibleItemScrollOffset > 600)
        }
    }
    val scrollScope = rememberCoroutineScope()
    var scrollToTopJob by remember { mutableStateOf<Job?>(null) }
    val currentUi by rememberUpdatedState(ui)
    val loadMore by rememberUpdatedState(onLoadMore)
    val openUrl = rememberOpenCustomTab()
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    }
    val zoneId = ZoneId.systemDefault().id

    var scrollGuild by rememberSaveable { mutableStateOf(ui.guildId) }
    LaunchedEffect(ui.guildId) {
        if (scrollGuild != ui.guildId) {
            scrollToTopJob?.cancel()
            listState.scrollToItem(0)
            scrollGuild = ui.guildId
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val state = currentUi
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (!state.isLoading && !state.loadFailed && lastVisible >= 0 &&
                lastVisible >= listState.layoutInfo.totalItemsCount - 6) state.nextCursor else null
        }.distinctUntilChanged().collect { if (it != null) loadMore() }
    }

    Scaffold(
        floatingActionButton = {
            if (showBackToTop) {
                SmallFloatingActionButton(
                    onClick = {
                        if (scrollToTopJob?.isActive != true) {
                            scrollToTopJob = scrollScope.launch { listState.animateScrollToItem(0) }
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_arrow_up_outline),
                        contentDescription = stringResource(R.string.action_back_to_top),
                    )
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
            item(key = "header", contentType = "header") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.nav_history), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = onRefresh, enabled = ui.guildId != null && !ui.isLoading) {
                            Text(stringResource(R.string.action_refresh))
                        }
                    }
                    if (ui.entries.isNotEmpty()) SwipeActionHints(showRemove = false)
                }
            }
            if (ui.error != null) {
                item(key = "error", contentType = "error") {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ui.error.asString(), modifier = Modifier.weight(1f))
                            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
                        }
                    }
                }
            }
            items(ui.entries, key = { it.id }, contentType = { "history-entry" }) { entry ->
                HistoryEntryRow(
                    entry = entry,
                    canEnqueue = ui.canEnqueue,
                    queueStatus = ui.queueStatuses[entry.id] ?: SwipeActionStatus.IDLE,
                    formatter = formatter,
                    zoneId = zoneId,
                    onEnqueue = onEnqueue,
                    onOpenUrl = openUrl,
                )
            }
            item(key = "footer", contentType = "footer") {
                when {
                    ui.isLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                    ui.loadFailed -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.history_retry)) }
                    }
                    ui.entries.isEmpty() -> Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_history_outline),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            stringResource(if (ui.guildId == null) R.string.history_select_guild else R.string.history_empty),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryRow(
    entry: PlaybackHistoryEntryResponse,
    canEnqueue: Boolean,
    queueStatus: SwipeActionStatus,
    formatter: DateTimeFormatter,
    zoneId: String,
    onEnqueue: (Long) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val playedAt = remember(entry.startedAtUtc, formatter, zoneId) {
        runCatching {
            OffsetDateTime.parse(entry.startedAtUtc).atZoneSameInstant(ZoneId.of(zoneId)).format(formatter)
        }.getOrDefault(entry.startedAtUtc)
    }
    val canOpen = entry.queueInput.startsWith("https://") || entry.queueInput.startsWith("http://")
    SwipeActionCard(
        addLabel = stringResource(R.string.action_add_to_queue),
        enabled = canEnqueue,
        addStatus = queueStatus,
        onAdd = { onEnqueue(entry.id) },
    ) { foregroundModifier ->
        Card(
            modifier = foregroundModifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            TrackListCardContent(
                artworkUrl = entry.thumbnailUrl,
                titleContent = {
                    Text(
                        text = entry.title.ifBlank { entry.queueInput },
                        modifier = if (canOpen) Modifier.clickable { onOpenUrl(entry.queueInput) } else Modifier,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (canOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
                supportingContent = { Text(stringResource(R.string.history_played_at, playedAt)) },
            )
        }
    }
}
