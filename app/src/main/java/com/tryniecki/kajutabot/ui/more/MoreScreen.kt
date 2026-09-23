package com.tryniecki.kajutabot.ui.more

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.auth.AuthState
import com.tryniecki.kajutabot.auth.LogoutResult
import com.tryniecki.kajutabot.api.model.auth.SessionType
import com.tryniecki.kajutabot.browser.openCustomTab
import com.tryniecki.kajutabot.browser.rememberCustomTabColors
import com.tryniecki.kajutabot.browser.rememberOpenCustomTab
import com.tryniecki.kajutabot.ui.app.AppViewModel
import com.tryniecki.kajutabot.ui.player.PlayerViewModel
import com.tryniecki.kajutabot.ui.player.RealtimeConnectionState
import com.tryniecki.kajutabot.ui.theme.ThemeMode
import com.tryniecki.kajutabot.ui.text.UiText
import com.tryniecki.kajutabot.ui.text.asString
import com.tryniecki.kajutabot.ui.text.uiText
import kotlinx.coroutines.delay

@Composable
fun MoreRootScreen(
    container: AppContainer,
    appViewModel: AppViewModel,
    playerViewModel: PlayerViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenLibraries: () -> Unit,
    onOpenContact: () -> Unit,
) {
    val authState by appViewModel.authState.collectAsStateWithLifecycle()
    var isLoggingOut by remember { mutableStateOf(false) }
    var logoutError by remember { mutableStateOf<UiText?>(null) }
    val isGuest = container.sessionManager.currentUserSession()?.sessionType == SessionType.GUEST
    val openCustomTab = rememberOpenCustomTab()

    Scaffold(
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { SectionTitle(stringResource(R.string.more_section_account)) }

            item {
                val user = (authState as? AuthState.SignedIn)?.user

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!isGuest) {
                                AsyncImage(
                                    model = user?.avatarUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop,
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = user?.displayName ?: "-",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                Text(
                                    text = if (isGuest) {
                                        stringResource(R.string.more_guest_mode)
                                    } else if (user != null) {
                                        "@${user.username}"
                                    } else {
                                        stringResource(R.string.more_signed_out)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Button(
                                onClick = {
                                    isLoggingOut = true
                                    logoutError = null

                                    appViewModel.logout { result ->
                                        isLoggingOut = false

                                        if (result is LogoutResult.NeedsRetry) {
                                            logoutError = result.message
                                        }
                                    }
                                },
                                enabled = !isLoggingOut,
                            ) {
                                if (isLoggingOut) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(stringResource(R.string.action_logout))
                                }
                            }
                        }

                        if (isGuest) {
                            Button(
                                onClick = { openCustomTab("https://discord.gg/7jV7j5djF") },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.action_join_test_server)) }

                            OutlinedButton(
                                onClick = {
                                    logoutError = null
                                    appViewModel.switchGuestToDiscord { logoutError = it }
                                },
                                enabled = !isLoggingOut,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(stringResource(R.string.action_sign_in_discord)) }
                        }

                        if (logoutError != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = logoutError?.asString().orEmpty(),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )

                                OutlinedButton(
                                    onClick = {
                                        logoutError = null
                                        isLoggingOut = true

                                        appViewModel.logout { result ->
                                            isLoggingOut = false

                                            if (result is LogoutResult.NeedsRetry) {
                                                logoutError = result.message
                                            }
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.action_retry_short))
                                }
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle(stringResource(R.string.more_section_realtime)) }
            item { RealtimeStatusCard(playerViewModel) }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle(stringResource(R.string.more_section_theme)) }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size,
                                ),
                            ) { Text(stringResource(mode.labelResId)) }
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle(stringResource(R.string.more_section_help)) }

            item {
                SettingsRow(
                    icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_directions_outline,
                    title = stringResource(R.string.more_guide_title),
                    subtitle = stringResource(R.string.more_guide_subtitle),
                    useContainer = true,
                    onClick = onOpenOnboarding,
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle(stringResource(R.string.more_section_about)) }

            item {
                SettingsRow(
                    icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_info_circle_outline,
                    title = stringResource(R.string.more_libraries_title),
                    subtitle = stringResource(R.string.more_libraries_subtitle),
                    useContainer = true,
                    onClick = onOpenLibraries,
                )
            }
            item {
                SettingsRow(
                    icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_mail_outline,
                    title = stringResource(R.string.more_contact_title),
                    subtitle = stringResource(R.string.more_contact_author),
                    useContainer = true,
                    onClick = onOpenContact,
                )
            }
        }
    }
}

