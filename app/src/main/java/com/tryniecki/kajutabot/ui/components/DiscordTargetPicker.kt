package com.tryniecki.kajutabot.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse

/** Reusable Discord guild + voice-channel picker shared by onboarding and settings flow. */
@Composable
fun DiscordTargetPicker(
    guilds: List<DiscordGuildResponse>,
    voiceChannels: List<DiscordVoiceChannelResponse>,
    selectedGuildId: String?,
    selectedChannelId: String?,
    isLoadingVoiceChannels: Boolean,
    onGuildSelect: (String) -> Unit,
    onChannelSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    showGuildPicker: Boolean = true,
) {
    Column(modifier = modifier) {
        if (showGuildPicker) {
            Text(
                text = "Serwer Discord",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(end = 8.dp),
            ) {
                items(guilds, key = { it.id }) { guild ->
                    GuildChoiceCard(
                        guild = guild,
                        selected = guild.id == selectedGuildId,
                        onClick = {
                            if (guild.id != selectedGuildId) onGuildSelect(guild.id)
                        },
                    )
                }
            }
            Spacer(Modifier.size(18.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Kanał głosowy",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (isLoadingVoiceChannels) {
                ExpressiveLoadingIndicator(modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.size(8.dp))

        when {
            selectedGuildId == null -> SelectionHint("Najpierw wybierz serwer.")
            isLoadingVoiceChannels -> VoiceChannelSkeletons()
            voiceChannels.isEmpty() -> SelectionHint("Na tym serwerze nie ma dostępnych kanałów głosowych.")
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .animateContentSize(
                        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(voiceChannels, key = { it.id }) { channel ->
                    ChannelChoiceCard(
                        channel = channel,
                        selected = channel.id == selectedChannelId,
                        onClick = { onChannelSelect(channel.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceChannelSkeletons() {
    val pulse = rememberSkeletonPulse()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) { index ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SkeletonBlock(pulse, Modifier.size(24.dp), MaterialTheme.shapes.extraLarge)
                    SkeletonBlock(
                        pulse,
                        Modifier
                            .fillMaxWidth(if (index == 1) 0.58f else 0.76f)
                            .height(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GuildChoiceCard(
    guild: DiscordGuildResponse,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .widthIn(min = 190.dp, max = 260.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GuildAvatar(
                iconUrl = guild.iconUrl,
                modifier = Modifier.size(38.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = guild.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun ChannelChoiceCard(
    channel: DiscordVoiceChannelResponse,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_volume_outline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = channel.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun SelectionHint(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
