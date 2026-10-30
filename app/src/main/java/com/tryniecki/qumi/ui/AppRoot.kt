package com.tryniecki.qumi.ui

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.SupportingPaneSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberSupportingPaneSceneStrategy
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
import com.tryniecki.qumi.AppContainer
import com.tryniecki.qumi.R
import com.tryniecki.qumi.auth.AuthState
import com.tryniecki.qumi.api.model.auth.SessionType
import com.tryniecki.qumi.media.RemotePlaybackService
import com.tryniecki.qumi.browser.rememberOpenCustomTab
import com.tryniecki.qumi.ui.app.AppViewModel
import com.tryniecki.qumi.ui.app.AuthenticatedGate
import com.tryniecki.qumi.ui.app.resolveAuthenticatedGate
import com.tryniecki.qumi.ui.auth.LoginScreen
import com.tryniecki.qumi.ui.components.BrandMark
import com.tryniecki.qumi.ui.components.DelayedPendingSpinner
import com.tryniecki.qumi.ui.favorites.FavoritesRoute
import com.tryniecki.qumi.ui.favorites.FavoritesViewModel
import com.tryniecki.qumi.ui.history.HistoryRoute
import com.tryniecki.qumi.ui.history.HistoryViewModel
import com.tryniecki.qumi.ui.jellyfin.JellyfinConnectionStatus
import com.tryniecki.qumi.ui.jellyfin.JellyfinRoute
import com.tryniecki.qumi.ui.jellyfin.JellyfinViewModel
import com.tryniecki.qumi.ui.more.MoreRootScreen
import com.tryniecki.qumi.ui.navigation.AppKeyboardShortcuts
import com.tryniecki.qumi.ui.navigation.AppDestination
import com.tryniecki.qumi.ui.navigation.AppShortcut
import com.tryniecki.qumi.ui.navigation.AppRoute
import com.tryniecki.qumi.ui.navigation.NavigationMotionSpecs
import com.tryniecki.qumi.ui.navigation.Navigator
import com.tryniecki.qumi.ui.navigation.ScreenCommand
import com.tryniecki.qumi.ui.navigation.hidesBottomNavigation
import com.tryniecki.qumi.ui.navigation.forwardTransition
import com.tryniecki.qumi.ui.navigation.popTransition
import com.tryniecki.qumi.ui.navigation.navigationMotionMetadata
import com.tryniecki.qumi.ui.navigation.rememberNavigationState
import com.tryniecki.qumi.ui.onboarding.AccessCheckingScreen
import com.tryniecki.qumi.ui.onboarding.AccessErrorScreen
import com.tryniecki.qumi.ui.onboarding.NoAccessScreen
import com.tryniecki.qumi.ui.onboarding.OnboardingScreen
import com.tryniecki.qumi.ui.player.MiniPlayer
import com.tryniecki.qumi.ui.player.MiniPlayerState
import com.tryniecki.qumi.ui.player.PlayerRoute
import com.tryniecki.qumi.ui.player.PlayerViewModel
import com.tryniecki.qumi.ui.player.RealtimeOwner
import com.tryniecki.qumi.ui.player.SearchRoute
import com.tryniecki.qumi.ui.player.SharedTrackRoute
import com.tryniecki.qumi.ui.player.DiscordSelectionRoute
import com.tryniecki.qumi.ui.player.shouldShowMiniPlayer
import com.tryniecki.qumi.ui.text.UiText
import com.tryniecki.qumi.ui.feedback.ObserveUserFeedback
import com.tryniecki.qumi.ui.text.asString
import com.tryniecki.qumi.ui.theme.ThemeMode
import com.tryniecki.qumi.ui.theme.KbMotion
import com.tryniecki.qumi.ui.theme.directionalSharedAxisXMotion
import kotlinx.coroutines.awaitCancellation

