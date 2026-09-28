package com.tryniecki.kajutabot.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.flow.first

private const val SUMMARY_SEPARATOR = " · "

private data class OdometerGlyph(
    val id: String,
    val character: Char,
    val orderGroup: Int,
    val placeFromRight: Int,
    val isDigit: Boolean,
)

private data class PresentedGlyph(
    val glyph: OdometerGlyph,
    val visible: Boolean,
    val isNew: Boolean,
    val forward: Boolean,
)

private class ValueHistory(
    var value: String,
)

/**
 * Numeric runs are aligned from the right so carries and changes in digit count
 * keep existing odometer slots stable.
 */
private fun odometerGlyphs(value: String): List<OdometerGlyph> {
    val chunks = Regex("""\d+|\D+""")
        .findAll(value)
        .map { it.value }
        .toList()

    var digitGroup = 0
    var literalGroup = 0

    return chunks
        .asReversed()
        .flatMap { chunk ->
            val isDigit = chunk.first().isDigit()
            val group = if (isDigit) digitGroup++ else literalGroup++

            chunk
                .reversed()
                .mapIndexed { place, character ->
                    OdometerGlyph(
                        id = "${if (isDigit) 'd' else 'l'}:$group:$place",
                        character = character,
                        orderGroup = if (isDigit) {
                            2 * group + 1
                        } else {
                            2 * group + 2
                        },
                        placeFromRight = place,
                        isDigit = isDigit,
                    )
                }
        }
        .sortedWith(
            compareByDescending<OdometerGlyph> { it.orderGroup }
                .thenByDescending { it.placeFromRight },
        )
}

private fun rollsForward(
    previous: String,
    current: String,
): Boolean {
    val before = previous
        .filter(Char::isDigit)
        .trimStart('0')
        .ifEmpty { "0" }

    val after = current
        .filter(Char::isDigit)
        .trimStart('0')
        .ifEmpty { "0" }

    return after.length > before.length ||
            (after.length == before.length && after >= before)
}

