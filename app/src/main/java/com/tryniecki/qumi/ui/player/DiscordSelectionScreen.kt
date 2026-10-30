package com.tryniecki.qumi.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tryniecki.qumi.R
import com.tryniecki.qumi.ui.components.ActionTooltip
import com.tryniecki.qumi.ui.components.DiscordTargetPicker

@Composable
fun DiscordSelectionRoute(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    isGuest: Boolean = false,
    isSupportingPane: Boolean = false,
) {
    val ui by viewModel.entryState.collectAsStateWithLifecycle()

    DiscordSelectionScreen(
        ui = ui,
        onGuildSelect = viewModel::selectGuild,
        onChannelSelect = viewModel::selectChannel,
        onBack = onBack,
        isSupportingPane = isSupportingPane,
        isGuest = isGuest,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscordSelectionScreen(
    ui: PlayerEntryState,
    onGuildSelect: (String) -> Unit,
    onChannelSelect: (String) -> Unit,
    onBack: () -> Unit,
    isSupportingPane: Boolean = false,
    isGuest: Boolean,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.discord_selection_title)) },
                navigationIcon = {
                    ActionTooltip(stringResource(if (isSupportingPane) R.string.action_close else R.string.action_back)) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(
                                    if (isSupportingPane) com.composables.icons.tabler.outline.R.drawable.tabler_ic_x_outline
                                    else com.composables.icons.tabler.outline.R.drawable.tabler_ic_arrow_left_outline,
                                ),
                                contentDescription = stringResource(if (isSupportingPane) R.string.action_close else R.string.action_back),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_selection_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(if (isGuest) R.string.discord_selection_guest_description else R.string.discord_selection_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            DiscordTargetPicker(
                guilds = ui.guilds,
                voiceChannels = ui.voiceChannels,
                selectedGuildId = ui.selectedGuildId,
                selectedChannelId = ui.selectedVoiceChannelId,
                isLoadingVoiceChannels = ui.isLoadingVoiceChannels,
                onGuildSelect = onGuildSelect,
                onChannelSelect = onChannelSelect,
                showGuildPicker = !isGuest,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onBack,
                enabled = ui.selectedGuildId != null && ui.selectedVoiceChannelId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_done))
            }
        }
    }
}
