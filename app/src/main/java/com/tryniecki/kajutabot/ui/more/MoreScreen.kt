package com.tryniecki.kajutabot.ui.more

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tryniecki.kajutabot.AppContainer
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.auth.AuthState
import com.tryniecki.kajutabot.auth.LogoutResult
import com.tryniecki.kajutabot.browser.openCustomTab
import com.tryniecki.kajutabot.browser.rememberCustomTabColors
import com.tryniecki.kajutabot.ui.app.AppViewModel
import com.tryniecki.kajutabot.ui.navigation.MoreDestination
import com.tryniecki.kajutabot.ui.theme.KbMotion
import com.tryniecki.kajutabot.ui.theme.ThemeMode

@Composable
fun MoreScreen(
    container: AppContainer,
    appViewModel: AppViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    var destination by rememberSaveable {
        mutableStateOf(MoreDestination.ROOT)
    }

    BackHandler(
        enabled = destination != MoreDestination.ROOT,
    ) {
        destination = MoreDestination.ROOT
    }

    // Hierarchical navigation: forward pushes content in from the right,
    // Back reverses the direction. Shared-axis X, no full-screen travel.
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = destination,
        transitionSpec = {
            val forward = targetState != MoreDestination.ROOT
            val direction = if (forward) 1 else -1
            (fadeIn(motion.defaultEffectsSpec()) +
                slideInHorizontally(motion.defaultSpatialSpec()) {
                    direction * (it * KbMotion.HIERARCHY_SLIDE_FRACTION).toInt()
                }) togetherWith
                (fadeOut(motion.defaultEffectsSpec()) +
                    slideOutHorizontally(motion.defaultSpatialSpec()) {
                        -direction * (it * KbMotion.HIERARCHY_SLIDE_FRACTION).toInt()
                    })
        },
        label = "moreDestination",
    ) { current ->
    when (current) {
        MoreDestination.ROOT -> MoreRootScreen(
            container = container,
            appViewModel = appViewModel,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onOpenLibraries = { destination = MoreDestination.LIBRARIES },
            onOpenContact = { destination = MoreDestination.CONTACT },
        )
        MoreDestination.LIBRARIES -> LibrariesScreen(onBack = { destination = MoreDestination.ROOT })
        MoreDestination.CONTACT -> ContactScreen(
            container = container,
            onBack = { destination = MoreDestination.ROOT },
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreRootScreen(
    container: AppContainer,
    appViewModel: AppViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenLibraries: () -> Unit,
    onOpenContact: () -> Unit,
) {
    val authState by appViewModel.authState.collectAsStateWithLifecycle()
    var isLoggingOut by remember { mutableStateOf(false) }
    var logoutError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Więcej") }) },
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
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            user?.displayName ?: "—",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (user != null) "@${user.username}" else "Niezalogowany",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (logoutError != null) {
                            Text(
                                logoutError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                }
                                Text("Wyloguj")
                            }
                            if (logoutError != null) {
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
                                ) { Text("Ponów") }
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionTitle("Wygląd") }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Motyw", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Natywny używa kolorów Material You na Androidzie 12+ i trybu systemowego.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            item { SectionTitle("O aplikacji") }

            item {
                SettingsRow(
                    icon = R.drawable.kb_ic_info_circle,
                    title = "Użyte biblioteki",
                    subtitle = "Licencje i komponenty open source",
                    onClick = onOpenLibraries,
                )
            }
            item {
                SettingsRow(
                    icon = R.drawable.kb_ic_mail,
                    title = "Kontakt",
                    subtitle = "Kacper Tryniecki",
                    onClick = onOpenContact,
                )
            }
        }
    }
}

@Composable
private fun LibrariesScreen(onBack: () -> Unit) {
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
                    headlineContent = { Text(library.name) },
                    supportingContent = { Text(library.description) },
                    trailingContent = { Text(library.license) },
                )
                if (library != LIBRARIES.last()) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ContactScreen(container: AppContainer, onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(title = "Kontakt", onBack = onBack) }) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ListItem(
                headlineContent = { Text("Kacper Tryniecki") },
                supportingContent = { Text("Autor KajutaBot") },
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
        icon = R.drawable.kb_ic_mail,
        title = "kacper@tryniecki.com",
        subtitle = "E-mail",
        onClick = { uriHandler.openUri("mailto:kacper@tryniecki.com") },
    )
    SettingsRow(
        icon = R.drawable.kb_ic_brand_github,
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
                Icon(painter = painterResource(R.drawable.kb_ic_arrow_left), contentDescription = "Wstecz")
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
private fun SettingsRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.kb_ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

private data class LibraryInfo(val name: String, val description: String, val license: String)

private val LIBRARIES = listOf(
    LibraryInfo("Jetpack Compose", "Deklaratywny UI Androida", "Apache 2.0"),
    LibraryInfo("Material 3", "Natywne komponenty i system motywów", "Apache 2.0"),
    LibraryInfo("Tabler Icons", "Ikony interfejsu", "MIT"),
    LibraryInfo("Retrofit", "Klient REST", "Apache 2.0"),
    LibraryInfo("OkHttp", "Transport HTTP", "Apache 2.0"),
    LibraryInfo("kotlinx.serialization", "Serializacja JSON", "Apache 2.0"),
)
