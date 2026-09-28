package com.tryniecki.kajutabot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tryniecki.kajutabot.R

@Composable
fun SwipeActionHints(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_add_outline),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(R.string.swipe_queue_hint),
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_trash_outline),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(R.string.swipe_remove_hint),
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
