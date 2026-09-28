package com.tryniecki.kajutabot.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.ui.components.GuildAvatar
import com.tryniecki.kajutabot.ui.components.SkeletonBlock
import com.tryniecki.kajutabot.ui.components.SwipeActionCard
import com.tryniecki.kajutabot.ui.components.SwipeActionStatus
import com.tryniecki.kajutabot.ui.components.SwipeActionHints
import com.tryniecki.kajutabot.ui.components.rememberScrollAwareFabVisible
import com.tryniecki.kajutabot.ui.components.rememberSkeletonPulse
import com.tryniecki.kajutabot.ui.components.TrackArtwork
import com.tryniecki.kajutabot.ui.components.TonalToggleIconButton
import com.tryniecki.kajutabot.ui.favorites.FavoriteTrackButton
import com.tryniecki.kajutabot.ui.favorites.FavoritesViewModel
import com.tryniecki.kajutabot.ui.theme.fadeThrough
import com.tryniecki.kajutabot.ui.theme.forwardSharedAxisY
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.ui.text.asString
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val artworkAccentColorRegex = Regex("#[0-9a-fA-F]{6}")

internal fun queueDragAutoScrollDelta(
    pointerY: Float,
    viewportHeight: Float,
    edgeSize: Float,
    maxStep: Float,
): Float {
    if (viewportHeight <= 0f || edgeSize <= 0f) return 0f
    val activeEdge = minOf(edgeSize, viewportHeight / 2f)
    val topProximity = ((activeEdge - pointerY) / activeEdge).coerceIn(0f, 1f)
    val bottomProximity = ((pointerY - (viewportHeight - activeEdge)) / activeEdge).coerceIn(0f, 1f)
    return when {
        topProximity > 0f -> -maxStep * topProximity
        bottomProximity > 0f -> maxStep * bottomProximity
        else -> 0f
    }
}

private enum class PlayerSurfaceState {
    LOADING,
    ERROR,
    READY,
}

