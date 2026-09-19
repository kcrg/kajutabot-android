package com.tryniecki.kajutabot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tryniecki.kajutabot.R

/**
 * Backend-provided track artwork with an optional caller-supplied fallback and a broken state.
 *
 * [imageUrl] is used verbatim. If it is missing or cannot be loaded, [fallbackImageUrl]
 * is tried when supplied. Otherwise a visible broken-image state is shown.
 *
 * The caller must size this composable (e.g. `Modifier.size(56.dp)` or
 * `fillMaxWidth + aspectRatio`); the image and the broken state fill that space.
 */
@Composable
fun TrackArtwork(
    imageUrl: String?,
    fallbackImageUrl: String? = null,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    shape: Shape = RoundedCornerShape(8.dp),
    brokenIconSize: Dp = 24.dp,
    showMissingLabel: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val primary = remember(imageUrl) { resolveArtworkSource(imageUrl) }
    val fallback = remember(fallbackImageUrl) { resolveArtworkSource(fallbackImageUrl) }
    var primaryFailed by remember(imageUrl, fallbackImageUrl) { mutableStateOf(false) }
    var fallbackFailed by remember(imageUrl, fallbackImageUrl) { mutableStateOf(false) }
    val source = when {
        primary is ArtworkSource.Remote && !primaryFailed -> primary
        fallback is ArtworkSource.Remote && fallback.url != (primary as? ArtworkSource.Remote)?.url && !fallbackFailed -> fallback
        else -> ArtworkSource.Missing
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        when {
            source is ArtworkSource.Missing -> BrokenArtwork(
                isLoadError = primaryFailed || fallbackFailed,
                iconSize = brokenIconSize,
                showLabel = showMissingLabel,
            )
            else -> AsyncImage(
                model = (source as ArtworkSource.Remote).url,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
                onError = {
                    if (source == primary) primaryFailed = true else fallbackFailed = true
                },
            )
        }
    }
}

@Composable
private fun BrokenArtwork(
    isLoadError: Boolean,
    iconSize: Dp,
    showLabel: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_file_music_outline),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showLabel) {
            Text(
                text = if (isLoadError) "Błąd ładowania" else "Brak miniatury",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Discord guild avatar. A missing [iconUrl] is a normal Discord state (guilds may have
 * no icon), so it shows the regular server icon — this is a UI avatar placeholder,
 * not a track-artwork fallback.
 */
@Composable
fun GuildAvatar(
    iconUrl: String?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    val source = remember(iconUrl) { resolveArtworkSource(iconUrl) }
    var loadFailed by remember(iconUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (source is ArtworkSource.Remote && !loadFailed) {
            AsyncImage(
                model = source.url,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { loadFailed = false },
                onError = { loadFailed = true },
            )
        } else {
            Icon(
                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_server_outline),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
