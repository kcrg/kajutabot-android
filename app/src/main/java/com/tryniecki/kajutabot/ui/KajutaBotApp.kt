package com.tryniecki.kajutabot.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldValue
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.auth.AuthState
import com.tryniecki.kajutabot.api.model.auth.SessionType
import com.tryniecki.kajutabot.media.RemotePlaybackService
import com.tryniecki.kajutabot.browser.rememberOpenCustomTab
import com.tryniecki.kajutabot.ui.app.AppViewModel
import com.tryniecki.kajutabot.ui.app.AuthenticatedGate
import com.tryniecki.kajutabot.ui.app.resolveAuthenticatedGate
import com.tryniecki.kajutabot.ui.auth.LoginScreen
import com.tryniecki.kajutabot.ui.components.BrandMark
import com.tryniecki.kajutabot.ui.components.DelayedPendingSpinner
import com.tryniecki.kajutabot.ui.favorites.FavoritesRoute
import com.tryniecki.kajutabot.ui.favorites.FavoritesViewModel
import com.tryniecki.kajutabot.ui.history.HistoryRoute
import com.tryniecki.kajutabot.ui.history.HistoryViewModel
import com.tryniecki.kajutabot.ui.jellyfin.JellyfinConnectionStatus
import com.tryniecki.kajutabot.ui.jellyfin.JellyfinRoute
import com.tryniecki.kajutabot.ui.jellyfin.JellyfinViewModel
import com.tryniecki.kajutabot.ui.more.MoreRootScreen
import com.tryniecki.kajutabot.ui.navigation.AppDestination
import com.tryniecki.kajutabot.ui.navigation.AppRoute
import com.tryniecki.kajutabot.ui.navigation.NavigationMotionSpecs
import com.tryniecki.kajutabot.ui.navigation.Navigator
import com.tryniecki.kajutabot.ui.navigation.hidesBottomNavigation
import com.tryniecki.kajutabot.ui.navigation.kajutaForwardTransition
import com.tryniecki.kajutabot.ui.navigation.kajutaPopTransition
import com.tryniecki.kajutabot.ui.navigation.navigationMotionMetadata
import com.tryniecki.kajutabot.ui.navigation.rememberNavigationState
import com.tryniecki.kajutabot.ui.onboarding.AccessCheckingScreen
import com.tryniecki.kajutabot.ui.onboarding.AccessErrorScreen
import com.tryniecki.kajutabot.ui.onboarding.NoAccessScreen
import com.tryniecki.kajutabot.ui.onboarding.OnboardingScreen
import com.tryniecki.kajutabot.ui.player.MiniPlayer
import com.tryniecki.kajutabot.ui.player.MiniPlayerState
import com.tryniecki.kajutabot.ui.player.PlayerRoute
import com.tryniecki.kajutabot.ui.player.PlayerViewModel
import com.tryniecki.kajutabot.ui.player.RealtimeOwner
import com.tryniecki.kajutabot.ui.player.SearchRoute
import com.tryniecki.kajutabot.ui.player.SharedTrackRoute
import com.tryniecki.kajutabot.ui.player.DiscordSelectionRoute
import com.tryniecki.kajutabot.ui.player.shouldShowMiniPlayer
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.ui.feedback.ObserveUserFeedback
import com.tryniecki.kajutabot.ui.text.asString
import com.tryniecki.kajutabot.ui.theme.ThemeMode
import com.tryniecki.kajutabot.ui.theme.KbMotion
import com.tryniecki.kajutabot.ui.theme.directionalSharedAxisXMotion
import kotlinx.coroutines.awaitCancellation

