package com.tryniecki.qumi.ui.player

import com.tryniecki.qumi.api.model.queue.QueueSnapshotResponse
import com.tryniecki.qumi.api.model.radio.EnableRadioRequest

/**
 * Pure radio-toggle decision. Keeps queue-version handling testable without Android.
 *
 * Enabling requires the selected voice channel and carries the current queue version
 * plus the backend's min/max (defaulting to 60/600 s when absent). Disabling only
 * needs the current queue version.
 */
sealed interface RadioToggleAction {
    data class Enable(val request: EnableRadioRequest) : RadioToggleAction
    data class Disable(val expectedQueueVersion: Long) : RadioToggleAction
    data object MissingVoiceChannel : RadioToggleAction
}

object RadioToggleDefaults {
    const val MIN_DURATION_SECONDS = 60
    const val MAX_DURATION_SECONDS = 600
}

fun decideRadioToggle(
    queue: QueueSnapshotResponse,
    voiceChannelId: String?,
    favoritesSelected: Boolean = false,
): RadioToggleAction {
    if (queue.radio.isEnabled) {
        return RadioToggleAction.Disable(queue.queueVersion)
    }
    return radioEnableAction(queue, voiceChannelId, favoritesSelected)
}

fun decideRadioSourceToggle(
    queue: QueueSnapshotResponse,
    voiceChannelId: String?,
): RadioToggleAction = radioEnableAction(
    queue,
    queue.voiceChannelId ?: voiceChannelId,
    favoritesSelected = queue.radio.mode != "favorites",
)

private fun radioEnableAction(
    queue: QueueSnapshotResponse,
    voiceChannelId: String?,
    favoritesSelected: Boolean,
): RadioToggleAction {
    val channelId = voiceChannelId ?: return RadioToggleAction.MissingVoiceChannel
    return RadioToggleAction.Enable(
        EnableRadioRequest(
            voiceChannelId = channelId,
            minimumDurationSeconds = queue.radio.minimumDurationSeconds
                ?: RadioToggleDefaults.MIN_DURATION_SECONDS,
            maximumDurationSeconds = queue.radio.maximumDurationSeconds
                ?: RadioToggleDefaults.MAX_DURATION_SECONDS,
            expectedQueueVersion = queue.queueVersion,
            mode = if (favoritesSelected) "favorites" else "cache",
        ),
    )
}
