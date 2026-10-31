package com.tryniecki.qumi.data.repository

import com.tryniecki.qumi.api.model.jellyfin.JellyfinQuickConnectEvent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Carries hub signals to the Jellyfin screen, which has no connection of its own. */
class JellyfinLinkEvents {
    private val _quickConnectUpdates = MutableSharedFlow<JellyfinQuickConnectEvent>(
        extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val _hubConnected = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val quickConnectUpdates = _quickConnectUpdates.asSharedFlow()
    val hubConnected = _hubConnected.asSharedFlow()

    fun publish(update: JellyfinQuickConnectEvent) {
        _quickConnectUpdates.tryEmit(update)
    }

    fun publishHubConnected() {
        _hubConnected.tryEmit(Unit)
    }
}
