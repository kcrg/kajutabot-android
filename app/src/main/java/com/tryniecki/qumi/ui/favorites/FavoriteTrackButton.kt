package com.tryniecki.qumi.ui.favorites

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tryniecki.qumi.R
import com.tryniecki.qumi.api.model.common.PlaybackTrackResponse
import com.tryniecki.qumi.ui.components.TonalToggleIconButton
import com.tryniecki.qumi.ui.components.SwipeActionStatus

@Composable
fun FavoriteTrackButton(
    track: PlaybackTrackResponse,
    checked: Boolean,
    enabled: Boolean,
    status: SwipeActionStatus = SwipeActionStatus.IDLE,
    onToggle: (PlaybackTrackResponse) -> Unit,
) {
    if (!track.supportsFavorites) return
    TonalToggleIconButton(
        checked = checked,
        onCheckedChange = { onToggle(track) },
        iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_heart_outline,
        checkedContentDescription = stringResource(R.string.action_remove_favorite),
        uncheckedContentDescription = stringResource(R.string.action_add_favorite),
        enabled = enabled,
        failure = status == SwipeActionStatus.FAILURE,
        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
