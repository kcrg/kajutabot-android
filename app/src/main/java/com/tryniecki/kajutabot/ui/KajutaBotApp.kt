package com.tryniecki.kajutabot.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.auth.AuthState
import com.tryniecki.kajutabot.browser.rememberOpenCustomTab
import com.tryniecki.kajutabot.ui.app.AppViewModel
import com.tryniecki.kajutabot.ui.auth.LoginScreen
import com.tryniecki.kajutabot.ui.favorites.FavoritesRoute
import com.tryniecki.kajutabot.ui.more.MoreScreen
import com.tryniecki.kajutabot.ui.myaudio.MyAudioScreen
import com.tryniecki.kajutabot.ui.navigation.AppDestination
import com.tryniecki.kajutabot.ui.player.EXPECTED_END_GRACE_MS
import com.tryniecki.kajutabot.ui.player.MiniPlayer
import com.tryniecki.kajutabot.ui.player.POLL_INTERVAL_MS
import com.tryniecki.kajutabot.ui.player.PlayerRoute
import com.tryniecki.kajutabot.ui.player.PlayerViewModel
import com.tryniecki.kajutabot.ui.player.playbackIdentity
import com.tryniecki.kajutabot.ui.player.remainingMs
import com.tryniecki.kajutabot.ui.player.shouldShowMiniPlayer
import com.tryniecki.kajutabot.ui.theme.ThemeMode
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
    val authState by appViewModel.authState.collectAsState()
    val isSigningIn by appViewModel.isSigningIn.collectAsState()
    val currentDestination by appViewModel.currentDestination.collectAsState()
    val isAddTrackOpen by appViewModel.isAddTrackOpen.collectAsState()
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
        is AuthState.SignedIn -> AuthenticatedShell(
            container = container,
            appViewModel = appViewModel,
            currentDestination = currentDestination,
            isAddTrackOpen = isAddTrackOpen,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
        )
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
            Text("Brak połączenia", style = MaterialTheme.typography.titleLarge)
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
    currentDestination: AppDestination,
    isAddTrackOpen: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    // Single shared player state for the whole authenticated shell: Player
    // screen, MiniPlayer, share flow. Scoped to the activity, so switching
    // bottom tabs never recreates it and queue state survives.
    val playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.Factory(container),
    )
    val playerUi by playerViewModel.ui.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val miniPlayerVisible = shouldShowMiniPlayer(
        isAuthenticated = true,
        isBottomBarVisible = !isAddTrackOpen,
        destination = currentDestination,
        hasNowPlaying = playerUi.queue?.nowPlaying != null,
    )

    // Surface player errors on tabs without their own error card.
    // The Player tab keeps its inline card; other tabs get a transient snackbar.
    LaunchedEffect(playerUi.error, currentDestination) {
        val message = playerUi.error
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
                    AnimatedVisibility(
                        visible = miniPlayerVisible,
                        enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { it },
                        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { it },
                    ) {
                        MiniPlayer(
                            ui = playerUi,
                            onOpenPlayer = { appViewModel.onDestinationChange(AppDestination.PLAYER) },
                            onSkip = playerViewModel::skip,
                        )
                    }
                    NavigationBar {
                        AppDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = currentDestination == destination,
                                onClick = { appViewModel.onDestinationChange(destination) },
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
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
        // Short Material-like fade-through on destination change.
        // Only the content animates; the NavigationBar itself stays put.
        AnimatedContent(
            targetState = currentDestination,
            transitionSpec = {
                fadeIn(tween(140)) togetherWith fadeOut(tween(90))
            },
            label = "destination",
        ) { destination ->
        when (destination) {
            AppDestination.PLAYER -> PlayerRoute(
                appViewModel = appViewModel,
                viewModel = playerViewModel,
            )
            AppDestination.MY_AUDIO -> MyAudioScreen()
            AppDestination.FAVORITES -> FavoritesRoute(container = container)
            AppDestination.            MORE -> MoreScreen(
                container = container,
                appViewModel = appViewModel,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
        }
        }
        }
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
    val ui by viewModel.ui.collectAsState()
    val guildId = ui.selectedGuildId
    val lifecycleOwner = LocalLifecycleOwner.current
    val playbackKey = ui.queue?.nowPlaying?.let { track ->
        playbackIdentity(track, ui.queue?.nowPlayingStartedAt)
    }

    LaunchedEffect(guildId, playbackKey) {
        if (guildId == null) return@LaunchedEffect
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
