package com.tryniecki.kajutabot.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tryniecki.kajutabot.api.model.auth.SessionType
import com.tryniecki.kajutabot.api.model.discord.DiscordGuildResponse
import com.tryniecki.kajutabot.api.model.discord.DiscordVoiceChannelResponse
import com.tryniecki.kajutabot.ui.components.DiscordTargetPicker
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val description: String,
    val visual: OnboardingVisual,
)

private enum class OnboardingVisual {
    PLAYER,
    QUEUE,
    SHARE,
    FAVORITES,
    RADIO,
    MINIPLAYER,
    SYSTEM_MEDIA,
}

private val onboardingPages = listOf(
    OnboardingPage(
        title = "Steruj tym, co gra na Discordzie",
        description = "Telefon działa jak pilot do KajutaBota na wybranym kanale głosowym. Widzisz aktualny utwór i postęp, możesz pominąć lub zatrzymać odtwarzanie, włączyć powtarzanie i zapisać utwór do ulubionych.",
        visual = OnboardingVisual.PLAYER,
    ),
    OnboardingPage(
        title = "Kolejka pod pełną kontrolą",
        description = "Przytrzymaj i przeciągnij utwór, aby zmienić jego pozycję. Możesz usuwać pojedyncze pozycje, wyczyścić całą kolejkę albo szybko otworzyć wyszukiwanie z poziomu odtwarzacza.",
        visual = OnboardingVisual.QUEUE,
    ),
    OnboardingPage(
        title = "Dodawaj muzykę na swój sposób",
        description = "Wpisz nazwę utworu, wklej bezpośredni link albo wybierz KajutaBot w systemowym menu Udostępnij, np. w YouTube. Link trafia od razu do ekranu dodawania i nie musisz kopiować go ręcznie między aplikacjami.",
        visual = OnboardingVisual.SHARE,
    ),
    OnboardingPage(
        title = "Ulubione zawsze pod ręką",
        description = "Zapisuj utwory na później i dodawaj je ponownie jednym stuknięciem. Możesz też wrzucić wszystkie ulubione do kolejki naraz i opcjonalnie wymieszać ich kolejność.",
        visual = OnboardingVisual.FAVORITES,
    ),
    OnboardingPage(
        title = "Radio, gdy skończy się kolejka",
        description = "Włącz Radio przyciskiem z ikoną radia w odtwarzaczu. Gdy zwykła kolejka się opróżni, KajutaBot automatycznie dobiera losowy utwór z cache, dzięki czemu muzyka może grać dalej bez ręcznego dokładania kolejnych pozycji.",
        visual = OnboardingVisual.RADIO,
    ),
    OnboardingPage(
        title = "Sterowanie zostaje z Tobą",
        description = "Gdy coś gra, miniplayer pozostaje nad dolną nawigacją na pozostałych zakładkach. Pokazuje postęp i pozwala szybko dodać utwór do ulubionych, pominąć go albo wrócić do pełnego odtwarzacza.",
        visual = OnboardingVisual.MINIPLAYER,
    ),
    OnboardingPage(
        title = "Steruj też z poziomu Androida",
        description = "Podczas odtwarzania Android pokazuje systemową kartę multimediów. Możesz podejrzeć aktualny utwór i używać szybkich akcji bez wracania do aplikacji.",
        visual = OnboardingVisual.SYSTEM_MEDIA,
    ),
)

private val guestIntroPage = onboardingPages[0].copy(
    description = "Telefon jest pilotem do KajutaBota na serwerze demonstracyjnym Discord. Podejrzysz aktualny utwór i postęp oraz skorzystasz z tych samych podstawowych kontrolek bez wpisywania komend.",
)

@Composable
fun OnboardingScreen(
    guilds: List<DiscordGuildResponse>,
    voiceChannels: List<DiscordVoiceChannelResponse>,
    selectedGuildId: String?,
    selectedChannelId: String?,
    isLoadingVoiceChannels: Boolean,
    canDismiss: Boolean,
    backEnabled: Boolean = true,
    sessionType: SessionType = SessionType.DISCORD,
    onGuildSelect: (String) -> Unit,
    onChannelSelect: (String) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectionPageIndex = onboardingPages.size
    val pageCount = selectionPageIndex + 1
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
                        page = when {
                            sessionType == SessionType.GUEST && page == 0 -> guestIntroPage
                            else -> onboardingPages[page]
                        },
                        isActive = pagerState.targetPage == page,
                    )
                } else {
                    OnboardingSelectionPage(
                        guilds = guilds,
                        voiceChannels = voiceChannels,
                        selectedGuildId = selectedGuildId,
                        selectedChannelId = selectedChannelId,
                        isLoadingVoiceChannels = isLoadingVoiceChannels,
                        sessionType = sessionType,
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            FeatureVisual(
                visual = page.visual,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .graphicsLayer {
                        scaleX = heroScale
                        scaleY = heroScale
                    },
            )
        }

        Spacer(Modifier.height(16.dp))

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
    sessionType: SessionType,
    onGuildSelect: (String) -> Unit,
    onChannelSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        DiscordSelectionHero(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = "Gdzie chcesz sterować botem?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (sessionType == SessionType.GUEST) "Serwer demonstracyjny jest już wybrany. Wybierz kanał głosowy; możesz go później zmienić z ekranu odtwarzacza."
                else "Wybierz serwer Discord, a potem kanał głosowy. Ten wybór możesz później zmienić z ekranu odtwarzacza.",
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
            showGuildPicker = sessionType != SessionType.GUEST,
            onGuildSelect = onGuildSelect,
            onChannelSelect = onChannelSelect,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun FeatureVisual(
    visual: OnboardingVisual,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val drawable = when (visual) {
            OnboardingVisual.PLAYER -> com.tryniecki.kajutabot.R.drawable.onboarding_player
            OnboardingVisual.QUEUE -> com.tryniecki.kajutabot.R.drawable.onboarding_queue
            OnboardingVisual.SHARE -> com.tryniecki.kajutabot.R.drawable.onboarding_share
            OnboardingVisual.FAVORITES -> com.tryniecki.kajutabot.R.drawable.onboarding_favorites
            OnboardingVisual.RADIO -> com.tryniecki.kajutabot.R.drawable.onboarding_radio
            OnboardingVisual.MINIPLAYER -> com.tryniecki.kajutabot.R.drawable.onboarding_miniplayer
            OnboardingVisual.SYSTEM_MEDIA -> com.tryniecki.kajutabot.R.drawable.onboarding_system_media
        }
        ScreenshotHero(
            drawable = drawable,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ScreenshotHero(
    @DrawableRes drawable: Int,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        alignment = Alignment.Center,
    )
}

@Composable
private fun DiscordSelectionHero(modifier: Modifier = Modifier) {
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
                .padding(16.dp),
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
                        painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_brand_discord_outline),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(14.dp)
                            .size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = "Połączenie z Discordem",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
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
