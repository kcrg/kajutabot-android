package com.tryniecki.kajutabot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tryniecki.kajutabot.R

@Composable
fun TonalToggleIconButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @DrawableRes iconRes: Int,
    checkedContentDescription: String,
    uncheckedContentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    failure: Boolean = false,
    colors: IconToggleButtonColors = IconButtonDefaults.filledTonalIconToggleButtonColors(),
) {
    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    ) {
        ActionFeedbackIcon(
            status = if (failure) SwipeActionStatus.FAILURE else SwipeActionStatus.IDLE,
            idleIconRes = iconRes,
            contentDescription = if (failure) stringResource(R.string.action_failed)
                else if (checked) checkedContentDescription else uncheckedContentDescription,
            tint = LocalContentColor.current,
        )
    }
}
