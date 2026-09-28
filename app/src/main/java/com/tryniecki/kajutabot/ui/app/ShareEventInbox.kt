package com.tryniecki.kajutabot.ui.app

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

data class SharedUrlEvent(val id: Long, val url: String)

/** Activity intent delivery is a one-shot FIFO, including repeated identical URLs. */
class ShareEventInbox {
    private val channel = Channel<SharedUrlEvent>(Channel.UNLIMITED)
    val events = channel.receiveAsFlow()
    private var nextId = 0L

    @Synchronized fun offer(url: String?) {
        if (!url.isNullOrBlank()) channel.trySend(SharedUrlEvent(++nextId, url))
    }
}