@Composable
private fun RealtimeStatusCard(playerViewModel: PlayerViewModel) {
    val diagnostics by playerViewModel.realtimeDiagnostics.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var nowMs by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                nowMs = SystemClock.elapsedRealtime()
                delay(1_000)
            }
        }
    }
    val status = when (diagnostics.state) {
        RealtimeConnectionState.DISCONNECTED -> R.string.realtime_disconnected
        RealtimeConnectionState.CONNECTING -> R.string.realtime_connecting
        RealtimeConnectionState.CONNECTED_NO_GUILD -> R.string.realtime_connected_no_guild
        RealtimeConnectionState.SUBSCRIBING -> R.string.realtime_subscribing
        RealtimeConnectionState.CONNECTED_AND_SUBSCRIBED -> R.string.realtime_connected
        RealtimeConnectionState.RECONNECTING -> R.string.realtime_reconnecting
    }
    val statusColor = if (diagnostics.state == RealtimeConnectionState.CONNECTED_AND_SUBSCRIBED) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("●", color = statusColor, style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(status), style = MaterialTheme.typography.titleSmall)
            }
            Text(
                stringResource(R.string.realtime_last_frame, realtimeAgeText(diagnostics.lastFrameAtMs, nowMs).asString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.realtime_last_update, realtimeAgeText(diagnostics.lastQueueUpdateAtMs, nowMs).asString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun realtimeAgeText(timestampMs: Long?, nowMs: Long): UiText {
    if (timestampMs == null) return uiText(R.string.realtime_no_data)
    val seconds = ((nowMs - timestampMs).coerceAtLeast(0L)) / 1_000L
    return when {
        seconds < 60 -> uiText(R.string.realtime_seconds_ago, seconds)
        seconds < 3_600 -> uiText(R.string.realtime_minutes_ago, seconds / 60)
        else -> uiText(R.string.realtime_hours_ago, seconds / 3_600)
    }
}

@Composable
fun LibrariesScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(title = stringResource(R.string.more_libraries_title), onBack = onBack) }) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            items(LIBRARIES, key = { it.name }) { library ->
                ListItem(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier,
                    leadingContent = null,
                    trailingContent = { Text(library.license) },
                    overlineContent = null,
                    supportingContent = { Text(stringResource(library.descriptionResId)) },
                    colors = ListItemDefaults.colors(),
                    elevation = ListItemDefaults.elevation(),
                    content = { Text(library.name) },
                )
                if (library != LIBRARIES.last()) HorizontalDivider()
            }
        }
    }
}

@Composable
fun ContactScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(title = stringResource(R.string.more_contact_title), onBack = onBack) }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ListItem(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier,
                leadingContent = null,
                trailingContent = null,
                overlineContent = null,
                supportingContent = { Text(stringResource(R.string.contact_kajutabot_author)) },
                colors = ListItemDefaults.colors(),
                elevation = ListItemDefaults.elevation(),
                content = { Text(stringResource(R.string.more_contact_author)) },
            )
            HorizontalDivider()
            ContactLinks()
        }
    }
}

@Composable
private fun ContactLinks() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val colors = rememberCustomTabColors()
    SettingsRow(
        icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_mail_outline,
        title = "kacper@tryniecki.com",
        subtitle = stringResource(R.string.contact_email_label),
        onClick = { uriHandler.openUri("mailto:kacper@tryniecki.com") },
    )
    SettingsRow(
        icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_brand_github_outline,
        title = "github.com/kcrg",
        subtitle = "GitHub",
        onClick = {
            openCustomTab(context, uriHandler, "https://github.com/kcrg", colors)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_arrow_left_outline), contentDescription = stringResource(R.string.action_back))
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    useContainer: Boolean = false,
) {
    ListItem(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (useContainer) {
            Modifier
                .clip(MaterialTheme.shapes.large)
                .clickable(onClick = onClick)
        } else {
            Modifier.clickable(onClick = onClick)
        },
        leadingContent = {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        trailingContent = {
                Icon(
                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_chevron_right_outline),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        overlineContent = null,
        supportingContent = { Text(subtitle) },
        colors = ListItemDefaults.colors(
            containerColor = if (useContainer) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                ListItemDefaults.containerColor
            },
        ),
        elevation = ListItemDefaults.elevation(),
        content = { Text(title) },
    )
}

private data class LibraryInfo(val name: String, @StringRes val descriptionResId: Int, val license: String)

private val LIBRARIES = listOf(
    LibraryInfo("Jetpack Compose", R.string.library_compose_description, "Apache 2.0"),
    LibraryInfo("Material 3", R.string.library_material3_description, "Apache 2.0"),
    LibraryInfo("AndroidX Activity", R.string.library_activity_description, "Apache 2.0"),
    LibraryInfo("Navigation Compose", R.string.library_navigation_description, "Apache 2.0"),
    LibraryInfo("AndroidX Lifecycle", R.string.library_lifecycle_description, "Apache 2.0"),
    LibraryInfo("AndroidX Core KTX", R.string.library_core_ktx_description, "Apache 2.0"),
    LibraryInfo("AndroidX Media3", R.string.library_media3_description, "Apache 2.0"),
    LibraryInfo("AndroidX Browser", R.string.library_browser_description, "Apache 2.0"),
    LibraryInfo("Coil 3", R.string.library_coil_description, "Apache 2.0"),
    LibraryInfo("Tabler Icons", R.string.library_tabler_description, "MIT"),
    LibraryInfo("Kotlin Coroutines", R.string.library_coroutines_description, "Apache 2.0"),
    LibraryInfo("Retrofit", R.string.library_retrofit_description, "Apache 2.0"),
    LibraryInfo("SignalR Java Client", R.string.library_signalr_description, "MIT"),
    LibraryInfo("OkHttp", R.string.library_okhttp_description, "Apache 2.0"),
    LibraryInfo("kotlinx.serialization", R.string.library_serialization_description, "Apache 2.0"),
)
