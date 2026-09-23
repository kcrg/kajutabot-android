package com.tryniecki.kajutabot.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
    val isSigningIn by appViewModel.isSigningIn.collectAsStateWithLifecycle()
    val isGuestSigningIn by appViewModel.isGuestSigningIn.collectAsStateWithLifecycle()
    val openCustomTab = rememberOpenCustomTab()

    LaunchedEffect(appViewModel) {
        appViewModel.openUrl.collect { url ->
            openCustomTab(url)
        }
    }

    when (val state = authState) {
        AuthState.Restoring -> RestoringScreen()
        is AuthState.SignedOut -> LoginScreen(
            isSigningIn = isSigningIn,
            isGuestSigningIn = isGuestSigningIn,
            errorMessage = state.message,
            isOAuthConfigured = container.appConfig.isOAuthConfigured,
            onLoginClick = { appViewModel.startLogin() },
            onGuestClick = { appViewModel.continueAsGuest() },
        )
        is AuthState.RecoverableError -> RestoreErrorScreen(
            message = state.message,
            onRetry = { appViewModel.retryRestore() },
        )
        is AuthState.SignedIn -> sessionIdentity?.let { identity ->
            key(identity) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides appViewModel.ownerForSession(identity)) {
                    AuthenticatedShell(
                        container = container,
                        appViewModel = appViewModel,
                        discordUserId = state.user.discordUserId,
                        sessionType = container.sessionManager.currentUserSession()?.sessionType ?: SessionType.DISCORD,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                    )
                }
            }
        } ?: RestoringScreen()
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
    val navController = rememberNavController()
    val entryState by playerViewModel.entryState.collectAsStateWithLifecycle()
    var onboardingCompleted by remember(discordUserId, sessionType) {
        mutableStateOf(container.onboardingPreferences.isCompletedFor(sessionType, discordUserId))
    }
    var manualOnboardingRequested by remember(discordUserId, sessionType) { mutableStateOf(false) }

    val gate = resolveAuthenticatedGate(
        guildAccessState = entryState.guildAccessState,
        onboardingCompleted = onboardingCompleted,
        manualOnboardingRequested = manualOnboardingRequested,
    )
    val motion = MaterialTheme.motionScheme

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = gate,
            modifier = Modifier.fillMaxSize(),
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
                            container.onboardingPreferences.setCompletedFor(sessionType, discordUserId)
                            onboardingCompleted = true
                            manualOnboardingRequested = false
                        },
                        onDismiss = { manualOnboardingRequested = false },
                    )
                }
                AuthenticatedGate.CONTENT -> AuthenticatedContent(
                    container = container,
                    appViewModel = appViewModel,
                    playerViewModel = playerViewModel,
                    navController = navController,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onOpenOnboarding = { manualOnboardingRequested = true },
                    sessionType = sessionType,
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedContent(
    container: AppContainer,
    appViewModel: AppViewModel,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenOnboarding: () -> Unit,
    sessionType: SessionType,
) {

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    val currentDestination = currentRoute.topLevelDestination()
    val isAddTrackOpen = currentRoute?.hasRoute<AppRoute.AddTrack>() == true
    val isDiscordSelectionOpen = currentRoute?.hasRoute<AppRoute.DiscordSelection>() == true
    val isFullScreenDetailOpen = isAddTrackOpen || isDiscordSelectionOpen
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
    val pendingSharedUrl by appViewModel.pendingSharedUrl.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val motion = MaterialTheme.motionScheme

    LaunchedEffect(favoritesViewModel, context) {
        favoritesViewModel.toggleMessages.collect { message ->
            Toast.makeText(context, message.resolve(context), Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(pendingSharedUrl) {
        val url = pendingSharedUrl ?: return@LaunchedEffect
        playerViewModel.setSearchQuery(url)
        if (navController.currentDestination?.hasRoute<AppRoute.AddTrack>() != true) {
            navController.navigateToTopLevel(AppDestination.PLAYER)
            navController.navigate(AppRoute.AddTrack) { launchSingleTop = true }
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Full-screen AddTrack modal: no tabs reachable underneath.
            if (!isFullScreenDetailOpen) {
                Column {
                    // Appear/disappear animates the occupied space too, so the
                    // NavigationBar stays put and content above glides instead
                    // of jumping.
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
                        lastMiniPlayerState?.let { state ->
                            MiniPlayer(
                                slide = state.slide,
                                track = state.track,
                                isMutating = state.isMutating,
                                activeControlAction = state.activeControlAction,
                                isFavorite = favoritesViewModel.isFavorite(state.track),
                                favoritesBusy = favoritesUi.isMutating || favoritesUi.isLoading,
                                onToggleFavorite = favoritesViewModel::toggle,
                                onOpenPlayer = { navController.navigateToTopLevel(AppDestination.PLAYER) },
                                onSkip = playerViewModel::skip,
                            )
                        }
                    }
                    NavigationBar {
                        AppDestination.entries.forEach { destination ->
                            val destinationLabel = stringResource(destination.labelResId)
                            NavigationBarItem(
                                selected = currentDestination == destination,
                                onClick = {
                                    if (currentDestination == destination) {
                                        if (destination == AppDestination.MORE) {
                                            navController.popBackStack(AppRoute.More, inclusive = false)
                                        }
                                    } else {
                                        navController.navigateToTopLevel(destination)
                                    }
                                },
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
        // Keep screens (including the Player FAB) above the bottom NavigationBar.
        // Only the bottom is forwarded: screens own their TopAppBars.
        NavHost(
            navController = navController,
            startDestination = AppRoute.Player,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            // Peer tabs settle quickly. Detail routes travel farther along the
            // horizontal hierarchy; effects and spatial motion use distinct specs.
            enterTransition = {
                if (targetState.destination.isDetailRoute()) {
                    fadeIn(motion.defaultEffectsSpec()) +
                        slideInHorizontally(motion.defaultSpatialSpec()) {
                            (it * KbMotion.HIERARCHY_SLIDE_FRACTION).toInt()
                        }
                } else {
                    fadeIn(motion.fastEffectsSpec()) +
                        scaleIn(motion.fastSpatialSpec(), initialScale = 0.98f)
                }
            },
            exitTransition = {
                if (targetState.destination.isDetailRoute()) {
                    fadeOut(motion.fastEffectsSpec()) +
                        slideOutHorizontally(motion.defaultSpatialSpec()) {
                            -(it * KbMotion.HIERARCHY_SLIDE_FRACTION).toInt()
                        }
                } else {
                    fadeOut(motion.fastEffectsSpec()) +
                        scaleOut(motion.fastSpatialSpec(), targetScale = 0.98f)
                }
            },
            popEnterTransition = {
                if (initialState.destination.isDetailRoute()) {
                    fadeIn(motion.defaultEffectsSpec()) +
                        slideInHorizontally(motion.defaultSpatialSpec()) {
                            -(it * KbMotion.HIERARCHY_SLIDE_FRACTION).toInt()
                        }
                } else {
                    fadeIn(motion.fastEffectsSpec()) +
                        scaleIn(motion.fastSpatialSpec(), initialScale = 0.98f)
                }
            },
            popExitTransition = {
                if (initialState.destination.isDetailRoute()) {
                    fadeOut(motion.fastEffectsSpec()) +
                        slideOutHorizontally(motion.defaultSpatialSpec()) {
                            (it * KbMotion.HIERARCHY_SLIDE_FRACTION).toInt()
                        }
                } else {
                    fadeOut(motion.fastEffectsSpec()) +
                        scaleOut(motion.fastSpatialSpec(), targetScale = 0.98f)
                }
            },
            // Navigation 2.10+ uses dedicated transitions while handling system / predictive Back.
            // Mirror the regular pop transitions so hardware/gesture Back looks identical to
            // the in-app back buttons on all hierarchical detail screens.
            predictivePopEnterTransition = { _ ->
                if (initialState.destination.isDetailRoute()) {
                    fadeIn(motion.defaultEffectsSpec()) +
                        slideInHorizontally(motion.defaultSpatialSpec()) {
                            -(it * KbMotion.HIERARCHY_SLIDE_FRACTION).toInt()
                        }
                } else {
                    fadeIn(motion.fastEffectsSpec()) +
                        scaleIn(motion.fastSpatialSpec(), initialScale = 0.98f)
                }
            },
            predictivePopExitTransition = { _ ->
                if (initialState.destination.isDetailRoute()) {
                    fadeOut(motion.fastEffectsSpec()) +
                        slideOutHorizontally(motion.defaultSpatialSpec()) {
                            (it * KbMotion.HIERARCHY_SLIDE_FRACTION).toInt()
                        }
                } else {
                    fadeOut(motion.fastEffectsSpec()) +
                        scaleOut(motion.fastSpatialSpec(), targetScale = 0.98f)
                }
            },
        ) {
            composable<AppRoute.Player> {
                PlayerRoute(
                    viewModel = playerViewModel,
                    favoritesViewModel = favoritesViewModel,
                    onAddTrackOpen = { navController.navigate(AppRoute.AddTrack) },
                    onDiscordSelectionOpen = { navController.navigate(AppRoute.DiscordSelection) },
                )
            }
            composable<AppRoute.Favorites> { FavoritesRoute(viewModel = favoritesViewModel) }
            composable<AppRoute.More> {
                MoreRootScreen(
                    container = container,
                    appViewModel = appViewModel,
                    playerViewModel = playerViewModel,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onOpenOnboarding = onOpenOnboarding,
                    onOpenLibraries = { navController.navigate(AppRoute.Libraries) },
                    onOpenContact = { navController.navigate(AppRoute.Contact) },
                )
            }
            composable<AppRoute.Libraries> {
                LibrariesScreen(onBack = { navController.popBackStack() })
            }
            composable<AppRoute.Contact> {
                ContactScreen(onBack = { navController.popBackStack() })
            }
            composable<AppRoute.AddTrack> {
                // Disposal follows the actual Navigation exit, including system Back.
                DisposableEffect(Unit) {
                    onDispose { playerViewModel.clearAddTrack() }
                }
                AddTrackRoute(
                    viewModel = playerViewModel,
                    favoritesViewModel = favoritesViewModel,
                    onClose = { navController.popBackStack() },
                )
            }
            composable<AppRoute.DiscordSelection> {
                DiscordSelectionRoute(
                    viewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    isGuest = sessionType == SessionType.GUEST,
                )
            }
        }
    }
}

private fun NavDestination.isDetailRoute(): Boolean =
    hasRoute<AppRoute.AddTrack>() ||
        hasRoute<AppRoute.DiscordSelection>() ||
        hasRoute<AppRoute.Libraries>() ||
        hasRoute<AppRoute.Contact>()

private fun NavDestination?.topLevelDestination(): AppDestination = when {
    this?.hasRoute<AppRoute.Favorites>() == true -> AppDestination.FAVORITES
    this?.hasRoute<AppRoute.More>() == true ||
        this?.hasRoute<AppRoute.Libraries>() == true ||
        this?.hasRoute<AppRoute.Contact>() == true -> AppDestination.MORE
    else -> AppDestination.PLAYER
}

private fun NavHostController.navigateToTopLevel(destination: AppDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
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
