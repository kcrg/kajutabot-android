package com.tryniecki.kajutabot.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.ui.components.TrackListCardContent

/** Shared queue row layout; callers supply only the actions available on their screen. */
@Composable
internal fun QueueTrackCardContent(
    track: PlaybackTrackResponse,
    position: Int,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth()) {
        TrackListCardContent(
            artworkUrl = track.artworkUrl,
            reservePositionSpace = trailingContent == null,
            supportingContent = { Text(formatDuration(track.durationMilliseconds)) },
            trailingContent = trailingContent,
            titleContent = {
                Text(
                    track.title,
                    modifier = titleModifier,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        QueuePositionIndicator(position, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
internal fun QueuePositionIndicator(position: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.player_queue_position, position)
    Text(
        text = position.toString(),
        modifier = modifier
            .padding(top = 4.dp, end = 4.dp)
            .semantics { contentDescription = description },
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
