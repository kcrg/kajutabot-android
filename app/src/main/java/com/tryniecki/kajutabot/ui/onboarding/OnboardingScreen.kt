package com.tryniecki.kajutabot.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.requiredWidth
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.ui.components.GuildAvatar
import com.tryniecki.kajutabot.ui.components.DiscordTargetPicker
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
    backEnabled: Boolean = true,
    onGuildSelect: (String) -> Unit,
    onChannelSelect: (String) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme

    fun goToPage(page: Int) {
        scope.launch {
            pagerState.animateScrollToPage(
                page = page.coerceIn(0, pageCount - 1),
                animationSpec = motion.slowSpatialSpec(),
            )
        }
    }

    BackHandler(enabled = backEnabled) {
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
                    OnboardingFeaturePage(
                        page = onboardingPages[page],
                        isActive = pagerState.targetPage == page,
                    )
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

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                val showBack = pagerState.currentPage > 0
                val splitButtonWidth = ((maxWidth - 12.dp) / 2).coerceAtLeast(0.dp)
                val backWidth by animateDpAsState(
                    targetValue = if (showBack) splitButtonWidth else 0.dp,
                    animationSpec = motion.defaultSpatialSpec(),
                    label = "onboardingBackWidth",
                )
                val nextWidth by animateDpAsState(
                    targetValue = if (showBack) splitButtonWidth else maxWidth,
                    animationSpec = motion.defaultSpatialSpec(),
                    label = "onboardingNextWidth",
                )
                val buttonSpacing by animateDpAsState(
                    targetValue = if (showBack) 12.dp else 0.dp,
                    animationSpec = motion.defaultSpatialSpec(),
                    label = "onboardingButtonSpacing",
                )
                val backAlpha by animateFloatAsState(
                    targetValue = if (showBack) 1f else 0f,
                    animationSpec = motion.defaultEffectsSpec(),
                    label = "onboardingBackAlpha",
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (backWidth > 0.dp) {
                        Box(
                            modifier = Modifier
                                .width(backWidth)
                                .clipToBounds(),
                        ) {
                            OutlinedButton(
                                onClick = { goToPage((pagerState.currentPage - 1).coerceAtLeast(0)) },
                                enabled = showBack,
                                modifier = Modifier
                                    .requiredWidth(splitButtonWidth)
                                    .graphicsLayer { alpha = backAlpha },
                            ) {
                                Text("Wstecz")
                            }
                        }
                    }

                    Spacer(Modifier.width(buttonSpacing))

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
                        modifier = Modifier.width(nextWidth),
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
}

@Composable
private fun OnboardingFeaturePage(page: OnboardingPage, isActive: Boolean) {
    val heroScale by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.96f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "onboardingHeroScale",
    )
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
                .heightIn(min = 220.dp)
                .graphicsLayer {
                    scaleX = heroScale
                    scaleY = heroScale
                },
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
        DiscordTargetPicker(
            guilds = guilds,
            voiceChannels = voiceChannels,
            selectedGuildId = selectedGuildId,
            selectedChannelId = selectedChannelId,
            isLoadingVoiceChannels = isLoadingVoiceChannels,
            onGuildSelect = onGuildSelect,
            onChannelSelect = onChannelSelect,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
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
            val color by animateColorAsState(
                targetValue = if (index == currentPage) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "onboardingIndicatorColor",
            )
            Surface(
                modifier = Modifier
                    .height(8.dp)
                    .width(width),
                shape = RoundedCornerShape(999.dp),
                color = color,
            ) {}
        }
    }
}