@Composable
fun AppRoot(
    container: AppContainer,
    appViewModel: AppViewModel,
    keyboardShortcuts: AppKeyboardShortcuts,
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
                            keyboardShortcuts = keyboardShortcuts,
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
    keyboardShortcuts: AppKeyboardShortcuts,
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
                    container = container,
                    appViewModel = appViewModel,
                    keyboardShortcuts = keyboardShortcuts,
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

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AuthenticatedContent(
    container: AppContainer,
    appViewModel: AppViewModel,
    keyboardShortcuts: AppKeyboardShortcuts,
    playerViewModel: PlayerViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenOnboarding: () -> Unit,
    sessionType: SessionType,
) {
    val navigationState = rememberNavigationState()
    val navigator = remember(navigationState) { Navigator(navigationState) }
    val currentDestination = navigationState.currentDestination
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    // The recipe drops the spacer because of b/444438086.
    val paneDirective = remember(adaptiveInfo) {
        calculatePaneScaffoldDirective(adaptiveInfo)
            .copy(horizontalPartitionSpacerSize = 0.dp, verticalPartitionSpacerSize = 0.dp)
    }
    // Phones keep the regular transitions; only a window with room for two panes needs the scene.
    val isTwoPane = paneDirective.maxHorizontalPartitions > 1
    val supportingPaneStrategy = rememberSupportingPaneSceneStrategy<NavKey>(
        backNavigationBehavior = BackNavigationBehavior.PopUntilCurrentDestinationChange,
        directive = paneDirective,
    )
    val isFullScreenDetailOpen = navigationState.currentRoute.hidesBottomNavigation() && !isTwoPane
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
    val visibleDestinations = AppDestination.entries.filter { destination ->
        destination != AppDestination.JELLYFIN || currentDestination == destination ||
            jellyfinStatus == JellyfinConnectionStatus.CONNECTED
    }
    val isPlayerOnTop = currentDestination == AppDestination.PLAYER && navigationState.currentRoute == AppRoute.Player
    val canDismissRoute = navigationState.activeBackStack.size > 1
    SideEffect(visibleDestinations, isPlayerOnTop, canDismissRoute) {
        keyboardShortcuts.visibleDestinations = visibleDestinations
        keyboardShortcuts.canSkipTrack = isPlayerOnTop
        keyboardShortcuts.canDismiss = canDismissRoute
    }
    LaunchedEffect(keyboardShortcuts) {
        keyboardShortcuts.events.collect { shortcut ->
            when (shortcut) {
                is AppShortcut.Destination ->
                    keyboardShortcuts.visibleDestinations.getOrNull(shortcut.index)?.let(navigator::selectTopLevel)
                AppShortcut.Search ->
                    if (navigationState.currentDestination == AppDestination.JELLYFIN) {
                        navigator.send(ScreenCommand.FOCUS_SEARCH)
                    } else {
                        navigator.openSearch()
                    }
                AppShortcut.Refresh -> navigator.send(ScreenCommand.REFRESH)
                AppShortcut.NextTrack -> playerViewModel.skip()
                AppShortcut.Dismiss -> navigator.goBack()
            }
        }
    }
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
        val supportingPaneMetadata =
            navigationMotionMetadata(AppDestination.PLAYER) + SupportingPaneSceneStrategy.supportingPane()
        val appEntryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
            entry<AppRoute.Player>(
                metadata = navigationMotionMetadata(AppDestination.PLAYER) + SupportingPaneSceneStrategy.mainPane(),
            ) {
                PlayerRoute(
                    viewModel = playerViewModel,
                    favoritesViewModel = favoritesViewModel,
                    onSearchOpen = { navigator.navigate(AppRoute.Search) },
                    onDiscordSelectionOpen = { navigator.navigate(AppRoute.DiscordSelection) },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                    commands = navigator.commands(AppDestination.PLAYER),
                )
            }
            entry<AppRoute.Favorites>(metadata = navigationMotionMetadata(AppDestination.FAVORITES)) {
                FavoritesRoute(
                    viewModel = favoritesViewModel,
                    commands = navigator.commands(AppDestination.FAVORITES),
                )
            }
            entry<AppRoute.Jellyfin>(metadata = navigationMotionMetadata(AppDestination.JELLYFIN)) {
                JellyfinRoute(
                    viewModel = jellyfinViewModel,
                    commands = navigator.commands(AppDestination.JELLYFIN),
                )
            }
            entry<AppRoute.History>(metadata = navigationMotionMetadata(AppDestination.HISTORY)) {
                val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
                HistoryRoute(
                    viewModel = historyViewModel,
                    commands = navigator.commands(AppDestination.HISTORY),
                )
            }
            entry<AppRoute.More>(metadata = navigationMotionMetadata(AppDestination.MORE)) {
                ReadableWidth {
                    MoreRootScreen(
                        appViewModel = appViewModel,
                        playerViewModel = playerViewModel,
                        isGuest = sessionType == SessionType.GUEST,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        onOpenOnboarding = onOpenOnboarding,
                        jellyfinViewModel = jellyfinViewModel,
                        commands = navigator.commands(AppDestination.MORE),
                    )
                }
            }
            entry<AppRoute.Search>(metadata = supportingPaneMetadata) {
                // Disposal follows the actual Navigation 3 exit transition,
                // including system and predictive Back.
                DisposableEffect(Unit) {
                    onDispose { playerViewModel.clearSearch() }
                }
                ReadableWidth {
                    SearchRoute(
                        viewModel = playerViewModel,
                        favoritesViewModel = favoritesViewModel,
                        onClose = navigator::goBack,
                    )
                }
            }
            entry<AppRoute.SharedTrack>(metadata = supportingPaneMetadata) { route ->
                SharedTrackRoute(
                    viewModel = playerViewModel,
                    requestId = route.requestId,
                    url = route.url,
                    onClose = navigator::goBack,
                )
            }
            entry<AppRoute.DiscordSelection>(metadata = supportingPaneMetadata) {
                DiscordSelectionRoute(
                    viewModel = playerViewModel,
                    isSupportingPane = isTwoPane,
                    onBack = navigator::goBack,
                    isGuest = sessionType == SessionType.GUEST,
                )
            }
        }
        val activeEntries = navigationState.toDecoratedEntries(appEntryProvider)
        val navigationSuiteType = NavigationSuiteScaffoldDefaults.navigationSuiteType(currentWindowAdaptiveInfoV2())

        NavigationSuiteScaffold(
            navigationItems = {
                visibleDestinations.forEach { destination ->
                    val destinationLabel = stringResource(destination.labelResId)
                    NavigationSuiteItem(
                        selected = currentDestination == destination,
                        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
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
                    sceneStrategies = if (isTwoPane) listOf(supportingPaneStrategy) else emptyList(),
                    sharedTransitionScope = this@SharedTransitionLayout,
                    transitionSpec = { forwardTransition(navigationMotion) },
                    popTransitionSpec = { popTransition(navigationMotion) },
                    predictivePopTransitionSpec = { popTransition(navigationMotion) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
                AnimatedVisibility(
                    visible = miniPlayerVisible,
                    // A rail leaves the bottom inset to the content; the bar has already consumed it.
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)),
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

/** Text-first screens stay readable on a window wider than a line of text should be. */
@Composable
private fun ReadableWidth(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 840.dp).fillMaxWidth()) { content() }
    }
}