@Composable
fun PlayerRoute(
    viewModel: PlayerViewModel,
    favoritesViewModel: FavoritesViewModel,
    onSearchOpen: () -> Unit,
    onDiscordSelectionOpen: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val ui by viewModel.playerScreenState.collectAsStateWithLifecycle()
    val favoritesUi by favoritesViewModel.ui.collectAsStateWithLifecycle()

    PlayerScreen(
        ui = ui,
        onDiscordSelectionOpen = onDiscordSelectionOpen,
        onSkip = viewModel::skip,
        onRequeueNowPlaying = viewModel::requeueNowPlaying,
        onRequeueEntry = viewModel::requeueEntry,
        onStop = viewModel::stop,
        onRepeatToggle = { viewModel.setRepeat(ui.queue?.isRepeatEnabled != true) },
        onRadioToggle = viewModel::toggleRadio,
        onRemoveEntry = viewModel::removeEntry,
        onSwapEntries = viewModel::swapEntries,
        onClearQueue = viewModel::clearQueue,
        isFavorite = favoritesViewModel::isFavorite,
        favoriteStatus = favoritesViewModel::statusFor,
        onToggleFavorite = favoritesViewModel::toggle,
        favoritesBusy = favoritesUi.isLoading,
        onDismissMessage = viewModel::dismissMessage,
        onSearchOpen = onSearchOpen,
        onRetryQueue = {
            ui.selectedGuildId?.let(viewModel::refreshQueue)
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )
}

@Composable
fun SearchRoute(
    viewModel: PlayerViewModel,
    favoritesViewModel: FavoritesViewModel,
    onClose: () -> Unit,
) {
    val ui by viewModel.searchState.collectAsStateWithLifecycle()
    val favoritesUi by favoritesViewModel.ui.collectAsStateWithLifecycle()

    var savedQuery by rememberSaveable { mutableStateOf(ui.searchQuery) }
    var savedSourceName by rememberSaveable { mutableStateOf(ui.searchSource.name) }
    val savedSource = SearchSourceOption.entries.firstOrNull { it.name == savedSourceName }
        ?: SearchSourceOption.YOUTUBE

    // Rehydrate ViewModel search input after process recreation. The route's
    // rememberSaveable state survives because Navigation 3 owns a saveable-state holder.
    LaunchedEffect(Unit) {
        if (viewModel.searchState.value.searchQuery != savedQuery) {
            viewModel.setSearchQuery(savedQuery)
        }
        if (viewModel.searchState.value.searchSource != savedSource) {
            viewModel.setSearchSource(savedSource)
        }
    }

    LaunchedEffect(ui.searchEnqueueCompleted) {
        if (ui.searchEnqueueCompleted) {
            viewModel.acknowledgeSearchEnqueueCompleted()
            onClose()
        }
    }

    SearchScreen(
        ui = ui.copy(searchQuery = savedQuery, searchSource = savedSource),
        onClose = onClose,
        onQueryChange = { query ->
            savedQuery = query
            viewModel.setSearchQuery(query)
        },
        onSearchSourceChange = { source ->
            savedSourceName = source.name
            viewModel.setSearchSource(source)
        },
        onSubmit = viewModel::submitSmartInput,
        onHistoryClick = { query ->
            savedQuery = query
            viewModel.searchFromHistory(query)
        },
        onResultClick = viewModel::enqueueSearchResult,
        isFavorite = favoritesViewModel::isFavorite,
        favoriteStatus = favoritesViewModel::statusFor,
        onToggleFavorite = favoritesViewModel::toggle,
        favoritesBusy = favoritesUi.isLoading,
        onDismissMessage = viewModel::dismissMessage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    ui: PlayerScreenState,
    onDiscordSelectionOpen: () -> Unit,
    onSkip: () -> Unit,
    onRequeueNowPlaying: () -> Unit = {},
    onRequeueEntry: (String) -> Unit = {},
    onStop: () -> Unit,
    onRepeatToggle: () -> Unit,
    onRadioToggle: () -> Unit,
    onRemoveEntry: (String) -> Unit,
    onSwapEntries: (String, String, Long) -> Boolean,
    onClearQueue: () -> Unit,
    isFavorite: (PlaybackTrackResponse) -> Boolean,
    favoriteStatus: (PlaybackTrackResponse) -> SwipeActionStatus = { SwipeActionStatus.IDLE },
    onToggleFavorite: (PlaybackTrackResponse) -> Unit,
    favoritesBusy: Boolean,
    onDismissMessage: () -> Unit,
    onSearchOpen: () -> Unit,
    onRetryQueue: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val motion = MaterialTheme.motionScheme
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val showFloatingActions = rememberScrollAwareFabVisible(listState)
    val showBackToTop by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 4 ||
                (listState.firstVisibleItemIndex > 0 && listState.firstVisibleItemScrollOffset > 600)
        }
    }
    val scrollScope = rememberCoroutineScope()
    var scrollToTopJob by remember { mutableStateOf<Job?>(null) }
    var draggingEntryId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var dragStartTopPx by remember { mutableFloatStateOf(0f) }
    var dragHeightPx by remember { mutableIntStateOf(0) }
    var dragPointerY by remember { mutableFloatStateOf(0f) }
    var dragTargetIndex by remember { mutableIntStateOf(-1) }
    var dragSnapshotVersion by remember { mutableStateOf<Long?>(null) }
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var messageHeightPx by remember { mutableIntStateOf(0) }
    var nowPlayingHeightPx by remember { mutableIntStateOf(0) }
    var queueHeaderHeightPx by remember { mutableIntStateOf(0) }
    val dragItemBounds = remember { mutableMapOf<String, Rect>() }
    var previewOrder by remember(ui.queue?.guildId) {
        mutableStateOf<List<QueueEntryResponse>?>(null)
    }
    LaunchedEffect(ui.isMutating, ui.error) {
        if (!ui.isMutating || ui.error != null) previewOrder = null
    }
    val pending = previewOrder ?: ui.queue?.pendingEntries.orEmpty()
    val pendingIndexById = remember(pending) {
        buildMap(pending.size) {
            pending.forEachIndexed { index, entry ->
                if (!containsKey(entry.entryId)) put(entry.entryId, index)
            }
        }
    }
    val pendingEntryIds = pendingIndexById.keys
    val density = LocalDensity.current
    val scrollEdgePx = with(density) { 72.dp.toPx() }
    val maxScrollPerFramePx = with(density) { 18.dp.toPx() }
    val favoriteTouchAreaPx = with(density) { 56.dp.toPx() }
    fun updateDragTarget() {
        val closest = listState.layoutInfo.visibleItemsInfo
            .filter { it.key in pendingEntryIds }
            .minByOrNull { info ->
                kotlin.math.abs(dragPointerY - (info.offset + info.size / 2f))
            }
        dragTargetIndex = (closest?.key as? String)?.let(pendingIndexById::get) ?: dragTargetIndex
    }
    fun resetDrag() {
        draggingEntryId = null
        dragOffsetPx = 0f
        dragTargetIndex = -1
        dragSnapshotVersion = null
    }
    val changeServerChannelDesc = stringResource(R.string.player_change_server_channel)
    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                AnimatedVisibility(
                    visible = showFloatingActions,
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
                            onClick = onDiscordSelectionOpen,
                            modifier = Modifier.semantics { contentDescription = changeServerChannelDesc },
                        ) {
                            if (ui.selectedGuild != null) {
                                GuildAvatar(
                                    iconUrl = ui.selectedGuild.iconUrl,
                                    modifier = Modifier.size(32.dp),
                                    iconSize = 18.dp,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_brand_discord_outline),
                                    contentDescription = null,
                                )
                            }
                        }
                        FloatingActionButton(onClick = onSearchOpen) {
                            Icon(
                                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_search_outline),
                                contentDescription = stringResource(R.string.search_title),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val hasMessage = ui.error != null || ui.info != null
        val queueBottomClearance = 144.dp
        val emptyQueueMinHeight = with(density) {
            val gaps = (if (hasMessage) 3 else 2) * 16.dp.toPx()
            (viewportHeightPx - nowPlayingHeightPx - queueHeaderHeightPx -
                (if (hasMessage) messageHeightPx else 0) - gaps -
                (innerPadding.calculateTopPadding() + 8.dp).toPx() -
                (innerPadding.calculateBottomPadding() + queueBottomClearance).toPx())
                .coerceAtLeast(0f).toDp()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    listCoordinates = it
                    viewportHeightPx = it.size.height
                }
                .pointerInput(pendingIndexById, ui.queue?.version, ui.isMutating) {
                    if (ui.isMutating || pending.size < 2) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val sourceInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                            val id = info.key as? String
                            val bounds = id?.let(dragItemBounds::get)
                            id != null && id in pendingEntryIds && bounds?.contains(down.position) == true &&
                                down.position.x < bounds.right - favoriteTouchAreaPx
                        } ?: return@awaitEachGesture
                        val sourceId = sourceInfo.key as String
                        val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        longPress.consume()
                        draggingEntryId = sourceId
                        dragStartTopPx = sourceInfo.offset.toFloat()
                        dragHeightPx = sourceInfo.size
                        dragOffsetPx = 0f
                        dragPointerY = longPress.position.y
                        dragTargetIndex = pendingIndexById[sourceId] ?: -1
                        dragSnapshotVersion = ui.queue?.version
                        try {
                            val completed = drag(longPress.id) { change ->
                                dragOffsetPx += change.position.y - change.previousPosition.y
                                dragPointerY = change.position.y
                                change.consume()
                                updateDragTarget()
                            }
                            if (completed) {
                                val targetId = pending.getOrNull(dragTargetIndex)?.entryId
                                val version = dragSnapshotVersion
                                if (targetId != null && version != null) {
                                    val swapped = swappedQueueEntries(pending, sourceId, targetId)
                                    if (swapped != null && onSwapEntries(sourceId, targetId, version)) {
                                        previewOrder = swapped
                                    }
                                }
                            }
                        } finally {
                            resetDrag()
                        }
                    }
                },
        ) {
            LaunchedEffect(draggingEntryId) {
                if (draggingEntryId == null) return@LaunchedEffect
                while (true) {
                    withFrameNanos { }
                    val delta = queueDragAutoScrollDelta(
                        dragPointerY,
                        listState.layoutInfo.viewportEndOffset.toFloat(),
                        scrollEdgePx,
                        maxScrollPerFramePx,
                    )
                    if (delta != 0f && listState.scrollBy(delta) != 0f) updateDragTarget()
                }
            }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            userScrollEnabled = draggingEntryId == null,
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + queueBottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (ui.error != null || ui.info != null) {
                item {
                    Card(
                        modifier = Modifier.onSizeChanged { messageHeightPx = it.height },
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

            item {
                val surfaceState = when {
                    ui.queue == null && ui.queueLoadState == QueueLoadState.ERROR -> PlayerSurfaceState.ERROR
                    ui.isInitialContentLoading -> PlayerSurfaceState.LOADING
                    else -> PlayerSurfaceState.READY
                }
                AnimatedContent(
                    modifier = Modifier.onSizeChanged { nowPlayingHeightPx = it.height },
                    targetState = surfaceState,
                    transitionSpec = {
                        fadeThrough(
                            enterSpec = motion.defaultEffectsSpec(),
                            exitSpec = motion.fastEffectsSpec(),
                        ).using(SizeTransform { _, _ -> motion.defaultSpatialSpec() })
                    },
                    label = "playerInitialState",
                ) { state ->
                    when (state) {
                        PlayerSurfaceState.LOADING -> NowPlayingSkeletonCard()
                        PlayerSurfaceState.ERROR -> PlayerLoadErrorCard(
                            message = ui.queueLoadError,
                            onRetry = onRetryQueue,
                        )
                        PlayerSurfaceState.READY -> NowPlayingCard(
                            queue = ui.queue,
                            queueObservedAtElapsedRealtimeMs = ui.queueObservedAtElapsedRealtimeMs,
                            presentedNowPlaying = ui.presentedNowPlaying
                                ?: ui.queue?.nowPlayingPresentationOrNull(),
                            isMutating = ui.isMutating,
                            isQueueReordering = ui.isQueueReordering,
                            activeControlAction = ui.activeControlAction,
                            onSkip = onSkip,
                            onRequeueNowPlaying = onRequeueNowPlaying,
                            onStop = { confirmStop = true },
                            isFavorite = isFavorite,
                            favoriteStatus = favoriteStatus,
                            onToggleFavorite = onToggleFavorite,
                            favoritesBusy = favoritesBusy,
                            onRepeatToggle = onRepeatToggle,
                            onRadioToggle = onRadioToggle,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                }
            }

            item(key = "queue-header") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().onSizeChanged { queueHeaderHeightPx = it.height },
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    stringResource(R.string.player_queue_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                ui.queue?.takeIf { it.pendingEntriesCount > 0 }?.let { queue ->
                                    Text(
                                        "${queue.pendingEntriesCount} · ${formatQueueDuration(queue.pendingDurationMilliseconds)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (pending.isNotEmpty()) {
                                IconButton(onClick = { confirmClear = true }, enabled = !ui.isMutating) {
                                    Icon(
                                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_x_outline),
                                        contentDescription = stringResource(R.string.player_clear_queue),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        if (pending.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                SwipeActionHints()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_grip_vertical_outline),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        stringResource(R.string.player_drag_reorder_hint),
                                        modifier = Modifier.padding(start = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = !ui.isInitialContentLoading && pending.isEmpty(),
                        enter = fadeIn(tween(220, delayMillis = 80)) + expandVertically(tween(320)),
                        exit = fadeOut(tween(150)) + shrinkVertically(tween(280)),
                        label = "emptyQueueTransition",
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth()
                                .padding(top = 16.dp)
                                .heightIn(min = emptyQueueMinHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyQueueCard(onSearchOpen = onSearchOpen)
                        }
                    }
                }
            }

            if (ui.isInitialContentLoading) {
                item(key = "queue-loading") {
                    QueueSkeleton(
                        modifier = Modifier.animateItem(
                            fadeInSpec = motion.fastEffectsSpec(),
                            fadeOutSpec = motion.defaultEffectsSpec(),
                            placementSpec = motion.defaultSpatialSpec(),
                        ),
                    )
                }
            } else if (pending.isNotEmpty()) {
                items(pending, key = { it.entryId }) { entry ->
                    val entryIndex = pendingIndexById[entry.entryId] ?: -1
                    val dragged = draggingEntryId == entry.entryId
                    val target = dragTargetIndex == entryIndex
                    val moveUpDescription = stringResource(R.string.action_move_up)
                    val moveDownDescription = stringResource(R.string.action_move_down)
                    SwipeActionCard(
                        addLabel = stringResource(R.string.player_requeue_now_playing),
                        removeLabel = stringResource(R.string.action_remove_from_queue),
                        enabled = !ui.isMutating && entry.entryId !in ui.mutatingEntryIds && draggingEntryId == null,
                        addStatus = ui.requeueStatuses[entry.entryId] ?: SwipeActionStatus.IDLE,
                        removeStatus = ui.removeStatuses[entry.entryId] ?: SwipeActionStatus.IDLE,
                        onAdd = { onRequeueEntry(entry.entryId) },
                        onRemove = { onRemoveEntry(entry.entryId) },
                        modifier = Modifier
                            .alpha(if (dragged) 0f else 1f)
                            .animateItem(
                                fadeInSpec = motion.fastEffectsSpec(),
                                fadeOutSpec = motion.fastEffectsSpec(),
                                placementSpec = motion.fastSpatialSpec(),
                            ),
                    ) { foregroundModifier ->
                        Card(
                            modifier = foregroundModifier.fillMaxWidth()
                                .testTag("queue-drag-${entry.entryId}")
                                .onGloballyPositioned { coordinates ->
                                    val origin = listCoordinates?.boundsInRoot()?.topLeft ?: return@onGloballyPositioned
                                    val bounds = coordinates.boundsInRoot()
                                    dragItemBounds[entry.entryId] = Rect(
                                        bounds.left - origin.x,
                                        bounds.top - origin.y,
                                        bounds.right - origin.x,
                                        bounds.bottom - origin.y,
                                    )
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (target && !dragged) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            QueueTrackCardContent(
                                track = entry.track,
                                position = entryIndex + 1,
                                titleModifier = Modifier.semantics {
                                    customActions = listOf(
                                        CustomAccessibilityAction(moveUpDescription) {
                                            if (entryIndex > 0 && !ui.isMutating && ui.queue != null) {
                                                onSwapEntries(entry.entryId, pending[entryIndex - 1].entryId, ui.queue.version)
                                            } else false
                                        },
                                        CustomAccessibilityAction(moveDownDescription) {
                                            if (entryIndex in 0 until pending.lastIndex && !ui.isMutating && ui.queue != null) {
                                                onSwapEntries(entry.entryId, pending[entryIndex + 1].entryId, ui.queue.version)
                                            } else false
                                        },
                                    )
                                },
                                trailingContent = {
                                    FavoriteTrackButton(
                                        track = entry.track,
                                        checked = isFavorite(entry.track),
                                        status = favoriteStatus(entry.track),
                                        enabled = !favoritesBusy,
                                        onToggle = onToggleFavorite,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

            val draggedEntry = pending.firstOrNull { it.entryId == draggingEntryId }
            if (draggedEntry != null) {
                Card(
                    modifier = Modifier
                        .offset { IntOffset(0, (dragStartTopPx + dragOffsetPx).roundToInt()) }
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(with(density) { dragHeightPx.toDp() })
                        .shadow(8.dp, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Box {
                        ListItem(
                            modifier = Modifier.fillMaxSize(),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                            leadingContent = {
                                TrackArtwork(draggedEntry.track.artworkUrl, modifier = Modifier.size(64.dp))
                            },
                            supportingContent = { Text(formatDuration(draggedEntry.track.durationMilliseconds)) },
                            trailingContent = {
                                FavoriteTrackButton(
                                    track = draggedEntry.track,
                                    checked = isFavorite(draggedEntry.track),
                                    status = favoriteStatus(draggedEntry.track),
                                    enabled = false,
                                    onToggle = onToggleFavorite,
                                )
                            },
                        ) {
                            Text(draggedEntry.track.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        QueuePositionIndicator(
                            pendingIndexById.getValue(draggedEntry.entryId) + 1,
                            Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            }
        }

        if (confirmStop) {
            AlertDialog(
                onDismissRequest = { confirmStop = false },
                title = { Text(stringResource(R.string.player_stop_dialog_title)) },
                text = { Text(stringResource(R.string.player_stop_dialog_body)) },
                confirmButton = {
                    TextButton(onClick = { confirmStop = false; onStop() }, enabled = !ui.isMutating) {
                        Text(stringResource(R.string.action_stop), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text(stringResource(R.string.player_clear_dialog_title)) },
                text = { Text(stringResource(R.string.player_clear_dialog_body)) },
                confirmButton = {
                    TextButton(onClick = { confirmClear = false; onClearQueue() }, enabled = !ui.isMutating) {
                        Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

@Composable
private fun NowPlayingCard(
    queue: QueueSnapshotResponse?,
    queueObservedAtElapsedRealtimeMs: Long?,
    presentedNowPlaying: NowPlayingPresentation?,
    isMutating: Boolean,
    isQueueReordering: Boolean,
    activeControlAction: PlayerControlAction?,
    onSkip: () -> Unit,
    onRequeueNowPlaying: () -> Unit,
    onStop: () -> Unit,
    isFavorite: (PlaybackTrackResponse) -> Boolean,
    favoriteStatus: (PlaybackTrackResponse) -> SwipeActionStatus,
    onToggleFavorite: (PlaybackTrackResponse) -> Unit,
    favoritesBusy: Boolean,
    onRepeatToggle: () -> Unit,
    onRadioToggle: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val repeatEnabled = queue?.isRepeatEnabled == true
    val radioEnabled = queue?.radio?.isEnabled == true
    val activeTrack = presentedNowPlaying?.track
    val hasActivePlayback = activeTrack != null
    val motion = MaterialTheme.motionScheme
    val currentSlide = nowPlayingSlide(
        presentation = presentedNowPlaying,
        hasQueue = queue != null,
        positionObservedAtElapsedRealtimeMs = queueObservedAtElapsedRealtimeMs,
    )
    val playbackControlsBlocked = shouldBlockPlaybackControls(
        isMutating, activeControlAction, isQueueReordering,
    )
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
            // Track -> track keeps a vertical directional shared axis; idle <-> playing
            // has no "next" semantics, so it uses a calm fade-through instead.
            // Both rely on the reserved title/flags slots below: equal heights
            // mean no card jump on track change, and the built-in size animation
            // only runs for genuine idle <-> playing height deltas.
            Box {
                SmoothArtworkGlow(
                    slide = currentSlide,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .align(Alignment.TopCenter),
                )

                AnimatedContent(
                    targetState = currentSlide,
                    contentKey = { it.identity },
                    transitionSpec = {
                        val contentTransition = if (initialState.hasTrack == targetState.hasTrack) {
                            forwardSharedAxisY(
                                fadeSpec = motion.defaultEffectsSpec(),
                                slideSpec = motion.defaultSpatialSpec(),
                            )
                        } else {
                            fadeThrough(
                                enterSpec = motion.defaultEffectsSpec(),
                                exitSpec = motion.fastEffectsSpec(),
                            )
                        }
                        contentTransition.using(
                            SizeTransform { _, _ -> motion.defaultSpatialSpec() },
                        )
                    },
                    label = "nowPlaying",
                ) { slide ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        NowPlayingArtwork(
                            slide = slide,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(R.string.player_now_playing),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            // Fixed two-line slot: 1-line and 2-line titles occupy
                            // the same height at any font scale, so the card never
                            // jumps on track -> track.
                            Text(
                                text = if (slide.hasTrack) slide.title else stringResource(R.string.player_nothing_playing),
                                modifier = if (slide.hasTrack) {
                                    Modifier.playerTitleSharedBounds(
                                        playbackIdentity = slide.identity,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    )
                                } else {
                                    Modifier
                                },
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
                                reportedPositionMs = slide.positionMs,
                                observedAtElapsedRealtimeMs = slide.positionObservedAtElapsedRealtimeMs,
                                durationMs = slide.durationMs,
                                isPlaying = slide.isPlaying,
                            )
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onRequeueNowPlaying,
                modifier = Modifier.fillMaxWidth(),
                enabled = hasActivePlayback && activeControlAction != PlayerControlAction.REQUEUE,
            ) {
                Icon(
                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_add_outline),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.player_requeue_now_playing))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedIconButton(
                    onClick = onStop,
                    enabled = hasActivePlayback && !playbackControlsBlocked,
                ) {
                    Icon(
                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_player_stop_outline),
                        contentDescription = stringResource(R.string.action_stop),
                        tint = if (hasActivePlayback && !playbackControlsBlocked) {
                            MaterialTheme.colorScheme.error
                        } else {
                            LocalContentColor.current
                        },
                    )
                }
                FilledIconButton(
                    onClick = onSkip,
                    enabled = hasActivePlayback && !playbackControlsBlocked,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_player_skip_forward_outline),
                        contentDescription = stringResource(R.string.action_skip),
                    )
                }
                TonalToggleIconButton(
                    checked = repeatEnabled,
                    onCheckedChange = { onRepeatToggle() },
                    iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_repeat_outline,
                    checkedContentDescription = stringResource(R.string.player_repeat_disable),
                    uncheckedContentDescription = stringResource(R.string.player_repeat_enable),
                    enabled = hasActivePlayback && !playbackControlsBlocked,
                )
                TonalToggleIconButton(
                    checked = radioEnabled,
                    onCheckedChange = { onRadioToggle() },
                    iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_radio_outline,
                    checkedContentDescription = stringResource(R.string.player_radio_disable),
                    uncheckedContentDescription = stringResource(R.string.player_radio_enable),
                    enabled = true,
                )
                if (activeTrack != null) {
                    FavoriteTrackButton(
                        track = activeTrack,
                        checked = isFavorite(activeTrack),
                        status = favoriteStatus(activeTrack),
                        enabled = !favoritesBusy,
                        onToggle = onToggleFavorite,
                    )
                } else {
                    TonalToggleIconButton(
                        checked = false,
                        onCheckedChange = {},
                        iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_heart_outline,
                        checkedContentDescription = stringResource(R.string.action_remove_favorite),
                        uncheckedContentDescription = stringResource(R.string.action_add_favorite),
                        enabled = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun SmoothArtworkGlow(
    slide: NowPlayingSlide,
    modifier: Modifier = Modifier,
) {
    val fallbackAccent = lerp(
        MaterialTheme.colorScheme.primary,
        Color.White,
        0.18f,
    )

    val accents = remember { mutableStateMapOf<String, Color>() }
    val accent = slide.artworkAccentColor.toArtworkAccentColor() ?: fallbackAccent
    SideEffect {
        if (slide.hasTrack) accents[slide.identity] = accent
    }
    LaunchedEffect(slide.identity) {
        delay(700)
        accents.keys.filter { it != slide.identity }.forEach(accents::remove)
    }

    AnimatedContent(
        targetState = slide.identity to slide.hasTrack,
        contentKey = { it.first },
        transitionSpec = {
            fadeIn(
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            ) togetherWith fadeOut(
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
            )
        },
        modifier = modifier,
        label = "nowPlayingGlow",
    ) { (identity, hasTrack) ->
        if (hasTrack) {
            val animatedAccent by animateColorAsState(
                targetValue = accents[identity] ?: fallbackAccent,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                label = "nowPlayingGlowAccent",
            )
            AccentArtworkGlow(
                accent = animatedAccent,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun String?.toArtworkAccentColor(): Color? =
    this
        ?.takeIf { it.matches(artworkAccentColorRegex) }
        ?.let { Color(0xFF000000L or it.substring(1).toLong(16)) }

@Composable
private fun NowPlayingArtwork(
    slide: NowPlayingSlide,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        contentAlignment = Alignment.Center,
    ) {
        TrackArtwork(
            imageUrl = slide.thumbnailUrl,
            modifier = Modifier
                .fillMaxSize()
                .let { artworkModifier ->
                    if (slide.hasTrack) {
                        artworkModifier.playerArtworkSharedElement(
                            playbackIdentity = slide.identity,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    } else {
                        artworkModifier
                    }
                },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .offset(y = 4.dp)
                .graphicsLayer {
                    scaleX = 1.03f
                    scaleY = 1.04f
                }
                .blur(
                    radius = 40.dp,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                )
                .background(
                    color = accent.copy(alpha = 0.24f),
                    shape = RoundedCornerShape(14.dp),
                ),
        )
    }
}

@Composable
private fun EmptyQueueCard(onSearchOpen: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
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
            Text(stringResource(R.string.player_queue_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.player_queue_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSearchOpen) {
                Text(stringResource(R.string.action_add_track))
            }
        }
    }
}

@Composable
private fun NowPlayingSkeletonCard() {
    val pulse = rememberSkeletonPulse()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SkeletonBlock(
                pulse = pulse,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                shape = RoundedCornerShape(12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SkeletonBlock(pulse, Modifier.width(116.dp).height(12.dp))
                SkeletonBlock(pulse, Modifier.fillMaxWidth(0.78f).height(26.dp))
                SkeletonBlock(pulse, Modifier.fillMaxWidth(0.56f).height(26.dp))
            }
            SkeletonBlock(
                pulse,
                Modifier.fillMaxWidth().height(6.dp),
                RoundedCornerShape(999.dp),
            )
            SkeletonBlock(pulse, Modifier.fillMaxWidth().height(40.dp), RoundedCornerShape(999.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(pulse, Modifier.size(40.dp), RoundedCornerShape(999.dp))
                SkeletonBlock(pulse, Modifier.size(56.dp), RoundedCornerShape(999.dp))
                SkeletonBlock(pulse, Modifier.size(40.dp), RoundedCornerShape(999.dp))
                SkeletonBlock(pulse, Modifier.size(40.dp), RoundedCornerShape(999.dp))
                SkeletonBlock(pulse, Modifier.size(40.dp), RoundedCornerShape(999.dp))
            }
        }
    }
}

@Composable
private fun QueueSkeleton(modifier: Modifier = Modifier) {
    val pulse = rememberSkeletonPulse()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(3) { index ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SkeletonBlock(pulse, Modifier.size(56.dp), RoundedCornerShape(10.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        SkeletonBlock(
                            pulse,
                            Modifier
                                .fillMaxWidth(if (index == 1) 0.72f else 0.88f)
                                .height(16.dp),
                        )
                        SkeletonBlock(pulse, Modifier.width(72.dp).height(12.dp))
                    }
                    SkeletonBlock(pulse, Modifier.size(36.dp), RoundedCornerShape(999.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerLoadErrorCard(
    message: UiText?,
    onRetry: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_server_outline),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Text(
                stringResource(R.string.player_load_failed).removeSuffix("."),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                message?.asString() ?: stringResource(R.string.player_load_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "—"

    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

internal fun formatQueueDuration(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) / 1_000)
    return "%02d:%02d:%02d".format(seconds / 3_600, (seconds % 3_600) / 60, seconds % 60)
}
