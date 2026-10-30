package com.tryniecki.qumi.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tryniecki.qumi.MainActivity
import com.tryniecki.qumi.R
import com.tryniecki.qumi.ui.components.ActionTooltip
import com.tryniecki.qumi.ui.feedback.VolumeBoundaryFeedback
import com.tryniecki.qumi.ui.feedback.rememberFeedbackPerformer
import com.tryniecki.qumi.ui.components.ExpressiveLoadingIndicator
import kotlin.math.roundToInt

private const val DESKTOP_HELPER_URL = "https://kajuta.tryniecki.eu"

/** Touch detent only; keyboard/accessibility adjustments retain every integer percentage. */
internal fun snapLocalVolume(value: Int, dragging: Boolean): Int =
    if (dragging && value in 97..103) 100 else value.coerceIn(0, 200)

internal fun localVolumeStatus(state: LocalVolumeUiState): Int? = when {
    !state.isDiscordSession -> R.string.local_volume_guest
    !state.connected -> R.string.local_volume_disconnected
    state.unsupported -> R.string.local_volume_unsupported
    !state.fresh -> R.string.local_volume_syncing
    state.observed?.installed == false -> R.string.local_volume_install
    state.observed?.online == false -> R.string.local_volume_offline
    state.observed?.available == false -> R.string.local_volume_unavailable
    else -> null
}

private fun LocalVolumeError.messageResource(): Int = when (this) {
    LocalVolumeError.ACCESS_DENIED -> R.string.local_volume_guest
    LocalVolumeError.INVALID_VOLUME -> R.string.local_volume_invalid
    LocalVolumeError.OFFLINE -> R.string.local_volume_offline
    LocalVolumeError.UNAVAILABLE -> R.string.local_volume_unavailable
    LocalVolumeError.RPC_FAILED -> R.string.local_volume_rpc_failed
    LocalVolumeError.TIMEOUT -> R.string.local_volume_timeout
    LocalVolumeError.BUSY -> R.string.local_volume_busy
    LocalVolumeError.UNSUPPORTED -> R.string.local_volume_unsupported
    LocalVolumeError.FAILED -> R.string.local_volume_failed
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LocalVolumeSheet(
    state: LocalVolumeUiState,
    onDraftChange: (Int) -> Unit,
    onGestureFinished: () -> Unit,
    onGestureCancelled: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = stringResource(R.string.local_volume_title)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = Color.Transparent,
        modifier = Modifier.testTag("local-volume-sheet").semantics { paneTitle = title },
    ) {
        if (state.isDiscordSession) {
            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = state.isHelperOffline,
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                        .using(SizeTransform { _, _ -> motion.defaultSpatialSpec() })
                },
                label = "localVolumeHelper",
            ) { isHelperOffline ->
                if (isHelperOffline) {
                    LocalVolumeHelperNotice()
                } else {
                    LocalVolumeSheetContent(state, onDraftChange, onGestureFinished, onGestureCancelled)
                }
            }
        } else {
            LocalVolumeDemoNotice()
        }
    }
}

@Composable
private fun LocalVolumeDemoNotice() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.local_volume_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.local_volume_demo),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("local-volume-demo"),
        )
    }
}

/** Replaces the volume controls, which have nothing to show until the helper reports in. */
@Composable
private fun LocalVolumeHelperNotice() {
    val context = LocalContext.current
    val shareLabel = stringResource(R.string.local_volume_helper_share)
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, bottom = 24.dp)
            .testTag("local-volume-helper-notice"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.local_volume_helper_start),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.local_volume_helper_install),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ActionTooltip(shareLabel) {
            IconButton(onClick = { context.shareDesktopHelperLink(shareLabel) }) {
                Icon(
                    painter = painterResource(com.composables.icons.tabler.outline.R.drawable.tabler_ic_share_outline),
                    contentDescription = shareLabel,
                )
            }
        }
    }
}

