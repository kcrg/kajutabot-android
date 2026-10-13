package com.tryniecki.kajutabot.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.ui.components.ExpressiveLoadingIndicator
import kotlin.math.roundToInt

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
        LocalVolumeSheetContent(state, onDraftChange, onGestureFinished, onGestureCancelled)
    }
}

@Composable
internal fun LocalVolumeSheetContent(
    state: LocalVolumeUiState,
    onDraftChange: (Int) -> Unit,
    onGestureFinished: () -> Unit,
    onGestureCancelled: () -> Unit,
) {
    DisposableEffect(Unit) { onDispose { onGestureCancelled() } }
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
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            state.displayedVolume?.let { value ->
                val colors = SliderDefaults.colors()
                val slider = remember { SliderState(value.toFloat(), steps = 199, trackRange = 0f..200f) }
                SideEffect { slider.value = value.toFloat() }
                Slider(
                    state = slider,
                    onValueChange = { onDraftChange(it.roundToInt().coerceIn(0, 200)) },
                    onValueChangeFinished = onGestureFinished,
                    enabled = state.enabled,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth().testTag("local-volume-slider")
                        .semantics {
                            contentDescription = title
                            stateDescription = percentage
                        },
                    track = { SliderDefaults.Track(it, modifier = Modifier.height(32.dp), colors = colors,
                        enabled = state.enabled, drawTick = { _, _ -> }, drawStopIndicator = null) },
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
