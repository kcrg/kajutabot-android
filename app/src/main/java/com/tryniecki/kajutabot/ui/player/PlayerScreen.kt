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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.ui.components.ActionTooltip
import com.tryniecki.kajutabot.ui.navigation.ScreenCommand
import com.tryniecki.kajutabot.ui.navigation.ScreenCommandEffect
import com.tryniecki.kajutabot.ui.feedback.FeedbackEffect
import com.tryniecki.kajutabot.ui.feedback.ReorderFeedback
import com.tryniecki.kajutabot.ui.feedback.rememberFeedbackPerformer
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.queue.QueueEntryResponse
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.ui.components.GuildAvatar
import com.tryniecki.kajutabot.ui.components.SkeletonBlock
import com.tryniecki.kajutabot.ui.components.SwipeActionCard
import com.tryniecki.kajutabot.ui.components.SwipeActionStatus
import com.tryniecki.kajutabot.ui.components.visibleSwipeItems
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

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
    commands: Flow<ScreenCommand> = emptyFlow(),
) {
    val ui by viewModel.playerScreenState.collectAsStateWithLifecycle()
    val favoritesUi by favoritesViewModel.ui.collectAsStateWithLifecycle()

    val localVolume by viewModel.localVolumeState.collectAsStateWithLifecycle()
    var localVolumeOpen by rememberSaveable { mutableStateOf(false) }

    PlayerScreen(
        ui = ui,
        onDiscordSelectionOpen = onDiscordSelectionOpen,
        onSkip = viewModel::skip,
        onRequeueNowPlaying = viewModel::requeueNowPlaying,
        onRequeueEntry = viewModel::requeueEntry,
        onStop = viewModel::stop,
        onRepeatToggle = { viewModel.setRepeat(ui.queue?.isRepeatEnabled != true) },
        onRadioToggle = viewModel::toggleRadio,
        onRadioSourceToggle = viewModel::toggleRadioSource,
        onRemoveEntry = viewModel::removeEntry,
        onSwapEntries = viewModel::swapEntries,
        onClearQueue = viewModel::clearQueue,
        isFavorite = favoritesUi::isFavorite,
        favoriteStatus = favoritesUi::statusFor,
        onToggleFavorite = favoritesViewModel::toggle,
        favoritesBusy = favoritesUi.isLoading,
        onDismissMessage = viewModel::dismissMessage,
        onSearchOpen = onSearchOpen,
        onRetryQueue = {
            ui.selectedGuildId?.let(viewModel::refreshQueue)
        },
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        commands = commands,
        onLocalVolumeOpen = { localVolumeOpen = true },
        showLocalVolumeButton = localVolume.showVolumeButton,
    )
    if (localVolumeOpen) {
        LocalVolumeSheet(
            state = localVolume,
            onDraftChange = viewModel::changeLocalVolumeDraft,
            onGestureFinished = viewModel::finishLocalVolumeGesture,
            onGestureCancelled = viewModel::cancelLocalVolumeGesture,
            onDismiss = { localVolumeOpen = false },
        )
    }
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
        ?: SearchSourceOption.SMART

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
        onResultSwipe = viewModel::enqueueSearchResult,
        isFavorite = favoritesUi::isFavorite,
        favoriteStatus = favoritesUi::statusFor,
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
    onRadioSourceToggle: () -> Unit = {},
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
    onLocalVolumeOpen: () -> Unit = {},
    showLocalVolumeButton: Boolean = false,
    commands: Flow<ScreenCommand> = emptyFlow(),
) {
    val motion = MaterialTheme.motionScheme
    val performFeedback = rememberFeedbackPerformer()
    val reorderFeedback = remember(performFeedback) { ReorderFeedback(performFeedback) }
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    ScreenCommandEffect(
        commands = commands,
        onScrollToTop = { listState.animateScrollToItem(0) },
        onRefresh = onRetryQueue,
    )
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
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var messageHeightPx by remember { mutableIntStateOf(0) }
    var nowPlayingHeightPx by remember { mutableIntStateOf(0) }
    var queueHeaderHeightPx by remember { mutableIntStateOf(0) }
    var previewOrder by remember(ui.queue?.guildId) {
        mutableStateOf<List<QueueEntryResponse>?>(null)
    }
    LaunchedEffect(ui.isMutating, ui.error) {
        if (!ui.isMutating || ui.error != null) previewOrder = null
    }
    val pending = previewOrder ?: visibleSwipeItems(
        items = ui.queue?.pendingEntries.orEmpty(),
        retainedItems = ui.retainedRemovalEntries.values,
        scopeId = ui.queue?.guildId,
        identity = { it.entryId },
    )
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
        var target = dragTargetIndex
        var nearestDistance = Float.POSITIVE_INFINITY
        for (info in listState.layoutInfo.visibleItemsInfo) {
            val id = info.key as? String ?: continue
            val index = pendingIndexById[id] ?: continue
            val distance = kotlin.math.abs(dragPointerY - (info.offset + info.size / 2f))
            if (distance < nearestDistance) {
                nearestDistance = distance
                target = index
            }
        }
        if (target != dragTargetIndex) {
            dragTargetIndex = target
            reorderFeedback.target(target)
        }
    }
    fun resetDrag() {
        reorderFeedback.end()
        draggingEntryId = null
        dragOffsetPx = 0f
        dragTargetIndex = -1
        dragSnapshotVersion = null
    }
    val changeServerChannelDesc = stringResource(R.string.player_change_server_channel)
    val missingVoiceChannelDesc = stringResource(R.string.player_voice_channel_required)
    val needsVoiceChannel = ui.needsVoiceChannelSelection
    val surfaceState = when {
        ui.queue == null && ui.queueLoadState == QueueLoadState.ERROR -> PlayerSurfaceState.ERROR
        ui.isInitialContentLoading -> PlayerSurfaceState.LOADING
        else -> PlayerSurfaceState.READY
    }
    val nowPlayingControls: @Composable () -> Unit = {
        NowPlayingControls(
            queue = ui.queue,
            presentedNowPlaying = ui.presentedNowPlaying ?: ui.queue?.nowPlayingPresentationOrNull(),
            favoritesRadioSelected = ui.favoritesRadioSelected,
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
            onRadioSourceToggle = onRadioSourceToggle,
            onLocalVolumeOpen = onLocalVolumeOpen,
            showLocalVolumeButton = showLocalVolumeButton,
        )
    }
    // On a half-open foldable the controls leave this card for the flat half of the screen.
    val nowPlayingSurface: @Composable (Boolean) -> Unit = { showControls ->
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
                    controls = nowPlayingControls.takeIf { showControls },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }
    }
    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showBackToTop) {
                    ActionTooltip(stringResource(R.string.action_back_to_top)) {
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
                        ActionTooltip(changeServerChannelDesc) {
                            SmallFloatingActionButton(
                                onClick = onDiscordSelectionOpen,
                                modifier = Modifier.semantics {
                                    contentDescription = changeServerChannelDesc
                                    if (needsVoiceChannel) stateDescription = missingVoiceChannelDesc
                                },
                                containerColor = if (needsVoiceChannel) MaterialTheme.colorScheme.surfaceContainerHigh
                                    else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (needsVoiceChannel) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onPrimaryContainer,
                            ) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    if (ui.selectedGuild != null) {
                                        GuildAvatar(
                                            iconUrl = ui.selectedGuild.iconUrl,
                                            modifier = Modifier.size(32.dp).alpha(if (needsVoiceChannel) 0.55f else 1f),
                                            iconSize = 18.dp,
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_brand_discord_outline),
                                            contentDescription = null,
                                        )
                                    }
                                    if (needsVoiceChannel) {
                                        Box(
                                            modifier = Modifier.align(Alignment.TopEnd)
                                                .padding(2.dp)
                                                .size(18.dp)
                                                .background(MaterialTheme.colorScheme.error, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_alert_triangle_outline),
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.onError,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        ActionTooltip(stringResource(R.string.search_title)) {
                            FloatingActionButton(onClick = onSearchOpen) {
                                Icon(
                                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_search_outline),
                                    contentDescription = stringResource(R.string.search_title),
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val hasMessage = ui.error != null || ui.info != null
        val queueBottomClearance = 128.dp
        val queuePane: @Composable (PlayerPaneMode) -> Unit = { paneMode ->
            Column(Modifier.fillMaxSize()) {
                if (paneMode == PlayerPaneMode.TABLETOP && surfaceState == PlayerSurfaceState.READY) {
                    Card(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Box(Modifier.padding(16.dp)) { nowPlayingControls() }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onSizeChanged { viewportHeightPx = it.height }
                        .pointerInput(pendingIndexById, ui.queue?.version, ui.isMutating, ui.retainedRemovalEntries) {
                            if (ui.isMutating || ui.retainedRemovalEntries.isNotEmpty() || pending.size < 2) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val totalWidth = size.width.toFloat()
                                val sourceInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    val id = info.key as? String
                                    if (id == null || id !in pendingEntryIds) return@firstOrNull false
                                    val itemTop = info.offset.toFloat()
                                    val itemBottom = itemTop + info.size
                                    down.position.y >= itemTop && down.position.y <= itemBottom &&
                                        down.position.x < totalWidth - favoriteTouchAreaPx
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
                                reorderFeedback.start(dragTargetIndex)
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
                                        } else {
                                            performFeedback(FeedbackEffect.Reject)
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
                val moveUpDescription = stringResource(R.string.action_move_up)
                val moveDownDescription = stringResource(R.string.action_move_down)
                val requeueLabel = stringResource(R.string.player_requeue_now_playing)
                val removeLabel = stringResource(R.string.action_remove_from_queue)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    userScrollEnabled = draggingEntryId == null,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = if (paneMode == PlayerPaneMode.TABLETOP) 8.dp else innerPadding.calculateTopPadding() + 8.dp,
                        end = 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + queueBottomClearance,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (ui.error != null || ui.info != null) {
                        item(key = "message", contentType = "message") {
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

                    if (paneMode == PlayerPaneMode.SINGLE) {
                        item(key = "now-playing", contentType = "now-playing") { nowPlayingSurface(true) }
                    }

                    item(key = "queue-header", contentType = "queue-header") {
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
                                        val queueSummary = ui.queue?.takeIf { it.pendingEntriesCount > 0 }?.let { queue ->
                                            "${queue.pendingEntriesCount} · ${formatQueueDuration(queue.pendingDurationMilliseconds)}"
                                        }
                                        QueueSummaryOdometer(queueSummary)
                                    }
                                    if (pending.isNotEmpty()) {
                                        ActionTooltip(stringResource(R.string.player_clear_queue), enabled = !ui.isMutating) {
                                            IconButton(onClick = { confirmClear = true }, enabled = !ui.isMutating) {
                                                Icon(
                                                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_x_outline),
                                                    contentDescription = stringResource(R.string.player_clear_queue),
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }
                                }
                                if (pending.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        SwipeActionHints()
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_drag_drop_2_outline),
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
                                        // Sibling heights change on every frame of a size animation.
                                        // Reading them here keeps that work out of composition.
                                        .layout { measurable, constraints ->
                                            val isNowPlayingInList = paneMode == PlayerPaneMode.SINGLE
                                            val gaps = ((if (hasMessage) 2 else 1) + (if (isNowPlayingInList) 1 else 0)) * 16.dp.toPx()
                                            val minHeight = (viewportHeightPx - (if (isNowPlayingInList) nowPlayingHeightPx else 0) - queueHeaderHeightPx -
                                                (if (hasMessage) messageHeightPx else 0) - gaps -
                                                (innerPadding.calculateTopPadding() + 8.dp).toPx() -
                                                (innerPadding.calculateBottomPadding() + queueBottomClearance).toPx())
                                                .coerceAtLeast(0f).roundToInt()
                                            val placeable = measurable.measure(
                                                constraints.copy(minHeight = minHeight.coerceAtMost(constraints.maxHeight)),
                                            )
                                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    EmptyQueueCard(onSearchOpen = onSearchOpen)
                                }
                            }
                        }
                    }

                    if (ui.isInitialContentLoading) {
                        item(key = "queue-loading", contentType = "queue-loading") {
                            QueueSkeleton(
                                modifier = Modifier.animateItem(
                                    fadeInSpec = motion.fastEffectsSpec(),
                                    fadeOutSpec = motion.defaultEffectsSpec(),
                                    placementSpec = motion.defaultSpatialSpec(),
                                ),
                            )
                        }
                    } else if (pending.isNotEmpty()) {
                        items(pending, key = { it.entryId }, contentType = { "queue-entry" }) { entry ->
                            val entryIndex = pendingIndexById[entry.entryId] ?: -1
                            val dragged = draggingEntryId == entry.entryId
                            val target = dragTargetIndex == entryIndex
                            SwipeActionCard(
                                addLabel = requeueLabel,
                                removeLabel = removeLabel,
                                enabled = !ui.isMutating && entry.entryId !in ui.retainedRemovalEntries &&
                                    entry.entryId !in ui.mutatingEntryIds && draggingEntryId == null,
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
                                        .testTag("queue-drag-${entry.entryId}"),
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
                                                    if (entryIndex > 0 && !ui.isMutating &&
                                                        ui.retainedRemovalEntries.isEmpty() && ui.queue != null) {
                                                        onSwapEntries(entry.entryId, pending[entryIndex - 1].entryId, ui.queue.version)
                                                    } else false
                                                },
                                                CustomAccessibilityAction(moveDownDescription) {
                                                    if (entryIndex in 0 until pending.lastIndex && !ui.isMutating &&
                                                        ui.retainedRemovalEntries.isEmpty() && ui.queue != null) {
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
                                .pointerHoverIcon(PointerIcon(android.view.PointerIcon.TYPE_GRABBING))
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
            }
        }
        PlayerPanes(
            tabletopHinge = tabletopHinge(),
            nowPlaying = { paneMode ->
                Box(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(
                        start = 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        end = if (paneMode == PlayerPaneMode.SIDE_BY_SIDE) 0.dp else 16.dp,
                        bottom = innerPadding.calculateBottomPadding() + 8.dp,
                    ),
                ) {
                    nowPlayingSurface(paneMode != PlayerPaneMode.TABLETOP)
                }
            },
            queue = queuePane,
        )

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
    controls: (@Composable () -> Unit)?,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val motion = MaterialTheme.motionScheme
    val currentSlide = nowPlayingSlide(
        presentation = presentedNowPlaying,
        hasQueue = queue != null,
        positionObservedAtElapsedRealtimeMs = queueObservedAtElapsedRealtimeMs,
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

            controls?.invoke()
        }
    }
}

@Composable
private fun NowPlayingControls(
    queue: QueueSnapshotResponse?,
    presentedNowPlaying: NowPlayingPresentation?,
    favoritesRadioSelected: Boolean,
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
    onRadioSourceToggle: () -> Unit,
    onLocalVolumeOpen: () -> Unit,
    showLocalVolumeButton: Boolean,
) {
    val repeatEnabled = queue?.isRepeatEnabled == true
    val radioEnabled = queue?.radio?.isEnabled == true
    val favoritesSource = if (radioEnabled) queue.radio.mode == "favorites" else favoritesRadioSelected
    val activeTrack = presentedNowPlaying?.track
    val hasActivePlayback = activeTrack != null
    val playbackControlsBlocked = shouldBlockPlaybackControls(
        isMutating, activeControlAction, isQueueReordering,
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onRequeueNowPlaying,
                modifier = Modifier.weight(1f),
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
            if (showLocalVolumeButton) ActionTooltip(stringResource(R.string.local_volume_title)) {
                OutlinedIconButton(
                    onClick = onLocalVolumeOpen,
                    shape = CircleShape,
                    modifier = Modifier.size(ButtonDefaults.MinHeight).testTag("local-volume-open"),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true),
                    colors = IconButtonDefaults.outlinedIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_volume_outline),
                        contentDescription = stringResource(R.string.local_volume_title),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionTooltip(stringResource(R.string.action_stop), enabled = hasActivePlayback && !playbackControlsBlocked) {
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
            }
            ActionTooltip(stringResource(R.string.action_skip), enabled = hasActivePlayback && !playbackControlsBlocked) {
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
            }
            TonalToggleIconButton(
                checked = repeatEnabled,
                onCheckedChange = { onRepeatToggle() },
                iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_repeat_outline,
                checkedContentDescription = stringResource(R.string.player_repeat_disable),
                uncheckedContentDescription = stringResource(R.string.player_repeat_enable),
                enabled = hasActivePlayback && !playbackControlsBlocked,
            )
            Row(
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.shapes.extraLarge,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TonalToggleIconButton(
                    checked = radioEnabled,
                    onCheckedChange = { onRadioToggle() },
                    iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_radio_outline,
                    checkedContentDescription = stringResource(R.string.player_radio_disable),
                    uncheckedContentDescription = stringResource(R.string.player_radio_enable),
                    enabled = !playbackControlsBlocked,
                )
                ActionTooltip(stringResource(R.string.player_radio_favorites_source), enabled = !playbackControlsBlocked) {
                    androidx.compose.material3.IconToggleButton(
                        checked = favoritesSource,
                        onCheckedChange = { onRadioSourceToggle() },
                        enabled = !playbackControlsBlocked,
                        modifier = Modifier.testTag("radio-favorites-source"),
                    ) {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_music_heart_outline),
                            contentDescription = stringResource(R.string.player_radio_favorites_source),
                            tint = if (favoritesSource) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
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

@Stable
private class ArtworkGlowLayer(val identity: String, val color: Color, initialAlpha: Float) {
    val alpha = Animatable(initialAlpha)
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
    val accent = slide.artworkAccentColor.toArtworkAccentColor()

    // Seeded during the first composition: returning to this screen must show the
    // current color right away instead of animating in from the theme accent.
    val layers = remember {
        mutableStateListOf<ArtworkGlowLayer>().apply {
            if (slide.hasTrack) add(ArtworkGlowLayer(slide.identity, accent ?: fallbackAccent, 1f))
        }
    }

    LaunchedEffect(slide.identity, slide.hasTrack, accent) {
        val current = layers.lastOrNull()
        val target = when {
            !slide.hasTrack -> null
            current?.identity == slide.identity -> accent ?: current.color
            else -> accent ?: fallbackAccent
        }
        if (target != null && (current?.identity != slide.identity || current.color != target)) {
            layers += ArtworkGlowLayer(slide.identity, target, 0f)
        }
        val incoming = layers.lastOrNull().takeIf { target != null }
        coroutineScope {
            layers.toList().forEach { layer ->
                launch {
                    layer.alpha.animateTo(
                        targetValue = if (layer === incoming) 1f else 0f,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                    )
                    if (layer !== incoming) layers -= layer
                }
            }
        }
    }

    AccentArtworkGlow(layers = layers, modifier = modifier)
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
    layers: List<ArtworkGlowLayer>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cornerRadiusPx = remember(density) { with(density) { 14.dp.toPx() } }
    val cornerRadius = remember(cornerRadiusPx) { androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx) }

    // Layers fade through the drawn color, inside one blurred node: fading a graphics layer
    // or AnimatedContent would clip the blur to its bounds for the whole transition.
    Box(
        modifier = modifier
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
            .drawBehind {
                layers.forEach { layer ->
                    drawRoundRect(
                        color = layer.color.copy(alpha = 0.24f * layer.alpha.value),
                        cornerRadius = cornerRadius,
                    )
                }
            },
    )
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
                horizontalArrangement = Arrangement.SpaceBetween,
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
