package com.tryniecki.qumi.ui.feedback

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserFeedbackTest {
    @Test fun `toggle events distinguish resulting on and off states`() = runTest {
        val feedback = UserFeedback()
        feedback.events.test {
            feedback.toggle(true)
            assertEquals(FeedbackEffect.ToggleOn, awaitItem())
            feedback.toggle(false)
            assertEquals(FeedbackEffect.ToggleOff, awaitItem())
            expectNoEvents()
        }
    }

    @Test fun `events have no replay after background or recreation`() = runTest {
        val feedback = UserFeedback()
        feedback.emit(FeedbackEffect.Confirm)
        feedback.events.test {
            expectNoEvents()
            feedback.emit(FeedbackEffect.Reject)
            assertEquals(FeedbackEffect.Reject, awaitItem())
        }
        feedback.events.test { expectNoEvents() }
    }

    @Test fun `semantic effects map to native Compose types`() {
        val expected = mapOf(
            FeedbackEffect.Confirm to HapticFeedbackType.Confirm,
            FeedbackEffect.Reject to HapticFeedbackType.Reject,
            FeedbackEffect.ToggleOn to HapticFeedbackType.ToggleOn,
            FeedbackEffect.ToggleOff to HapticFeedbackType.ToggleOff,
            FeedbackEffect.Selection to HapticFeedbackType.SegmentTick,
            FeedbackEffect.DragTick to HapticFeedbackType.SegmentTick,
            FeedbackEffect.DragStart to HapticFeedbackType.LongPress,
            FeedbackEffect.SwipeThreshold to HapticFeedbackType.GestureThresholdActivate,
            FeedbackEffect.GestureEnd to HapticFeedbackType.GestureEnd,
        )
        assertEquals(FeedbackEffect.entries.toSet(), expected.keys)
        expected.forEach { (effect, type) -> assertEquals(type, effect.composeType()) }
    }

    @Test fun `drag picks up once and ticks only on distinct valid positions`() {
        val effects = mutableListOf<FeedbackEffect>()
        val drag = ReorderFeedback(effects::add)
        drag.start(-1)
        drag.target(0)
        drag.start(0)
        drag.start(0)
        drag.target(0)
        drag.target(1)
        drag.target(1)
        drag.target(-1)
        drag.target(2)
        drag.target(1)
        assertEquals(listOf(FeedbackEffect.DragStart, FeedbackEffect.DragTick,
            FeedbackEffect.DragTick, FeedbackEffect.DragTick), effects)
        drag.end()
        drag.target(2)
        drag.end()
        assertEquals(4, effects.size) // Cancellation/unchanged release cannot confirm.
        drag.start(1)
        assertEquals(FeedbackEffect.DragStart, effects.last())
    }

    @Test fun `volume ticks at boundaries rather than every percentage or repeated value`() {
        val effects = mutableListOf<FeedbackEffect>()
        val volume = VolumeBoundaryFeedback(effects::add)
        for (next in 51..99) volume.change(50, next)
        assertEquals(emptyList<FeedbackEffect>(), effects)
        volume.change(99, 101)
        volume.change(101, 101)
        volume.change(101, 150)
        volume.change(150, 200)
        volume.change(200, 200)
        volume.end()
        volume.change(200, 0)
        assertEquals(List(3) { FeedbackEffect.Selection }, effects)
        volume.end()
        assertEquals(3, effects.size)
    }
}
