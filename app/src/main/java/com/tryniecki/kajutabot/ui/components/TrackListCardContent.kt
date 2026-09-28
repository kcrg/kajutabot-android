package com.tryniecki.kajutabot.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared artwork and text layout for queue and favorites cards. */
@Composable
fun TrackListCardContent(
    artworkUrl: String?,
    titleContent: @Composable () -> Unit,
    supportingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    reservePositionSpace: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    ListItem(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 8.dp,
            end = if (reservePositionSpace) 32.dp else 8.dp,
            bottom = 8.dp,
        ),
        leadingContent = { TrackArtwork(imageUrl = artworkUrl, modifier = Modifier.size(64.dp)) },
        supportingContent = supportingContent,
        trailingContent = trailingContent,
        content = titleContent,
    )
}
