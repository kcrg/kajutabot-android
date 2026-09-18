package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.radio.EnableRadioRequest

/**
 * Pure radio-toggle decision. Keeps queue-version handling testable without Android.
 *
 * Enabling requires the selected voice channel and carries the current queue version
 * plus the backend's min/max (defaulting to 60/600 s when absent). Disabling only
 * needs the current queue version.
 */
sealed interface RadioToggleAction {
    data class Enable(val request: EnableRadioRequest) : RadioToggleAction
    data class Disable(val expectedVersion: Long?) : RadioToggleAction
    data object MissingVoiceChannel : RadioToggleAction
}

object RadioToggleDefaults {
    const val MIN_DURATION_SECONDS = 60
    const val MAX_DURATION_SECONDS = 600
}

fun decideRadioToggle(
    queue: QueueSnapshotResponse,
    voiceChannelId: String?,
): RadioToggleAction {
    if (queue.radio.isEnabled) {
        return RadioToggleAction.Disable(queue.version)
    }
    val channelId = voiceChannelId ?: return RadioToggleAction.MissingVoiceChannel
    return RadioToggleAction.Enable(
        EnableRadioRequest(
            voiceChannelId = channelId,
            minimumDurationSeconds = queue.radio.minimumDurationSeconds
                ?: RadioToggleDefaults.MIN_DURATION_SECONDS,
            maximumDurationSeconds = queue.radio.maximumDurationSeconds
                ?: RadioToggleDefaults.MAX_DURATION_SECONDS,
            expectedQueueVersion = queue.version,
        ),
    )
}
