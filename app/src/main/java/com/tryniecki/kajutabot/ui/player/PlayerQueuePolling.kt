package com.tryniecki.kajutabot.ui.player

import java.time.Instant
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** One polling policy shared by the foreground UI and the active media service. */
suspend fun pollSelectedQueue(viewModel: PlayerViewModel) {
    viewModel.pollingKeys.collectLatest { keys ->
        if (keys.guildId == null) return@collectLatest
        coroutineScope {
            viewModel.pollQueueOnce()
            val remaining = viewModel.ui.value.queue?.let { snapshot ->
                val track = snapshot.nowPlaying ?: return@let null
                remainingMs(
                    snapshot.nowPlayingStartedAt,
                    track.durationMilliseconds,
                    Instant.now().toEpochMilli(),
                )
            }
            if (remaining != null) {
                launch {
                    delay(remaining.coerceAtLeast(0) + EXPECTED_END_GRACE_MS)
                    viewModel.pollQueueOnce()
                }
            }
            while (true) {
                delay(POLL_INTERVAL_MS)
                viewModel.pollQueueOnce()
            }
        }
    }
}