private fun Context.shareDesktopHelperLink(title: String) {
    val link = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, DESKTOP_HELPER_URL)
    startActivity(
        Intent.createChooser(link, title).putExtra(
            // The app accepts shared links itself, and would try to queue this one.
            Intent.EXTRA_EXCLUDE_COMPONENTS,
            arrayOf(ComponentName(this, MainActivity::class.java)),
        ),
    )
}

@Composable
internal fun LocalVolumeSheetContent(
    state: LocalVolumeUiState,
    onDraftChange: (Int) -> Unit,
    onGestureFinished: () -> Unit,
    onGestureCancelled: () -> Unit,
) {
    val performFeedback = rememberFeedbackPerformer()
    val boundaryFeedback = remember(performFeedback) { VolumeBoundaryFeedback(performFeedback) }
    val sliderInteractions = remember { MutableInteractionSource() }
    val dragging by sliderInteractions.collectIsDraggedAsState()
    DisposableEffect(Unit) {
        onDispose {
            boundaryFeedback.end()
            onGestureCancelled()
        }
    }
    val title = stringResource(R.string.local_volume_title)
    val colorScheme = MaterialTheme.colorScheme
    val percentage = state.displayedVolume?.let {
        stringResource(R.string.local_volume_percent, it)
    } ?: stringResource(R.string.local_volume_unknown)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Null is never substituted with a synthetic observed value.
        Box(Modifier.fillMaxWidth().height(76.dp), contentAlignment = Alignment.TopCenter) {
            state.displayedVolume?.let { value ->
                val colors = SliderDefaults.colors()
                val slider = remember { SliderState(value.toFloat(), steps = 199, trackRange = 0f..200f) }
                SideEffect { slider.value = value.toFloat() }
                Slider(
                    state = slider,
                    onValueChange = {
                        val next = snapLocalVolume(it.roundToInt(), dragging)
                        slider.value = next.toFloat()
                        boundaryFeedback.change(value, next)
                        onDraftChange(next)
                    },
                    onValueChangeFinished = {
                        boundaryFeedback.end()
                        onGestureFinished()
                    },
                    enabled = state.enabled,
                    interactionSource = sliderInteractions,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("local-volume-slider")
                        .semantics {
                            contentDescription = title
                            stateDescription = percentage
                        },
                    track = {
                        val markerColor = if (state.enabled && value >= 100) colorScheme.onPrimary
                            else colorScheme.onSurfaceVariant
                        SliderDefaults.Track(it,
                            modifier = Modifier.height(32.dp).drawWithContent {
                                drawContent()
                                val x = size.width / 2f
                                drawLine(markerColor,
                                    Offset(x, center.y - 6.dp.toPx()), Offset(x, center.y + 6.dp.toPx()),
                                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            },
                            colors = colors, enabled = state.enabled,
                            drawTick = { _, _ -> }, drawStopIndicator = null)
                    },
                )
                Text(
                    stringResource(R.string.local_volume_percent, 100),
                    modifier = Modifier.align(Alignment.BottomCenter).testTag("local-volume-reference"),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
        // Permanent slots keep the percentage centered when the delayed indicator appears.
        Row(Modifier.height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(40.dp))
            Text(percentage, style = MaterialTheme.typography.displayMedium, color = colorScheme.onSurface,
                modifier = Modifier.testTag("local-volume-percentage"))
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (state.sending && state.showSendingIndicator) {
                    val sending = stringResource(R.string.local_volume_sending)
                    ExpressiveLoadingIndicator(
                        Modifier.size(40.dp).testTag("local-volume-sending")
                            .semantics { contentDescription = sending },
                        color = colorScheme.primary,
                    )
                }
            }
        }
        val message = state.error?.messageResource() ?: localVolumeStatus(state)
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.TopCenter) {
            message?.let { Text(stringResource(it),
                color = if (state.error != null) colorScheme.error else colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("local-volume-status")) }
        }
    }
}
