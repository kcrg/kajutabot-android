package com.tryniecki.kajutabot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
import com.tryniecki.kajutabot.browser.rememberOpenCustomTab
import com.tryniecki.kajutabot.ui.app.AppViewModel
import com.tryniecki.kajutabot.ui.app.AuthenticatedGate
import com.tryniecki.kajutabot.ui.app.resolveAuthenticatedGate
import com.tryniecki.kajutabot.ui.auth.LoginScreen
import com.tryniecki.kajutabot.ui.favorites.FavoritesRoute
import com.tryniecki.kajutabot.ui.favorites.FavoritesViewModel
import com.tryniecki.kajutabot.ui.more.ContactScreen
import com.tryniecki.kajutabot.ui.more.LibrariesScreen
import com.tryniecki.kajutabot.ui.more.MoreRootScreen
import com.tryniecki.kajutabot.ui.myaudio.MyAudioScreen
import com.tryniecki.kajutabot.ui.navigation.AppDestination
import com.tryniecki.kajutabot.ui.navigation.AppRoute
import com.tryniecki.kajutabot.ui.onboarding.AccessCheckingScreen
import com.tryniecki.kajutabot.ui.onboarding.AccessErrorScreen
import com.tryniecki.kajutabot.ui.onboarding.NoAccessScreen
import com.tryniecki.kajutabot.ui.onboarding.OnboardingScreen
import com.tryniecki.kajutabot.ui.player.EXPECTED_END_GRACE_MS
import com.tryniecki.kajutabot.ui.player.MiniPlayer
import com.tryniecki.kajutabot.ui.player.MiniPlayerState
import com.tryniecki.kajutabot.ui.player.POLL_INTERVAL_MS
import com.tryniecki.kajutabot.ui.player.PlayerRoute
import com.tryniecki.kajutabot.ui.player.PlayerViewModel
import com.tryniecki.kajutabot.ui.player.AddTrackRoute
import com.tryniecki.kajutabot.ui.player.playbackIdentity
import com.tryniecki.kajutabot.ui.player.remainingMs
import com.tryniecki.kajutabot.ui.player.shouldShowMiniPlayer
import com.tryniecki.kajutabot.ui.theme.ThemeMode
import com.tryniecki.kajutabot.ui.theme.KbMotion
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            errorMessage = state.message,
            isOAuthConfigured = container.appConfig.isOAuthConfigured,
            onLoginClick = { appViewModel.startLogin() },
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
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Przywracanie sesji…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RestoreErrorScreen(message: String, onRetry: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Nie można przywrócić sesji", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("Spróbuj ponownie") }
        }
    }
}

