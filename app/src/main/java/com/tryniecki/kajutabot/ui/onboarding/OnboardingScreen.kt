package com.tryniecki.kajutabot.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.ui.components.GuildAvatar
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val description: String,
    val screenshotLabel: String,
    @DrawableRes val icon: Int,
)

private val onboardingPages = listOf(
    OnboardingPage(
        title = "Steruj KajutaBotem z telefonu",
        description = "Aplikacja nie odtwarza muzyki lokalnie. Steruje KajutaBotem działającym na Twoim serwerze Discord - szybko i bez potrzeby wpisywania komend w Discordzie.",
        screenshotLabel = "Odtwarzacz i aktualny utwór",
        icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_outline,
    ),
    OnboardingPage(
        title = "Dodawaj muzykę bez kombinowania",
        description = "Wyszukuj utwory, wklejaj linki albo udostępniaj je bezpośrednio z innych aplikacji. KajutaBot doda je do kolejki na wybranym kanale.",
        screenshotLabel = "Wyszukiwanie i dodawanie utworów",
        icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_music_outline,
    ),
    OnboardingPage(
        title = "Ulubione zawsze pod ręką",
        description = "Zapisuj ulubione utwory i wrzucaj je ponownie do kolejki bez ponownego szukania. Również z możliwością przelosowania kolejności.",
        screenshotLabel = "Lista ulubionych",
        icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_hearts_outline,
    ),
    OnboardingPage(
        title = "Sterowanie na każdym ekranie",
        description = "Miniplayer pokazuje aktualny utwór także poza ekranem odtwarzacza, dzięki czemu najważniejsze akcje są zawsze blisko.",
        screenshotLabel = "Miniplayer i dolna nawigacja",
        icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_player_skip_forward_outline,
    ),
)

private const val selectionPageIndex = 4
private const val pageCount = 5

@Composable
fun OnboardingScreen(
    guilds: List<DiscordGuildResponse>,
    voiceChannels: List<DiscordVoiceChannelResponse>,
    selectedGuildId: String?,
    selectedChannelId: String?,
    isLoadingVoiceChannels: Boolean,
    canDismiss: Boolean,
    onGuildSelect: (String) -> Unit,
    onChannelSelect: (String) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    fun goToPage(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page.coerceIn(0, pageCount - 1)) }
    }

    BackHandler {
        when {
            pagerState.currentPage > 0 -> goToPage(pagerState.currentPage - 1)
            canDismiss -> onDismiss()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "KajutaBot",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )

                if (canDismiss) {
                    TextButton(onClick = onDismiss) { Text("Zamknij") }
                } else if (pagerState.currentPage < selectionPageIndex) {
                    TextButton(onClick = { goToPage(selectionPageIndex) }) {
                        Text("Pomiń")
                    }
                } else {
                    Spacer(Modifier.width(72.dp))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                beyondViewportPageCount = 1,
            ) { page ->
                if (page < selectionPageIndex) {
                    OnboardingFeaturePage(onboardingPages[page])
                } else {
                    OnboardingSelectionPage(
                        guilds = guilds,
                        voiceChannels = voiceChannels,
                        selectedGuildId = selectedGuildId,
                        selectedChannelId = selectedChannelId,
                        isLoadingVoiceChannels = isLoadingVoiceChannels,
                        onGuildSelect = onGuildSelect,
                        onChannelSelect = onChannelSelect,
                    )
                }
            }

            PageIndicator(
                currentPage = pagerState.currentPage,
                pageCount = pageCount,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 14.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = { goToPage(pagerState.currentPage - 1) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Wstecz")
                    }
                }

                Button(
                    onClick = {
                        if (pagerState.currentPage == selectionPageIndex) {
                            onComplete()
                        } else {
                            goToPage(pagerState.currentPage + 1)
                        }
                    },
                    enabled = pagerState.currentPage != selectionPageIndex ||
                        (selectedGuildId != null && selectedChannelId != null),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (pagerState.currentPage == selectionPageIndex) {
                            if (canDismiss) "Gotowe" else "Zaczynamy"
                        } else {
                            "Dalej"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingFeaturePage(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenshotSlot(
            label = page.screenshotLabel,
            icon = page.icon,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 220.dp),
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OnboardingSelectionPage(
    guilds: List<DiscordGuildResponse>,
    voiceChannels: List<DiscordVoiceChannelResponse>,
    selectedGuildId: String?,
    selectedChannelId: String?,
    isLoadingVoiceChannels: Boolean,
    onGuildSelect: (String) -> Unit,
    onChannelSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        ScreenshotSlot(
            label = "Połączenie z Discordem",
            icon = com.composables.icons.tabler.outline.R.drawable.tabler_ic_brand_discord_outline,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            compact = true,
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = "Gdzie chcesz sterować botem?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Wybierz serwer Discord, a potem kanał głosowy. Ten wybór możesz później zmienić z ekranu odtwarzacza.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = "Serwer Discord",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
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

        Spacer(Modifier.height(18.dp))
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
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            selectedGuildId == null -> SelectionHint("Najpierw wybierz serwer.")
            isLoadingVoiceChannels -> SelectionHint("Pobieranie kanałów głosowych…")
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
private fun ScreenshotSlot(
    label: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 16.dp else 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(14.dp)
                            .size(if (compact) 28.dp else 36.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = label,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                if (!compact) {
                    Text(
                        text = "Miejsce na zrzut ekranu aplikacji",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun SelectionHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
            )
            .padding(18.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PageIndicator(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val width by animateDpAsState(
                targetValue = if (index == currentPage) 24.dp else 8.dp,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "onboardingIndicatorWidth",
            )
            Surface(
                modifier = Modifier
                    .height(8.dp)
                    .width(width),
                shape = RoundedCornerShape(999.dp),
                color = if (index == currentPage) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            ) {}
        }
    }
}
