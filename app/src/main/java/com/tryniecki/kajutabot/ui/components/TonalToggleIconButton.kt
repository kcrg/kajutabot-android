package com.tryniecki.kajutabot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

@Composable
fun TonalToggleIconButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @DrawableRes iconRes: Int,
    checkedContentDescription: String,
    uncheckedContentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconToggleButtonColors = IconButtonDefaults.filledTonalIconToggleButtonColors(),
) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = if (checked) checkedContentDescription else uncheckedContentDescription,
        )
    }
}
