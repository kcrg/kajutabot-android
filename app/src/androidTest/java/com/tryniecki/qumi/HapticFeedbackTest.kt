package com.tryniecki.qumi

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tryniecki.qumi.ui.components.SwipeActionCard
import com.tryniecki.qumi.ui.components.SwipeActionStatus
import com.tryniecki.qumi.ui.feedback.FeedbackEffect
import com.tryniecki.qumi.ui.feedback.ObserveUserFeedback
import com.tryniecki.qumi.ui.feedback.UserFeedback
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HapticFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class FakeHaptics : HapticFeedback {
        val effects = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            effects += hapticFeedbackType
        }
    }

    @Test fun recompositionRestorationAndBackgroundResultsStaySilent() {
        val feedback = UserFeedback()
        val haptics = FakeHaptics()
        var externalState by mutableStateOf("initial")
        var observing by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                if (observing) ObserveUserFeedback(feedback.events)
                Text(externalState)
            }
        }
        compose.runOnIdle { externalState = "remote update" }
        compose.runOnIdle { assertEquals(emptyList<HapticFeedbackType>(), haptics.effects) }
        compose.runOnIdle { feedback.emit(FeedbackEffect.Confirm) }
        compose.runOnIdle { assertEquals(listOf(HapticFeedbackType.Confirm), haptics.effects) }
        compose.runOnIdle { observing = false }
        compose.runOnIdle { feedback.emit(FeedbackEffect.Reject) }
        compose.runOnIdle { observing = true }
        compose.waitForIdle()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.onActivity { feedback.emit(FeedbackEffect.Reject) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle { assertEquals(listOf(HapticFeedbackType.Confirm), haptics.effects) }
    }

    @Test fun nestedSwipeAndParentEmitOnlyOneCompletion() {
        val feedback = UserFeedback()
        val haptics = FakeHaptics()
        var status by mutableStateOf(SwipeActionStatus.IDLE)
        compose.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                MaterialTheme {
                    ObserveUserFeedback(feedback.events)
                    SwipeActionCard(
                        addLabel = "Add", enabled = true, addStatus = status,
                        onAdd = {
                            status = SwipeActionStatus.SUCCESS
                            feedback.emit(FeedbackEffect.Confirm)
                        },
                    ) { modifier ->
                        Box(modifier.fillMaxWidth().height(100.dp))
                    }
                }
            }
        }
        compose.onNodeWithTag("swipeActionCard").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, haptics.effects.count { it == HapticFeedbackType.GestureThresholdActivate })
            assertEquals(1, haptics.effects.count { it == HapticFeedbackType.Confirm })
        }
    }
}
