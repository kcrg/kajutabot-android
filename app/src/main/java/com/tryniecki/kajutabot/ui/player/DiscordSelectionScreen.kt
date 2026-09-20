package com.tryniecki.kajutabot.ui.player

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tryniecki.kajutabot.ui.components.DiscordTargetPicker

@Composable
fun DiscordSelectionRoute(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val ui by viewModel.entryState.collectAsStateWithLifecycle()

    DiscordSelectionScreen(
        ui = ui,
        onGuildSelect = viewModel::selectGuild,
        onChannelSelect = viewModel::selectChannel,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscordSelectionScreen(
    ui: PlayerEntryState,
    onGuildSelect: (String) -> Unit,
    onChannelSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Serwer i kanał") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_arrow_left_outline),
                            contentDescription = "Wróć",
                        )
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
                text = "Gdzie chcesz sterować botem?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Wybierz serwer Discord, a potem kanał głosowy używany przez odtwarzacz.",
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
                Text("Gotowe")
            }
        }
    }
}
