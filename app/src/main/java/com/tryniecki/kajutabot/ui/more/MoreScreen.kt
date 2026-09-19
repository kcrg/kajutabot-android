package com.tryniecki.kajutabot.ui.more

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
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
    onOpenOnboarding: () -> Unit,
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
            onOpenOnboarding = onOpenOnboarding,
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
    onOpenOnboarding: () -> Unit,
    onOpenLibraries: () -> Unit,
    onOpenContact: () -> Unit,
) {
    val authState by appViewModel.authState.collectAsStateWithLifecycle()
    var isLoggingOut by remember { mutableStateOf(false) }
    var logoutError by remember { mutableStateOf<String?>(null) }

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
                                    text = if (user != null) {
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
                    onClick = onOpenLibraries,
                )
            }
            item {
                SettingsRow(
                    icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_mail_outline,
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
private fun ContactScreen(container: AppContainer, onBack: () -> Unit) {
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
private fun SettingsRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick),
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
        colors = ListItemDefaults.colors(),
        elevation = ListItemDefaults.elevation(ListItemDefaults.Elevation),
        content = { Text(title) },
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
