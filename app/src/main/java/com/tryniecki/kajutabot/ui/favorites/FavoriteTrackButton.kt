package com.tryniecki.kajutabot.ui.favorites

import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import com.tryniecki.kajutabot.api.model.common.TrackResponse

@Composable
fun FavoriteTrackButton(
    track: TrackResponse,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (TrackResponse) -> Unit,
) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = { onToggle(track) },
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_heart_outline),
            contentDescription = if (checked) "Usuń z ulubionych" else "Dodaj do ulubionych",
            tint = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