@Composable
internal fun QueueSummaryOdometer(
    summary: String?,
) {
    val motion = MaterialTheme.motionScheme

    var lastSummary by remember {
        mutableStateOf(summary)
    }

    SideEffect {
        if (summary != null) {
            lastSummary = summary
        }
    }

    AnimatedVisibility(
        visible = summary != null,
        enter = fadeIn(
            animationSpec = motion.fastEffectsSpec(),
        ) + expandVertically(
            animationSpec = motion.defaultSpatialSpec(),
        ),
        exit = fadeOut(
            animationSpec = motion.fastEffectsSpec(),
        ) + shrinkVertically(
            animationSpec = motion.defaultSpatialSpec(),
        ),
        label = "queueSummaryVisibility",
    ) {
        val displayed = summary
            ?: lastSummary
            ?: return@AnimatedVisibility

        val separatorAt = displayed.indexOf(SUMMARY_SEPARATOR)

        if (separatorAt < 0) {
            return@AnimatedVisibility
        }

        Row(
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = displayed
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdometerValue(
                value = displayed.substring(
                    startIndex = 0,
                    endIndex = separatorAt,
                ),
            )

            Text(
                text = SUMMARY_SEPARATOR,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OdometerValue(
                value = displayed.substring(
                    startIndex = separatorAt + SUMMARY_SEPARATOR.length,
                ),
            )
        }
    }
}

@Composable
private fun OdometerValue(
    value: String,
) {
    val motion = MaterialTheme.motionScheme

    val targetGlyphs = remember(value) {
        odometerGlyphs(value)
    }

    var presented by remember {
        mutableStateOf(
            targetGlyphs.map {
                PresentedGlyph(
                    glyph = it,
                    visible = true,
                    isNew = false,
                    forward = true,
                )
            },
        )
    }

    val history = remember {
        ValueHistory(value)
    }

    val forward = rollsForward(
        previous = history.value,
        current = value,
    )

    SideEffect {
        history.value = value
    }

    LaunchedEffect(targetGlyphs) {
        val old = presented.associateBy {
            it.glyph.id
        }

        val target = targetGlyphs.associateBy {
            it.id
        }

        presented = (old.keys + target.keys)
            .mapNotNull { id ->
                val next = target[id]

                when {
                    next != null -> {
                        PresentedGlyph(
                            glyph = next,
                            visible = true,
                            isNew = id !in old,
                            forward = forward,
                        )
                    }

                    else -> {
                        old[id]?.copy(
                            visible = false,
                            forward = forward,
                        )
                    }
                }
            }
            .sortedWith(
                compareByDescending<PresentedGlyph> {
                    it.glyph.orderGroup
                }.thenByDescending {
                    it.glyph.placeFromRight
                },
            )
    }

    Row(
        modifier = Modifier.animateContentSize(
            animationSpec = motion.defaultSpatialSpec(),
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presented.forEach { slot ->
            key(slot.glyph.id) {
                OdometerGlyphContent(
                    slot = slot,
                    onExitFinished = {
                        presented = presented.filterNot {
                            it.glyph.id == slot.glyph.id &&
                                    !it.visible
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun OdometerGlyphContent(
    slot: PresentedGlyph,
    onExitFinished: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme

    val visibility = remember(slot.glyph.id) {
        MutableTransitionState(!slot.isNew)
    }

    LaunchedEffect(slot.visible) {
        visibility.targetState = slot.visible
    }

    LaunchedEffect(slot.visible) {
        if (!slot.visible) {
            snapshotFlow {
                visibility.isIdle &&
                        !visibility.currentState
            }.first { it }

            onExitFinished()
        }
    }

    AnimatedVisibility(
        visibleState = visibility,
        enter = expandHorizontally(
            animationSpec = motion.defaultSpatialSpec(),
        ) + fadeIn(
            animationSpec = motion.fastEffectsSpec(),
        ) + slideInVertically(
            animationSpec = motion.fastSpatialSpec(),
            initialOffsetY = {
                if (slot.forward) it else -it
            },
        ),
        exit = shrinkHorizontally(
            animationSpec = motion.defaultSpatialSpec(),
        ) + fadeOut(
            animationSpec = motion.fastEffectsSpec(),
        ) + slideOutVertically(
            animationSpec = motion.fastSpatialSpec(),
            targetOffsetY = {
                if (slot.forward) -it else it
            },
        ),
        label = "odometerGlyphVisibility",
    ) {
        if (slot.glyph.isDigit) {
            OdometerDigit(slot)
        } else {
            Text(
                text = slot.glyph.character.toString(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OdometerDigit(
    slot: PresentedGlyph,
) {
    val motion = MaterialTheme.motionScheme

    val normalColor = MaterialTheme.colorScheme.onSurfaceVariant
    val highlightColor = MaterialTheme.colorScheme.primary

    val flash = remember(slot.glyph.id) {
        Animatable(0f)
    }

    var previousCharacter by remember(slot.glyph.id) {
        mutableStateOf(slot.glyph.character)
    }

    LaunchedEffect(slot.glyph.character) {
        val changed =
            slot.isNew ||
                    previousCharacter != slot.glyph.character

        previousCharacter = slot.glyph.character

        if (changed) {
            /*
             * Deliberately don't snap back to 0 here.
             *
             * If another value arrives while the previous flash is fading,
             * Animatable continues from its current value instead of producing
             * a visible normalColor -> primary discontinuity.
             */
            flash.animateTo(
                targetValue = 1f,
                animationSpec = motion.fastEffectsSpec(),
            )

            flash.animateTo(
                targetValue = 0f,
                animationSpec = motion.defaultEffectsSpec(),
            )
        }
    }

    AnimatedContent(
        targetState = slot.glyph.character,
        transitionSpec = {
            val enter = fadeIn(
                animationSpec = motion.fastEffectsSpec(),
            ) + slideInVertically(
                animationSpec = motion.fastSpatialSpec(),
                initialOffsetY = {
                    if (slot.forward) it else -it
                },
            )

            val exit = fadeOut(
                animationSpec = motion.fastEffectsSpec(),
            ) + slideOutVertically(
                animationSpec = motion.fastSpatialSpec(),
                targetOffsetY = {
                    if (slot.forward) -it else it
                },
            )

            (enter togetherWith exit).using(
                SizeTransform(
                    clip = false,
                ),
            )
        },
        label = "odometerDigit",
    ) { character ->
        Text(
            text = character.toString(),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFeatureSettings = "tnum",
            ),
            color = if (character == slot.glyph.character) {
                lerp(
                    start = normalColor,
                    stop = highlightColor,
                    fraction = flash.value,
                )
            } else {
                normalColor
            },
        )
    }
}