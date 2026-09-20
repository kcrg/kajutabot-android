package com.tryniecki.kajutabot.ui.myaudio

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

private object MyAudioEntranceMemory {
    var hasPlayedConstructionEntrance: Boolean = false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyAudioScreen() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val shouldAnimateEntrance = remember {
        if (MyAudioEntranceMemory.hasPlayedConstructionEntrance) {
            false
        } else {
            MyAudioEntranceMemory.hasPlayedConstructionEntrance = true
            true
        }
    }

    var firstTapeVisible by remember { mutableStateOf(!shouldAnimateEntrance) }
    var secondTapeVisible by remember { mutableStateOf(!shouldAnimateEntrance) }
    var thirdTapeVisible by remember { mutableStateOf(!shouldAnimateEntrance) }
    var placardVisible by remember { mutableStateOf(!shouldAnimateEntrance) }

    LaunchedEffect(shouldAnimateEntrance) {
        if (!shouldAnimateEntrance) return@LaunchedEffect

        firstTapeVisible = true
        delay(90)
        secondTapeVisible = true
        delay(90)
        thirdTapeVisible = true
        delay(120)
        placardVisible = true
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ConstructionPlaceholder(
                selectedTab = selectedTab,
                firstTapeVisible = firstTapeVisible,
                secondTapeVisible = secondTapeVisible,
                thirdTapeVisible = thirdTapeVisible,
                placardVisible = placardVisible,
                modifier = Modifier.fillMaxSize(),
            )

            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(3f),
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Przesłane") },
                    icon = {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_upload_outline),
                            contentDescription = null,
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Jellyfin") },
                    icon = {
                        Icon(
                            painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_server_outline),
                            contentDescription = null,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ConstructionPlaceholder(
    selectedTab: Int,
    firstTapeVisible: Boolean,
    secondTapeVisible: Boolean,
    thirdTapeVisible: Boolean,
    placardVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme

    BoxWithConstraints(modifier = modifier) {
        AnimatedVisibility(
            visible = firstTapeVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = maxHeight * 0.13f)
                .zIndex(1f),
            enter = fadeIn(motion.defaultEffectsSpec()) +
                slideIn(motion.slowSpatialSpec()) { fullSize ->
                    IntOffset(-fullSize.width * 2, -fullSize.height * 5)
                },
        ) {
            ConstructionTape(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationZ = 19f
                        scaleX = 1.55f
                    },
            )
        }

        AnimatedVisibility(
            visible = secondTapeVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = maxHeight * 0.38f)
                .zIndex(1f),
            enter = fadeIn(motion.defaultEffectsSpec()) +
                slideIn(motion.slowSpatialSpec()) { fullSize ->
                    IntOffset(fullSize.width * 2, -fullSize.height * 3)
                },
        ) {
            ConstructionTape(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationZ = -21f
                        scaleX = 1.6f
                    },
            )
        }

        AnimatedVisibility(
            visible = thirdTapeVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = maxHeight * 0.73f)
                .zIndex(1f),
            enter = fadeIn(motion.defaultEffectsSpec()) +
                slideIn(motion.slowSpatialSpec()) { fullSize ->
                    IntOffset(-fullSize.width * 2, fullSize.height * 5)
                },
        ) {
            ConstructionTape(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        rotationZ = 17f
                        scaleX = 1.55f
                    },
            )
        }

        AnimatedVisibility(
            visible = placardVisible,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = maxHeight * 0.12f)
                .zIndex(2f),
            enter = fadeIn(motion.slowEffectsSpec()) +
                scaleIn(
                    initialScale = 0.96f,
                    animationSpec = motion.slowSpatialSpec(),
                ),
        ) {
            WorkInProgressPlacard(selectedTab = selectedTab)
        }
    }
}

@Composable
private fun WorkInProgressPlacard(
    selectedTab: Int,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 34.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(
                    if (selectedTab == 0) {
                        com.composables.icons.tabler.outline.R.drawable.tabler_ic_upload_outline
                    } else {
                        com.composables.icons.tabler.outline.R.drawable.tabler_ic_server_outline
                    },
                ),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Work in progress",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = motion.defaultEffectsSpec()) togetherWith
                        fadeOut(animationSpec = motion.fastEffectsSpec())
                },
                label = "myAudioWorkInProgressDescription",
            ) { tab ->
                Text(
                    text = "Wolałem skupić się na core feature'ach, więc zostawiam to na później.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ConstructionTape(
    modifier: Modifier = Modifier,
) {
    val yellow = Color(0xFFFFC928)
    val black = Color(0xFF181818)

    Canvas(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        drawRect(yellow)

        val stripeWidth = size.height * 1.45f
        val slant = size.height * 0.72f
        var x = -stripeWidth

        while (x < size.width + stripeWidth) {
            val path = Path().apply {
                moveTo(x, 0f)
                lineTo(x + stripeWidth, 0f)
                lineTo(x + stripeWidth - slant, size.height)
                lineTo(x - slant, size.height)
                close()
            }
            drawPath(path, black)
            x += stripeWidth * 2f
        }
    }
}
