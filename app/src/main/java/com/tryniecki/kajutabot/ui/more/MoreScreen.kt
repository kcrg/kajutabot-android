package com.tryniecki.kajutabot.ui.more

import android.os.SystemClock
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
import com.tryniecki.kajutabot.ui.app.AppViewModel
import com.tryniecki.kajutabot.ui.player.PlayerViewModel
import com.tryniecki.kajutabot.ui.player.RealtimeConnectionState
import com.tryniecki.kajutabot.ui.theme.ThemeMode
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
    var logoutError by remember { mutableStateOf<String?>(null) }
    val isGuest = container.sessionManager.currentUserSession()?.sessionType == SessionType.GUEST

    Scaffold(
        //topBar = { TopAppBar(title = { Text("Więcej") }) },
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
            item { SectionTitle("Konto") }

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
                            AsyncImage(
                                model = user?.avatarUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(MaterialTheme.shapes.medium),
                                contentScale = ContentScale.Crop,
                            )

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
                                        "Tryb gościa"
                                    } else if (user != null) {
                                        "@${user.username}"
                                    } else {
                                        "Niezalogowany"
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
                                    Text("Wyloguj")
                                }
                            }
                        }

                        if (isGuest) {
                            OutlinedButton(
                                onClick = {
                                    logoutError = null
                                    appViewModel.switchGuestToDiscord { logoutError = it }
                                },
                                enabled = !isLoggingOut,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Zaloguj przez Discord") }
                        }

                        if (logoutError != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = logoutError.orEmpty(),
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
                                    Text("Ponów")
                                }
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle("Połączenie realtime") }
            item { RealtimeStatusCard(playerViewModel) }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle("Motyw") }

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
                            ) { Text(mode.label) }
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle("Pomoc") }

            item {
                SettingsRow(
                    icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_directions_outline,
                    title = "Przewodnik po aplikacji",
                    subtitle = "Uruchom onboarding ponownie",
                    useContainer = true,
                    onClick = onOpenOnboarding,
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle("O aplikacji") }

            item {
                SettingsRow(
                    icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_info_circle_outline,
                    title = "Użyte biblioteki",
                    subtitle = "Licencje i komponenty open source",
                    useContainer = true,
                    onClick = onOpenLibraries,
                )
            }
            item {
                SettingsRow(
                    icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_mail_outline,
                    title = "Kontakt",
                    subtitle = "Kacper Tryniecki",
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
        RealtimeConnectionState.DISCONNECTED -> "Rozłączono"
        RealtimeConnectionState.CONNECTING -> "Łączenie…"
        RealtimeConnectionState.CONNECTED_NO_GUILD -> "Połączono · brak wybranego serwera"
        RealtimeConnectionState.SUBSCRIBING -> "Subskrybowanie serwera…"
        RealtimeConnectionState.CONNECTED_AND_SUBSCRIBED -> "Połączono"
        RealtimeConnectionState.RECONNECTING -> "Ponowne łączenie…"
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
                Text(status, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                "Ostatnia ramka: ${realtimeAgeLabel(diagnostics.lastFrameAtMs, nowMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Ostatnia aktualizacja danych: ${realtimeAgeLabel(diagnostics.lastQueueUpdateAtMs, nowMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun realtimeAgeLabel(timestampMs: Long?, nowMs: Long): String {
    if (timestampMs == null) return "brak danych"
    val seconds = ((nowMs - timestampMs).coerceAtLeast(0L)) / 1_000L
    return when {
        seconds < 60 -> "$seconds s temu"
        seconds < 3_600 -> "${seconds / 60} min temu"
        else -> "${seconds / 3_600} godz. temu"
    }
}

@Composable
fun LibrariesScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(title = "Użyte biblioteki", onBack = onBack) }) { innerPadding ->
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
                    supportingContent = { Text(library.description) },
                    colors = ListItemDefaults.colors(),
                    elevation = ListItemDefaults.elevation(ListItemDefaults.Elevation),
                    content = { Text(library.name) },
                )
                if (library != LIBRARIES.last()) HorizontalDivider()
            }
        }
    }
}

@Composable
fun ContactScreen(container: AppContainer, onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(title = "Kontakt", onBack = onBack) }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ListItem(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier,
                leadingContent = null,
                trailingContent = null,
                overlineContent = null,
                supportingContent = { Text("Autor KajutaBot") },
                colors = ListItemDefaults.colors(),
                elevation = ListItemDefaults.elevation(ListItemDefaults.Elevation),
                content = { Text("Kacper Tryniecki") },
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
        subtitle = "E-mail",
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
                Icon(painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_arrow_left_outline), contentDescription = "Wstecz")
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
        elevation = ListItemDefaults.elevation(ListItemDefaults.Elevation),
        content = { Text(title) },
    )
}

private data class LibraryInfo(val name: String, val description: String, val license: String)

private val LIBRARIES = listOf(
    LibraryInfo("Jetpack Compose", "Budowa deklaratywnego interfejsu i animacji", "Apache 2.0"),
    LibraryInfo("Material 3", "Komponenty, motywy i schemat ruchu interfejsu", "Apache 2.0"),
    LibraryInfo("AndroidX Activity", "Integracja Compose z aktywnością i obsługa gestu wstecz", "Apache 2.0"),
    LibraryInfo("Navigation Compose", "Nawigacja między ekranami i stos powrotu", "Apache 2.0"),
    LibraryInfo("AndroidX Lifecycle", "ViewModel i obserwacja stanu zgodna z cyklem życia", "Apache 2.0"),
    LibraryInfo("AndroidX Core KTX", "Funkcje pomocnicze dla platformy Android", "Apache 2.0"),
    LibraryInfo("AndroidX Media3", "Sesja multimedialna i systemowe sterowanie botem", "Apache 2.0"),
    LibraryInfo("AndroidX Browser", "Logowanie i otwieranie linków przez Custom Tabs", "Apache 2.0"),
    LibraryInfo("Coil 3", "Ładowanie miniatur utworów i awatarów", "Apache 2.0"),
    LibraryInfo("Tabler Icons", "Ikony interfejsu", "MIT"),
    LibraryInfo("Kotlin Coroutines", "Operacje asynchroniczne i przepływy stanu", "Apache 2.0"),
    LibraryInfo("Retrofit", "Wywołania KajutaBot Control API", "Apache 2.0"),
    LibraryInfo("SignalR Java Client", "Aktualizacje kolejki w czasie rzeczywistym", "MIT"),
    LibraryInfo("OkHttp", "Połączenia HTTP dla API i obrazów", "Apache 2.0"),
    LibraryInfo("kotlinx.serialization", "Odczyt i zapis danych JSON", "Apache 2.0"),
)
