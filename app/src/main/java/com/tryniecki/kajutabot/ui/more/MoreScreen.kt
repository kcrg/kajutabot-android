package com.tryniecki.kajutabot.ui.more

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.ui.navigation.MoreDestination
import com.tryniecki.kajutabot.ui.theme.ThemeMode

@Composable
fun MoreScreen(
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

    when (destination) {
        MoreDestination.ROOT -> MoreRootScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onOpenLibraries = {
                destination = MoreDestination.LIBRARIES
            },
            onOpenContact = {
                destination = MoreDestination.CONTACT
            },
        )

        MoreDestination.LIBRARIES -> LibrariesScreen(
            onBack = {
                destination = MoreDestination.ROOT
            },
        )

        MoreDestination.CONTACT -> ContactScreen(
            onBack = {
                destination = MoreDestination.ROOT
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreRootScreen(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenLibraries: () -> Unit,
    onOpenContact: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Więcej")
                },
            )
        },
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
            item {
                SectionTitle("Wygląd")
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Motyw",
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Text(
                        text = "Natywny używa kolorów Material You na Androidzie 12+ i trybu systemowego.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = {
                                    onThemeModeChange(mode)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = ThemeMode.entries.size,
                                ),
                            ) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            item {
                SectionTitle("O aplikacji")
            }

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
private fun LibrariesScreen(
    onBack: () -> Unit,
) {
    val libraries = listOf(
        LibraryInfo(
            name = "Jetpack Compose",
            description = "Deklaratywny UI Androida",
            license = "Apache 2.0",
        ),
        LibraryInfo(
            name = "Material 3",
            description = "Natywne komponenty i system motywów",
            license = "Apache 2.0",
        ),
        LibraryInfo(
            name = "Tabler Icons",
            description = "Ikony interfejsu",
            license = "MIT",
        ),
        LibraryInfo(
            name = "Retrofit",
            description = "Klient REST",
            license = "Apache 2.0",
        ),
        LibraryInfo(
            name = "OkHttp",
            description = "Transport HTTP",
            license = "Apache 2.0",
        ),
        LibraryInfo(
            name = "kotlinx.serialization",
            description = "Serializacja JSON",
            license = "Apache 2.0",
        ),
    )

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Użyte biblioteki",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            items(
                items = libraries,
                key = { it.name },
            ) { library ->
                ListItem(
                    headlineContent = {
                        Text(library.name)
                    },
                    supportingContent = {
                        Text(library.description)
                    },
                    trailingContent = {
                        Text(library.license)
                    },
                )

                if (library != libraries.last()) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ContactScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val colorScheme = MaterialTheme.colorScheme

    val customTabColors =
        CustomTabColorSchemeParams.Builder()
            .setToolbarColor(
                colorScheme.surfaceContainer.toArgb(),
            )
            .setNavigationBarColor(
                colorScheme.surface.toArgb(),
            )
            .setNavigationBarDividerColor(
                colorScheme.outlineVariant.toArgb(),
            )
            .build()

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Kontakt",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ListItem(
                headlineContent = {
                    Text("Kacper Tryniecki")
                },
                supportingContent = {
                    Text("Autor KajutaBot")
                },
            )

            HorizontalDivider()

            SettingsRow(
                icon = R.drawable.kb_ic_mail,
                title = "kacper@tryniecki.com",
                subtitle = "E-mail",
                onClick = {
                    uriHandler.openUri(
                        "mailto:kacper@tryniecki.com",
                    )
                },
            )

            SettingsRow(
                icon = R.drawable.kb_ic_brand_github,
                title = "github.com/kcrg",
                subtitle = "GitHub",
                onClick = {
                    openCustomTab(
                        context = context,
                        uriHandler = uriHandler,
                        url = "https://github.com/kcrg",
                        colors = customTabColors,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackTopBar(
    title: String,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(title)
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
            ) {
                Icon(
                    painter = painterResource(
                        R.drawable.kb_ic_arrow_left,
                    ),
                    contentDescription = "Wstecz",
                )
            }
        },
    )
}

@Composable
private fun SectionTitle(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(
            top = 8.dp,
        ),
    )
}

@Composable
private fun SettingsRow(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(
            onClick = onClick,
        ),
        leadingContent = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = {
            Text(title)
        },
        supportingContent = {
            Text(subtitle)
        },
        trailingContent = {
            Icon(
                painter = painterResource(
                    R.drawable.kb_ic_chevron_right,
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

private fun openCustomTab(
    context: Context,
    uriHandler: UriHandler,
    url: String,
    colors: CustomTabColorSchemeParams,
) {
    val customTabsIntent =
        CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colors)
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_ON)
            .build()

    try {
        customTabsIntent.launchUrl(
            context,
            url.toUri(),
        )
    } catch (_: ActivityNotFoundException) {
        uriHandler.openUri(url)
    }
}

private data class LibraryInfo(
    val name: String,
    val description: String,
    val license: String,
)