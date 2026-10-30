package com.tryniecki.qumi.ui.feedback

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidHapticsTest {
    private class UnsupportedHapticView : View(RuntimeEnvironment.getApplication()) {
        val calls = mutableListOf<Int>()
        override fun performHapticFeedback(feedbackConstant: Int): Boolean {
            calls += feedbackConstant
            return false
        }
    }
    private class FakeHaptics : HapticFeedback {
        val calls = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { calls += hapticFeedbackType }
    }

    @Test fun `API 34 uses one native drag start even when device declines the effect`() {
        val view = UnsupportedHapticView()
        val haptics = FakeHaptics()
        performNativeFeedback(FeedbackEffect.DragStart, haptics, view)
        assertEquals(listOf(HapticFeedbackConstants.DRAG_START), view.calls)
        assertEquals(emptyList<HapticFeedbackType>(), haptics.calls)
    }

    @Test @Config(sdk = [29])
    fun `API 29 uses Compose long press fallback for pickup`() {
        val view = UnsupportedHapticView()
        val haptics = FakeHaptics()
        performNativeFeedback(FeedbackEffect.DragStart, haptics, view)
        assertEquals(emptyList<Int>(), view.calls)
        assertEquals(listOf(HapticFeedbackType.LongPress), haptics.calls)
    }
}
