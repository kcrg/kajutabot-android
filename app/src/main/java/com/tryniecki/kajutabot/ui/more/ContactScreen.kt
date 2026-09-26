package com.tryniecki.kajutabot.ui.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.browser.openCustomTab
import com.tryniecki.kajutabot.browser.rememberCustomTabColors

@Composable
fun ContactScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            BackTopBar(
                title = stringResource(R.string.more_contact_title),
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
                verticalAlignment = Alignment.CenterVertically,
                supportingContent = { Text(stringResource(R.string.contact_kajutabot_author)) },
                colors = ListItemDefaults.colors(),
                content = { Text(stringResource(R.string.more_contact_author)) },
            )
            HorizontalDivider()
            ContactLinks()
        }
    }
}

@Composable
private fun ContactLinks() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
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
