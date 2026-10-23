package com.tryniecki.kajutabot.ui.feedback

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Presentation events, never inferred from restored state or remote snapshots. */
enum class FeedbackEffect {
    Confirm, Reject, ToggleOn, ToggleOff, Selection, DragStart, DragTick, SwipeThreshold, GestureEnd,
}

class UserFeedback {
    private val mutableEvents = MutableSharedFlow<FeedbackEffect>(
        extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = mutableEvents.asSharedFlow()
    fun emit(effect: FeedbackEffect) { mutableEvents.tryEmit(effect) }
    fun toggle(enabled: Boolean) = emit(if (enabled) FeedbackEffect.ToggleOn else FeedbackEffect.ToggleOff)
}

/** Pure gesture bookkeeping: no timers, jobs or allocations per pointer movement. */
internal class ReorderFeedback(private val perform: (FeedbackEffect) -> Unit) {
    private var position = -1
    fun start(index: Int) {
        if (position >= 0 || index < 0) return
        position = index
        perform(FeedbackEffect.DragStart)
    }
    fun target(index: Int) {
        if (position < 0 || index < 0 || index == position) return
        position = index
        perform(FeedbackEffect.DragTick)
    }
    fun end() { position = -1 }
}

internal class VolumeBoundaryFeedback(private val perform: (FeedbackEffect) -> Unit) {
    private var previous: Int? = null
    fun change(current: Int, next: Int) {
        val from = previous ?: current
        previous = next
        if (from == next) return
        if (next == 0 || next == 200 || (from < 100 && next >= 100) || (from > 100 && next <= 100)) {
            perform(FeedbackEffect.Selection)
        }
    }
    fun end() { previous = null }
}
