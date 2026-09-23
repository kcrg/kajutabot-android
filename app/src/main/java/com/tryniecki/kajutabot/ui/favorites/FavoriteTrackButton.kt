package com.tryniecki.kajutabot.ui.favorites

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.ui.components.TonalToggleIconButton

@Composable
fun FavoriteTrackButton(
    track: PlaybackTrackResponse,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (PlaybackTrackResponse) -> Unit,
) {
    TonalToggleIconButton(
        checked = checked,
        onCheckedChange = { onToggle(track) },
        iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_heart_outline,
        checkedContentDescription = stringResource(R.string.action_remove_favorite),
        uncheckedContentDescription = stringResource(R.string.action_add_favorite),
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
