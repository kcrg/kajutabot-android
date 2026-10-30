package com.tryniecki.kajutabot.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

/** Names an icon-only control on hover, keyboard focus and long press. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionTooltip(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = TooltipAnchorPosition.Above,
        ),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        modifier = if (enabled) modifier.pointerHoverIcon(PointerIcon.Hand) else modifier,
        content = content,
    )
}