@Composable
fun KajutaBotApp(
    container: AppContainer,
    appViewModel: AppViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val authState by appViewModel.authState.collectAsStateWithLifecycle()
    val sessionIdentity by appViewModel.sessionIdentity.collectAsStateWithLifecycle()
    val appUi by appViewModel.ui.collectAsStateWithLifecycle()
    ObserveUserFeedback(appViewModel.feedbackEvents)
    val openCustomTab = rememberOpenCustomTab()

    LaunchedEffect(appUi.loginUrl) {
        val url = appUi.loginUrl ?: return@LaunchedEffect
        openCustomTab(url)
        appViewModel.acknowledgeLoginUrl(url)
    }

    when (val state = authState) {
        AuthState.Restoring -> RestoringScreen()
        is AuthState.SignedOut -> {
            val loginVisible = remember { MutableTransitionState(false).apply { targetState = true } }
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AnimatedVisibility(
                    visibleState = loginVisible,
                    enter = fadeIn(tween(280)) + slideInVertically(
                        animationSpec = tween(360),
                        initialOffsetY = { it / 12 },
                    ),
                    label = "loginEntrance",
                ) {
                    LoginScreen(
                        isSigningIn = appUi.isSigningIn,
                        isGuestSigningIn = appUi.isGuestSigningIn,
                        errorMessage = appUi.accountError ?: state.message,
                        isOAuthConfigured = appViewModel.isOAuthConfigured,
                        onLoginClick = { appViewModel.startLogin() },
                        onGuestClick = { appViewModel.continueAsGuest() },
                    )
                }
            }
        }
        is AuthState.RecoverableError -> RestoreErrorScreen(
            message = state.message,
            onRetry = { appViewModel.retryRestore() },
        )
        is AuthState.SignedIn -> {
            val identity = sessionIdentity
            val sessionType = appUi.sessionType
            if (identity == null || sessionType == null) {
                RestoringScreen()
            } else {
                key(identity) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides appViewModel.ownerForSession(identity)) {
                        AuthenticatedShell(
                            container = container,
                            appViewModel = appViewModel,
                            discordUserId = state.user.discordUserId,
                            sessionType = sessionType,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RestoringScreen() {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrandMark(size = 72.dp)
            Spacer(Modifier.height(20.dp))
            DelayedPendingSpinner(
                visible = true,
                color = MaterialTheme.colorScheme.primary,
                size = 36.dp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.auth_restoring_session),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RestoreErrorScreen(message: UiText, onRetry: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.auth_restore_failed_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                message.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AuthenticatedShell(
    container: AppContainer,
    appViewModel: AppViewModel,
    discordUserId: String,
    sessionType: SessionType,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    // Single shared player state for the whole authenticated shell: Player
    // screen, MiniPlayer, share flow. Scoped to the activity, so switching
    // bottom tabs never recreates it and queue state survives within this session.
    val playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.factory(container),
    )
    val entryState by playerViewModel.entryState.collectAsStateWithLifecycle()
    ObserveUserFeedback(playerViewModel.feedbackEvents)
    val onboardingCompletedFlow = remember(appViewModel, discordUserId, sessionType) {
        appViewModel.onboardingCompleted(sessionType, discordUserId)
    }
    val onboardingCompleted by onboardingCompletedFlow.collectAsStateWithLifecycle(initialValue = null)
    var manualOnboardingRequested by rememberSaveable(discordUserId, sessionType) { mutableStateOf(false) }
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val constrainWideContent = adaptiveInfo.windowSizeClass
        .isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)

    val gate = resolveAuthenticatedGate(
        guildAccessState = entryState.guildAccessState,
        onboardingCompleted = onboardingCompleted,
        manualOnboardingRequested = manualOnboardingRequested,
    )
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val hierarchySlideDistancePx = with(density) { KbMotion.HIERARCHY_SLIDE_DISTANCE.roundToPx() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        AnimatedContent(
            targetState = gate,
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
            transitionSpec = {
                when {
                    targetState == AuthenticatedGate.ONBOARDING ->
                        directionalSharedAxisXMotion(
                            spatialSpec = motion.defaultSpatialSpec(),
                            effectsSpec = motion.fastEffectsSpec(),
                            direction = 1,
                            slideDistancePx = hierarchySlideDistancePx,
                            initialAlpha = KbMotion.HIERARCHY_INITIAL_ALPHA,
                        )
                    initialState == AuthenticatedGate.ONBOARDING ->
                        directionalSharedAxisXMotion(
                            spatialSpec = motion.defaultSpatialSpec(),
                            effectsSpec = motion.fastEffectsSpec(),
                            direction = -1,
                            slideDistancePx = hierarchySlideDistancePx,
                            initialAlpha = KbMotion.HIERARCHY_INITIAL_ALPHA,
                        )
                    else -> fadeIn(motion.defaultEffectsSpec()) togetherWith
                        fadeOut(motion.fastEffectsSpec())
                }
            },
            label = "authenticatedGateTransition",
        ) { activeGate ->
            Box(
                if (activeGate == AuthenticatedGate.CONTENT) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxHeight().adaptiveContentWidth(constrainWideContent)
                },
            ) {
            when (activeGate) {
                AuthenticatedGate.CHECKING_ACCESS -> AccessCheckingScreen(isGuest = sessionType == SessionType.GUEST)
                AuthenticatedGate.ACCESS_ERROR -> AccessErrorScreen(
                    message = entryState.guildAccessError,
                    onRetry = playerViewModel::refreshGuilds,
                    onLogout = { appViewModel.logout() },
                    isGuest = sessionType == SessionType.GUEST,
                )
                AuthenticatedGate.NO_ACCESS -> NoAccessScreen(
                    onRetry = playerViewModel::refreshGuilds,
                    onLogout = { appViewModel.logout() },
                    isGuest = sessionType == SessionType.GUEST,
                )
                AuthenticatedGate.ONBOARDING -> {
                    // Keep the close affordance stable while this screen slides out.
                    val canDismissOnboarding = remember(manualOnboardingRequested, onboardingCompleted) {
                        manualOnboardingRequested && (onboardingCompleted == true)
                    }
                    OnboardingScreen(
                        guilds = entryState.guilds,
                        voiceChannels = entryState.voiceChannels,
                        selectedGuildId = entryState.selectedGuildId,
                        selectedChannelId = entryState.selectedVoiceChannelId,
                        isLoadingVoiceChannels = entryState.isLoadingVoiceChannels,
                        canDismiss = canDismissOnboarding,
                        backEnabled = gate == AuthenticatedGate.ONBOARDING,
                        sessionType = sessionType,
                        onGuildSelect = playerViewModel::selectGuild,
                        onChannelSelect = playerViewModel::selectChannel,
                        onComplete = {
                            appViewModel.completeOnboarding(sessionType, discordUserId)
                            manualOnboardingRequested = false
                        },
                        onDismiss = { manualOnboardingRequested = false },
                    )
                }
                AuthenticatedGate.CONTENT -> AuthenticatedContent(
                    constrainWideContent = constrainWideContent,
                    container = container,
                    appViewModel = appViewModel,
                    playerViewModel = playerViewModel,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onOpenOnboarding = { manualOnboardingRequested = true },
                    sessionType = sessionType,
                )
            }
            }
        }
        }
    }
}

@Composable
private fun AuthenticatedContent(
    container: AppContainer,
    appViewModel: AppViewModel,
    playerViewModel: PlayerViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenOnboarding: () -> Unit,
    sessionType: SessionType,
    constrainWideContent: Boolean,
) {
    val navigationState = rememberNavigationState()
    val navigator = remember(navigationState) { Navigator(navigationState) }
    val currentDestination = navigationState.currentDestination
    val isFullScreenDetailOpen = navigationState.currentRoute.hidesBottomNavigation()
    val navigationSuiteState = rememberNavigationSuiteScaffoldState(
        if (isFullScreenDetailOpen) NavigationSuiteScaffoldValue.Hidden else NavigationSuiteScaffoldValue.Visible,
    )
    LaunchedEffect(isFullScreenDetailOpen) {
        if (isFullScreenDetailOpen) navigationSuiteState.hide() else navigationSuiteState.show()
    }

    val favoritesViewModel: FavoritesViewModel = viewModel(
        factory = FavoritesViewModel.factory(container),
    )
    ObserveUserFeedback(favoritesViewModel.feedbackEvents)
    // Lives here rather than in its entry: the tab itself depends on the connection.
    val jellyfinViewModel: JellyfinViewModel = viewModel(
        factory = JellyfinViewModel.factory(container, isAvailable = sessionType != SessionType.GUEST),
    )
    ObserveUserFeedback(jellyfinViewModel.feedbackEvents)
    val jellyfinStatus by jellyfinViewModel.connectionStatus.collectAsStateWithLifecycle()
    SideEffect(jellyfinStatus) {
        if (jellyfinStatus == JellyfinConnectionStatus.DISCONNECTED ||
            jellyfinStatus == JellyfinConnectionStatus.ERROR
        ) {
            navigator.leave(AppDestination.JELLYFIN)
        }
    }
    // Narrow slices: the shell only needs the mini-player state, the error
    // line and the polling keys — typing on the Search screen must not
    // recompose the shell or the MiniPlayer.
    val miniPlayerState by playerViewModel.miniPlayerState.collectAsStateWithLifecycle()
    val remotePlaybackActive by playerViewModel.remotePlaybackActive.collectAsStateWithLifecycle()
    val mediaServiceActive by container.mediaServiceActive.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val favoritesUi by favoritesViewModel.ui.collectAsStateWithLifecycle()
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val navigationMotion = NavigationMotionSpecs(
        hierarchySpatial = motion.defaultSpatialSpec(),
        hierarchyEffects = motion.fastEffectsSpec(),
        hierarchySlideDistancePx = with(density) { KbMotion.HIERARCHY_SLIDE_DISTANCE.roundToPx() },
        tabEnter = motion.defaultEffectsSpec(),
        tabExit = motion.fastEffectsSpec(),
        tabSpatial = motion.fastSpatialSpec(),
    )

    LaunchedEffect(appViewModel) {
        appViewModel.sharedUrlEvents.collect { event ->
            navigator.showSharedTrack(AppRoute.SharedTrack(event.id, event.url))
        }
    }

    // Retain the last content for the exit transition: visibility and state
    // go null in the same frame when the track ends.
    var lastMiniPlayerState by remember { mutableStateOf<MiniPlayerState?>(null) }
    SideEffect(miniPlayerState) {
        if (miniPlayerState != null) lastMiniPlayerState = miniPlayerState
    }

    val miniPlayerVisible = shouldShowMiniPlayer(
        isAuthenticated = true,
        isBottomBarVisible = !isFullScreenDetailOpen,
        destination = currentDestination,
        hasNowPlaying = miniPlayerState != null,
    )

    PlayerRealtimeEffect(playerViewModel)
    SideEffect(remotePlaybackActive, mediaServiceActive) {
        if (remotePlaybackActive && !mediaServiceActive) {
            RemotePlaybackService.start(context)
        }
    }

    SharedTransitionLayout {
        val appEntryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
            entry<AppRoute.Player>(metadata = navigationMotionMetadata(AppDestination.PLAYER)) {
                PlayerRoute(
                    viewModel = playerViewModel,
                    favoritesViewModel = favoritesViewModel,
                    onSearchOpen = { navigator.navigate(AppRoute.Search) },
                    onDiscordSelectionOpen = { navigator.navigate(AppRoute.DiscordSelection) },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                    reselected = navigator.reselected(AppDestination.PLAYER),
                )
            }
            entry<AppRoute.Favorites>(metadata = navigationMotionMetadata(AppDestination.FAVORITES)) {
                FavoritesRoute(
                    viewModel = favoritesViewModel,
                    reselected = navigator.reselected(AppDestination.FAVORITES),
                )
            }
            entry<AppRoute.Jellyfin>(metadata = navigationMotionMetadata(AppDestination.JELLYFIN)) {
                JellyfinRoute(
                    viewModel = jellyfinViewModel,
                    reselected = navigator.reselected(AppDestination.JELLYFIN),
                )
            }
            entry<AppRoute.History>(metadata = navigationMotionMetadata(AppDestination.HISTORY)) {
                val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
                HistoryRoute(
                    viewModel = historyViewModel,
                    reselected = navigator.reselected(AppDestination.HISTORY),
                )
            }
            entry<AppRoute.More>(metadata = navigationMotionMetadata(AppDestination.MORE)) {
                MoreRootScreen(
                    appViewModel = appViewModel,
                    playerViewModel = playerViewModel,
                    isGuest = sessionType == SessionType.GUEST,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onOpenOnboarding = onOpenOnboarding,
                    jellyfinViewModel = jellyfinViewModel,
                    reselected = navigator.reselected(AppDestination.MORE),
                )
            }
            entry<AppRoute.Search>(metadata = navigationMotionMetadata(AppDestination.PLAYER)) {
                // Disposal follows the actual Navigation 3 exit transition,
                // including system and predictive Back.
                DisposableEffect(Unit) {
                    onDispose { playerViewModel.clearSearch() }
                }
                SearchRoute(
                    viewModel = playerViewModel,
                    favoritesViewModel = favoritesViewModel,
                    onClose = navigator::goBack,
                )
            }
            entry<AppRoute.SharedTrack>(metadata = navigationMotionMetadata(AppDestination.PLAYER)) { route ->
                SharedTrackRoute(
                    viewModel = playerViewModel,
                    requestId = route.requestId,
                    url = route.url,
                    onClose = navigator::goBack,
                )
            }
            entry<AppRoute.DiscordSelection>(metadata = navigationMotionMetadata(AppDestination.PLAYER)) {
                DiscordSelectionRoute(
                    viewModel = playerViewModel,
                    onBack = navigator::goBack,
                    isGuest = sessionType == SessionType.GUEST,
                )
            }
        }
        val activeEntries = navigationState.toDecoratedEntries(appEntryProvider)
        val navigationSuiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())

        NavigationSuiteScaffold(
            navigationItems = {
                val isJellyfinConnected = jellyfinStatus == JellyfinConnectionStatus.CONNECTED
                AppDestination.entries.forEach { destination ->
                    if (destination == AppDestination.JELLYFIN && !isJellyfinConnected &&
                        currentDestination != destination
                    ) return@forEach
                    val destinationLabel = stringResource(destination.labelResId)
                    NavigationSuiteItem(
                        selected = currentDestination == destination,
                        onClick = { navigator.selectTopLevel(destination) },
                        icon = {
                            Icon(
                                painter = painterResource(destination.icon),
                                contentDescription = destinationLabel,
                            )
                        },
                        label = { Text(destinationLabel) },
                        navigationSuiteType = navigationSuiteType,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
            navigationSuiteType = navigationSuiteType,
            state = navigationSuiteState,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Peer tabs fade through; detail pages use directional Shared Axis X.
                // The same pop policy drives toolbar, system and predictive Back.
                NavDisplay(
                    entries = activeEntries,
                    onBack = navigator::goBack,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    transitionSpec = { kajutaForwardTransition(navigationMotion) },
                    popTransitionSpec = { kajutaPopTransition(navigationMotion) },
                    predictivePopTransitionSpec = { kajutaPopTransition(navigationMotion) },
                    modifier = Modifier
                        .weight(1f)
                        .adaptiveContentWidth(constrainWideContent),
                )
                AnimatedVisibility(
                    visible = miniPlayerVisible,
                    modifier = Modifier.adaptiveContentWidth(constrainWideContent),
                    enter = fadeIn(motion.defaultEffectsSpec()) +
                        expandVertically(
                            animationSpec = motion.defaultSpatialSpec(),
                            expandFrom = Alignment.Bottom,
                        ),
                    exit = fadeOut(motion.fastEffectsSpec()) +
                        shrinkVertically(
                            animationSpec = motion.defaultSpatialSpec(),
                            shrinkTowards = Alignment.Bottom,
                        ),
                ) {
                    val miniPlayerVisibilityScope = this
                    (miniPlayerState ?: lastMiniPlayerState)?.let { state ->
                        MiniPlayer(
                            slide = state.slide,
                            track = state.track,
                            isMutating = state.isMutating,
                            isQueueReordering = state.isQueueReordering,
                            activeControlAction = state.activeControlAction,
                            isFavorite = favoritesUi.isFavorite(state.track),
                            favoriteStatus = favoritesUi.statusFor(state.track),
                            favoritesBusy = favoritesUi.isLoading,
                            onToggleFavorite = favoritesViewModel::toggleSilently,
                            onOpenPlayer = { navigator.selectTopLevel(AppDestination.PLAYER) },
                            onSkip = playerViewModel::skip,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = miniPlayerVisibilityScope,
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.adaptiveContentWidth(constrainWideContent: Boolean): Modifier =
    if (constrainWideContent) {
        widthIn(max = 840.dp).fillMaxWidth()
    } else {
        fillMaxWidth()
    }

@Composable
private fun PlayerRealtimeEffect(viewModel: PlayerViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.setRealtimeOwner(RealtimeOwner.UI, true)
            try {
                awaitCancellation()
            } finally {
                viewModel.setRealtimeOwner(RealtimeOwner.UI, false)
            }
        }
    }
}