@Composable
private fun AuthenticatedShell(
    container: AppContainer,
    appViewModel: AppViewModel,
    discordUserId: String,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    // Single shared player state for the whole authenticated shell: Player
    // screen, MiniPlayer, share flow. Scoped to the activity, so switching
    // bottom tabs never recreates it and queue state survives within this session.
    val playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.Factory(container),
    )
    val navController = rememberNavController()
    val entryState by playerViewModel.entryState.collectAsStateWithLifecycle()
    var onboardingCompleted by remember(discordUserId) {
        mutableStateOf(container.onboardingPreferences.isCompletedFor(discordUserId))
    }
    var manualOnboardingRequested by remember(discordUserId) { mutableStateOf(false) }

    val gate = resolveAuthenticatedGate(
        guildAccessState = entryState.guildAccessState,
        onboardingCompleted = onboardingCompleted,
        manualOnboardingRequested = manualOnboardingRequested,
    )

    when (gate) {
        AuthenticatedGate.CHECKING_ACCESS -> {
            AccessCheckingScreen()
            return
        }
        AuthenticatedGate.ACCESS_ERROR -> {
            AccessErrorScreen(
                message = entryState.guildAccessError,
                onRetry = playerViewModel::refreshGuilds,
                onLogout = { appViewModel.logout() },
            )
            return
        }
        AuthenticatedGate.NO_ACCESS -> {
            NoAccessScreen(
                onRetry = playerViewModel::refreshGuilds,
                onLogout = { appViewModel.logout() },
            )
            return
        }
        AuthenticatedGate.ONBOARDING -> {
            OnboardingScreen(
                guilds = entryState.guilds,
                voiceChannels = entryState.voiceChannels,
                selectedGuildId = entryState.selectedGuildId,
                selectedChannelId = entryState.selectedVoiceChannelId,
                isLoadingVoiceChannels = entryState.isLoadingVoiceChannels,
                canDismiss = manualOnboardingRequested && onboardingCompleted,
                onGuildSelect = playerViewModel::selectGuild,
                onChannelSelect = playerViewModel::selectChannel,
                onComplete = {
                    container.onboardingPreferences.setCompletedFor(discordUserId)
                    onboardingCompleted = true
                    manualOnboardingRequested = false
                },
                onDismiss = { manualOnboardingRequested = false },
            )
            return
        }
        AuthenticatedGate.CONTENT -> Unit
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    val currentDestination = currentRoute.topLevelDestination()
    val isAddTrackOpen = currentRoute?.hasRoute<AppRoute.AddTrack>() == true
    val favoritesViewModel: FavoritesViewModel = viewModel(
        factory = FavoritesViewModel.Factory(container),
    )
    // Narrow slices: the shell only needs the mini-player state, the error
    // line and the polling keys — typing in AddTrack search must not
    // recompose the shell or the MiniPlayer.
    val miniPlayerState by playerViewModel.miniPlayerState.collectAsStateWithLifecycle()
    val favoritesUi by favoritesViewModel.ui.collectAsStateWithLifecycle()
    val playerError by playerViewModel.playerError.collectAsStateWithLifecycle()
    val pendingSharedUrl by appViewModel.pendingSharedUrl.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val motion = MaterialTheme.motionScheme

    LaunchedEffect(pendingSharedUrl) {
        val url = pendingSharedUrl ?: return@LaunchedEffect
        playerViewModel.setSearchQuery(url)
        if (navController.currentDestination?.hasRoute<AppRoute.AddTrack>() != true) {
            navController.navigateToTopLevel(AppDestination.PLAYER)
            navController.navigate(AppRoute.AddTrack) { launchSingleTop = true }
        }
        appViewModel.clearPendingSharedUrl()
    }

    // Clear transient search state after the closing transition, including system Back.
    var addTrackWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(isAddTrackOpen) {
        if (isAddTrackOpen) {
            addTrackWasOpen = true
        } else if (addTrackWasOpen) {
            addTrackWasOpen = false
            delay(KbMotion.MODAL_CLEAR_DELAY_MS)
            playerViewModel.clearAddTrack()
        }
    }

    // Retain the last content for the exit transition: visibility and state
    // go null in the same frame when the track ends.
    var lastMiniPlayerState by remember { mutableStateOf<MiniPlayerState?>(null) }
    LaunchedEffect(miniPlayerState) {
        if (miniPlayerState != null) lastMiniPlayerState = miniPlayerState
    }

    val miniPlayerVisible = shouldShowMiniPlayer(
        isAuthenticated = true,
        isBottomBarVisible = !isAddTrackOpen,
        destination = currentDestination,
        hasNowPlaying = miniPlayerState != null,
    )

    // Surface player errors on tabs without their own error card.
    // The Player tab keeps its inline card; other tabs get a transient snackbar.
    LaunchedEffect(playerError, currentDestination) {
        val message = playerError
        if (message != null && currentDestination != AppDestination.PLAYER) {
            snackbarHostState.showSnackbar(message)
        }
    }

    // The single queue polling loop: runs while STARTED regardless of the
    // active tab, so the MiniPlayer always has fresh state. No polling lives
    // in individual screens anymore.
    PlayerPollingEffect(playerViewModel)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Full-screen AddTrack modal: no tabs reachable underneath.
            if (!isAddTrackOpen) {
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
                                        contentDescription = destination.label,
                                    )
                                },
                                label = { Text(destination.label) },
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
        ) {
            composable<AppRoute.Player> {
                PlayerRoute(
                    viewModel = playerViewModel,
                    favoritesViewModel = favoritesViewModel,
                    onAddTrackOpen = { navController.navigate(AppRoute.AddTrack) },
                )
            }
            composable<AppRoute.MyAudio> { MyAudioScreen() }
            composable<AppRoute.Favorites> { FavoritesRoute(viewModel = favoritesViewModel) }
            composable<AppRoute.More> {
                MoreRootScreen(
                    container = container,
                    appViewModel = appViewModel,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onOpenOnboarding = { manualOnboardingRequested = true },
                    onOpenLibraries = { navController.navigate(AppRoute.Libraries) },
                    onOpenContact = { navController.navigate(AppRoute.Contact) },
                )
            }
            composable<AppRoute.Libraries> {
                LibrariesScreen(onBack = { navController.popBackStack() })
            }
            composable<AppRoute.Contact> {
                ContactScreen(container = container, onBack = { navController.popBackStack() })
            }
            composable<AppRoute.AddTrack> {
                AddTrackRoute(
                    viewModel = playerViewModel,
                    favoritesViewModel = favoritesViewModel,
                    onClose = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavDestination?.topLevelDestination(): AppDestination = when {
    this?.hasRoute<AppRoute.MyAudio>() == true -> AppDestination.MY_AUDIO
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

/**
 * The one and only queue polling loop. Active while the app is STARTED and a
 * guild is selected, on any tab. Entering the foreground polls immediately;
 * a one-shot expected-end refresh fires shortly after the current track
 * should end. Both paths share the ViewModel single-flight queue fetch.
 */
@Composable
private fun PlayerPollingEffect(viewModel: PlayerViewModel) {
    val keys by viewModel.pollingKeys.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(keys.guildId, keys.playbackKey) {
        val guildId = keys.guildId ?: return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.pollQueueOnce()
            val remaining = viewModel.ui.value.queue?.let { snapshot ->
                val track = snapshot.nowPlaying ?: return@let null
                remainingMs(
                    snapshot.nowPlayingStartedAt,
                    track.durationMilliseconds,
                    Instant.now().toEpochMilli(),
                )
            }
            val endRefresh = remaining?.let { ms ->
                launch {
                    delay(ms.coerceAtLeast(0) + EXPECTED_END_GRACE_MS)
                    viewModel.pollQueueOnce()
                }
            }
            try {
                while (true) {
                    delay(POLL_INTERVAL_MS)
                    viewModel.pollQueueOnce()
                }
            } finally {
                endRefresh?.cancel()
            }
        }
    }
}
