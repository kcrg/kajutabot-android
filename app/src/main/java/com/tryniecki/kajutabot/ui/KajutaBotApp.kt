package com.tryniecki.kajutabot.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
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
import com.tryniecki.kajutabot.ui.components.ExpressiveLoadingIndicator
import com.tryniecki.kajutabot.ui.favorites.FavoritesRoute
import com.tryniecki.kajutabot.ui.favorites.FavoritesViewModel
import com.tryniecki.kajutabot.ui.more.ContactScreen
import com.tryniecki.kajutabot.ui.more.LibrariesScreen
import com.tryniecki.kajutabot.ui.more.MoreRootScreen
import com.tryniecki.kajutabot.ui.navigation.AppDestination
import com.tryniecki.kajutabot.ui.navigation.AppRoute
import com.tryniecki.kajutabot.ui.onboarding.AccessCheckingScreen
import com.tryniecki.kajutabot.ui.onboarding.AccessErrorScreen
import com.tryniecki.kajutabot.ui.onboarding.NoAccessScreen
import com.tryniecki.kajutabot.ui.onboarding.OnboardingScreen
import com.tryniecki.kajutabot.ui.player.MiniPlayer
import com.tryniecki.kajutabot.ui.player.MiniPlayerState
import com.tryniecki.kajutabot.ui.player.PlayerRoute
import com.tryniecki.kajutabot.ui.player.PlayerViewModel
import com.tryniecki.kajutabot.ui.player.RealtimeOwner
import com.tryniecki.kajutabot.ui.player.AddTrackRoute
import com.tryniecki.kajutabot.ui.player.DiscordSelectionRoute
import com.tryniecki.kajutabot.ui.player.shouldShowMiniPlayer
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.ui.text.asString
import com.tryniecki.kajutabot.ui.text.resolve
import com.tryniecki.kajutabot.ui.theme.ThemeMode
import com.tryniecki.kajutabot.ui.theme.KbMotion
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
    val openCustomTab = rememberOpenCustomTab()

    LaunchedEffect(appUi.loginUrl) {
        val url = appUi.loginUrl ?: return@LaunchedEffect
        openCustomTab(url)
        appViewModel.acknowledgeLoginUrl(url)
    }

    when (val state = authState) {
        AuthState.Restoring -> RestoringScreen()
        is AuthState.SignedOut -> LoginScreen(
            isSigningIn = appUi.isSigningIn,
            isGuestSigningIn = appUi.isGuestSigningIn,
            errorMessage = appUi.accountError ?: state.message,
            isOAuthConfigured = appViewModel.isOAuthConfigured,
            onLoginClick = { appViewModel.startLogin() },
            onGuestClick = { appViewModel.continueAsGuest() },
        )
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
            ExpressiveLoadingIndicator(modifier = Modifier.size(36.dp))
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
    val onboardingCompletedFlow = remember(appViewModel, discordUserId, sessionType) {
        appViewModel.onboardingCompleted(sessionType, discordUserId)
    }
    val onboardingCompleted by onboardingCompletedFlow.collectAsStateWithLifecycle(initialValue = false)
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
        AnimatedContent(
            targetState = gate,
            modifier = Modifier
                .fillMaxHeight()
                .adaptiveContentWidth(constrainWideContent),
            transitionSpec = {
                when {
                    targetState == AuthenticatedGate.ONBOARDING ->
                        (fadeIn(motion.defaultEffectsSpec()) +
                            slideInHorizontally(motion.slowSpatialSpec()) {
                                (it * KbMotion.HIERARCHY_SLIDE_FRACTION).toInt()
                            }) togetherWith
                            (fadeOut(motion.fastEffectsSpec()) +
                                slideOutHorizontally(motion.slowSpatialSpec()) {
                                    -(it * KbMotion.HIERARCHY_SLIDE_FRACTION).toInt()
                                })
                    initialState == AuthenticatedGate.ONBOARDING ->
                        (fadeIn(motion.defaultEffectsSpec()) +
                            slideInHorizontally(motion.slowSpatialSpec()) {
                                -(it * KbMotion.HIERARCHY_SLIDE_FRACTION).toInt()
                            }) togetherWith
                            (fadeOut(motion.fastEffectsSpec()) +
                                slideOutHorizontally(motion.slowSpatialSpec()) {
                                    (it * KbMotion.HIERARCHY_SLIDE_FRACTION).toInt()
                                })
                    else -> fadeIn(motion.defaultEffectsSpec()) togetherWith
                        fadeOut(motion.fastEffectsSpec())
                }
            },
            label = "authenticatedGateTransition",
        ) { activeGate ->
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
                    val canDismissOnboarding = remember {
                        manualOnboardingRequested && onboardingCompleted
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

@Composable
private fun AuthenticatedContent(
    container: AppContainer,
    appViewModel: AppViewModel,
    playerViewModel: PlayerViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenOnboarding: () -> Unit,
    sessionType: SessionType,
) {
    val playerBackStack = rememberNavBackStack(AppRoute.Player)
    val favoritesBackStack = rememberNavBackStack(AppRoute.Favorites)
    val moreBackStack = rememberNavBackStack(AppRoute.More)
    var currentDestination by rememberSaveable { mutableStateOf(AppDestination.PLAYER) }

    val activeBackStack = when (currentDestination) {
        AppDestination.PLAYER -> playerBackStack
        AppDestination.FAVORITES -> favoritesBackStack
        AppDestination.MORE -> moreBackStack
    }
    val currentRoute = activeBackStack.lastOrNull() as? AppRoute ?: AppRoute.Player
    val isAddTrackOpen = currentRoute == AppRoute.AddTrack
    val isDiscordSelectionOpen = currentRoute == AppRoute.DiscordSelection
    val isFullScreenDetailOpen = isAddTrackOpen || isDiscordSelectionOpen

    fun popActiveBackStack() {
        if (activeBackStack.size > 1) {
            activeBackStack.removeLastOrNull()
        } else if (currentDestination != AppDestination.PLAYER) {
            // Exit non-home top-level stacks through Player. This matches the
            // Navigation 3 multiple-back-stack recipe and gives predictive Back
            // a real previous scene instead of synthesizing one.
            currentDestination = AppDestination.PLAYER
        }
    }

    fun selectTopLevel(destination: AppDestination) {
        if (currentDestination != destination) {
            currentDestination = destination
            return
        }

        // Re-selecting More returns to its root, matching the old NavController behavior.
        if (destination == AppDestination.MORE) {
            while (moreBackStack.size > 1) moreBackStack.removeLastOrNull()
        }
    }

    val favoritesViewModel: FavoritesViewModel = viewModel(
        factory = FavoritesViewModel.factory(container),
    )
    // Narrow slices: the shell only needs the mini-player state, the error
    // line and the polling keys — typing in AddTrack search must not
    // recompose the shell or the MiniPlayer.
    val miniPlayerState by playerViewModel.miniPlayerState.collectAsStateWithLifecycle()
    val remotePlaybackActive by playerViewModel.remotePlaybackActive.collectAsStateWithLifecycle()
    val mediaServiceActive by container.mediaServiceActive.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val favoritesUi by favoritesViewModel.ui.collectAsStateWithLifecycle()
    val playerError by playerViewModel.playerError.collectAsStateWithLifecycle()
    val controlMessage by playerViewModel.controlMessage.collectAsStateWithLifecycle()
    val appUi by appViewModel.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val motion = MaterialTheme.motionScheme

    LaunchedEffect(favoritesUi.transientMessage?.id) {
        val message = favoritesUi.transientMessage ?: return@LaunchedEffect
        Toast.makeText(context, message.text.resolve(context), Toast.LENGTH_SHORT).show()
        favoritesViewModel.acknowledgeTransientMessage(message.id)
    }

    LaunchedEffect(controlMessage?.id) {
        val message = controlMessage ?: return@LaunchedEffect
        Toast.makeText(context, message.text.resolve(context), Toast.LENGTH_SHORT).show()
        playerViewModel.acknowledgeControlMessage(message.id)
    }

    LaunchedEffect(appUi.pendingSharedUrl) {
        val url = appUi.pendingSharedUrl ?: return@LaunchedEffect
        playerViewModel.setSearchQuery(url)
        currentDestination = AppDestination.PLAYER
        if (playerBackStack.lastOrNull() != AppRoute.AddTrack) {
            while (playerBackStack.size > 1) playerBackStack.removeLastOrNull()
            playerBackStack.add(AppRoute.AddTrack)
        }
        appViewModel.clearPendingSharedUrl()
    }

    // Retain the last content for the exit transition: visibility and state
    // go null in the same frame when the track ends.
    var lastMiniPlayerState by remember { mutableStateOf<MiniPlayerState?>(null) }
    LaunchedEffect(miniPlayerState) {
        if (miniPlayerState != null) lastMiniPlayerState = miniPlayerState
    }

    val miniPlayerVisible = shouldShowMiniPlayer(
        isAuthenticated = true,
        isBottomBarVisible = !isFullScreenDetailOpen,
        destination = currentDestination,
        hasNowPlaying = miniPlayerState != null,
    )

    // Surface player errors on tabs without their own error card.
    // The Player tab keeps its inline card; other tabs get a transient snackbar.
    val playerErrorMessage = playerError?.asString()
    LaunchedEffect(playerErrorMessage, currentDestination) {
        if (playerErrorMessage != null && currentDestination != AppDestination.PLAYER) {
            snackbarHostState.showSnackbar(playerErrorMessage)
        }
    }

    PlayerRealtimeEffect(playerViewModel)
    LaunchedEffect(remotePlaybackActive, mediaServiceActive) {
        if (remotePlaybackActive && !mediaServiceActive) {
            RemotePlaybackService.start(context)
        }
    }

    SharedTransitionLayout {
        val appEntryProvider: (NavKey) -> NavEntry<NavKey> = entryProvider {
            entry<AppRoute.Player> {
                PlayerRoute(
                    viewModel = playerViewModel,
                    favoritesViewModel = favoritesViewModel,
                    onAddTrackOpen = {
                        if (playerBackStack.lastOrNull() != AppRoute.AddTrack) {
                            playerBackStack.add(AppRoute.AddTrack)
                        }
                    },
                    onDiscordSelectionOpen = {
                        if (playerBackStack.lastOrNull() != AppRoute.DiscordSelection) {
                            playerBackStack.add(AppRoute.DiscordSelection)
                        }
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                )
            }
            entry<AppRoute.Favorites> {
                FavoritesRoute(viewModel = favoritesViewModel)
            }
            entry<AppRoute.More> {
                MoreRootScreen(
                    appViewModel = appViewModel,
                    playerViewModel = playerViewModel,
                    isGuest = sessionType == SessionType.GUEST,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onOpenOnboarding = onOpenOnboarding,
                    onOpenLibraries = {
                        if (moreBackStack.lastOrNull() != AppRoute.Libraries) {
                            moreBackStack.add(AppRoute.Libraries)
                        }
                    },
                    onOpenContact = {
                        if (moreBackStack.lastOrNull() != AppRoute.Contact) {
                            moreBackStack.add(AppRoute.Contact)
                        }
                    },
                )
            }
            entry<AppRoute.Libraries> {
                LibrariesScreen(onBack = { popActiveBackStack() })
            }
            entry<AppRoute.Contact> {
                ContactScreen(onBack = { popActiveBackStack() })
            }
            entry<AppRoute.AddTrack> {
                // Disposal follows the actual Navigation 3 exit transition,
                // including system and predictive Back.
                DisposableEffect(Unit) {
                    onDispose { playerViewModel.clearAddTrack() }
                }
                AddTrackRoute(
                    viewModel = playerViewModel,
                    favoritesViewModel = favoritesViewModel,
                    onClose = { popActiveBackStack() },
                )
            }
            entry<AppRoute.DiscordSelection> {
                DiscordSelectionRoute(
                    viewModel = playerViewModel,
                    onBack = { popActiveBackStack() },
                    isGuest = sessionType == SessionType.GUEST,
                )
            }
        }

        // Each top-level stack owns a separate state holder. This mirrors the
        // Navigation 3 multiple-back-stack recipe and retains rememberSaveable
        // state while a tab is not currently displayed.
        val playerEntries = rememberDecoratedNavEntries(
            backStack = playerBackStack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>()),
            entryProvider = appEntryProvider,
        )
        val favoritesEntries = rememberDecoratedNavEntries(
            backStack = favoritesBackStack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>()),
            entryProvider = appEntryProvider,
        )
        val moreEntries = rememberDecoratedNavEntries(
            backStack = moreBackStack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>()),
            entryProvider = appEntryProvider,
        )
        // Keep Player as the starting stack underneath the selected tab. Besides
        // matching the official "exit through home" pattern, this gives NavDisplay
        // the correct previous scene for Back / predictive Back.
        val activeEntries = when (currentDestination) {
            AppDestination.PLAYER -> playerEntries
            AppDestination.FAVORITES -> playerEntries + favoritesEntries
            AppDestination.MORE -> playerEntries + moreEntries
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                // Full-screen AddTrack / Discord selection: no tabs reachable underneath.
                if (!isFullScreenDetailOpen) {
                    Column {
                        AnimatedVisibility(
                            visible = miniPlayerVisible,
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
                            lastMiniPlayerState?.let { state ->
                                MiniPlayer(
                                    slide = state.slide,
                                    track = state.track,
                                    isMutating = state.isMutating,
                                    activeControlAction = state.activeControlAction,
                                    isFavorite = favoritesViewModel.isFavorite(state.track),
                                    favoritesBusy = favoritesUi.isMutating || favoritesUi.isLoading,
                                    onToggleFavorite = favoritesViewModel::toggle,
                                    onOpenPlayer = { selectTopLevel(AppDestination.PLAYER) },
                                    onSkip = playerViewModel::skip,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = miniPlayerVisibilityScope,
                                )
                            }
                        }
                        NavigationBar {
                            AppDestination.entries.forEach { destination ->
                                val destinationLabel = stringResource(destination.labelResId)
                                NavigationBarItem(
                                    selected = currentDestination == destination,
                                    onClick = { selectTopLevel(destination) },
                                    icon = {
                                        Icon(
                                            painter = painterResource(destination.icon),
                                            contentDescription = destinationLabel,
                                        )
                                    },
                                    label = { Text(destinationLabel) },
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            // Navigation 3 owns the regular forward/back/predictive-back animations.
            // The authenticated shell is already centered/capped on expanded windows,
            // so screen composition itself stays identical to the phone layout.
            NavDisplay(
                entries = activeEntries,
                onBack = { popActiveBackStack() },
                sharedTransitionScope = this@SharedTransitionLayout,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding()),
            )
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
