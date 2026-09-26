package com.tryniecki.kajutabot.ui.more

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.auth.AuthState
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
    appViewModel: AppViewModel,
    playerViewModel: PlayerViewModel,
    isGuest: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenOnboarding: () -> Unit,
    onOpenLibraries: () -> Unit,
    onOpenContact: () -> Unit,
) {
    val authState by appViewModel.authState.collectAsStateWithLifecycle()
    val appUi by appViewModel.ui.collectAsStateWithLifecycle()
    val isLoggingOut = appUi.isLoggingOut
    val logoutError = appUi.accountError
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
                                onClick = appViewModel::logout,
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
                                onClick = appViewModel::switchGuestToDiscord,
                                enabled = !isLoggingOut && !appUi.isSigningIn,
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
                                        appViewModel.dismissAccountError()
                                        appViewModel.logout()
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}
