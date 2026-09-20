package com.tryniecki.kajutabot.ui.favorites

import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.tryniecki.kajutabot.api.model.common.TrackResponse
import com.tryniecki.kajutabot.ui.components.TonalToggleIconButton

@Composable
fun FavoriteTrackButton(
    track: TrackResponse,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (TrackResponse) -> Unit,
) {
    TonalToggleIconButton(
        checked = checked,
        onCheckedChange = { onToggle(track) },
        iconRes = com.composables.icons.tabler.outline.R.drawable.tabler_ic_heart_outline,
        checkedContentDescription = "Usuń z ulubionych",
        uncheckedContentDescription = "Dodaj do ulubionych",
        enabled = enabled,
        colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
