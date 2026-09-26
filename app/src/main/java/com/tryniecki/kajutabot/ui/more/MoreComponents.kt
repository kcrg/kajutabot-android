package com.tryniecki.kajutabot.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.tryniecki.kajutabot.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackTopBar(
    title: String,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(
                        com.composables.icons.tabler.outline.R.drawable.tabler_ic_arrow_left_outline,
                    ),
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        },
    )
}

@Composable
internal fun SettingsRow(
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
                painter = painterResource(
                    com.composables.icons.tabler.outline.R.drawable.tabler_ic_chevron_right_outline,
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent = { Text(subtitle) },
        colors = ListItemDefaults.colors(
            containerColor = if (useContainer) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                ListItemDefaults.containerColor
            },
        ),
        content = { Text(title) },
    )
}
