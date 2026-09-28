package com.tryniecki.kajutabot

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tryniecki.kajutabot.ui.components.SwipeActionCard
import com.tryniecki.kajutabot.ui.components.SwipeActionStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwipeActionCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun addFeedbackShowsPendingThenConfirmedSuccess() {
        var status by mutableStateOf(SwipeActionStatus.IDLE)
        compose.setContent {
            MaterialTheme {
                SwipeActionCard(
                    addLabel = "Add", removeLabel = "Remove", enabled = true,
                    addStatus = status, onAdd = { status = SwipeActionStatus.PENDING },
                    onRemove = {},
                ) { modifier -> Box(modifier.fillMaxWidth().height(80.dp)) { Text("Track") } }
            }
        }
        compose.onNodeWithTag("swipeActionCard").performTouchInput { swipeRight() }
        compose.onNodeWithTag("swipeActionCard").assert(hasStateDescription(compose.activity.getString(R.string.swipe_add_pending)))
        compose.runOnIdle { status = SwipeActionStatus.SUCCESS }
        compose.onNodeWithTag("swipeActionCard").assert(hasStateDescription(compose.activity.getString(R.string.swipe_add_success)))
    }

    @Test fun addFeedbackShowsFailure() {
        var status by mutableStateOf(SwipeActionStatus.IDLE)
        compose.setContent {
            MaterialTheme {
                SwipeActionCard(
                    addLabel = "Add", removeLabel = "Remove", enabled = true,
                    addStatus = status, onAdd = { status = SwipeActionStatus.PENDING },
                    onRemove = {},
                ) { modifier -> Box(modifier.fillMaxWidth().height(80.dp)) { Text("Track") } }
            }
        }
        compose.onNodeWithTag("swipeActionCard").performTouchInput { swipeRight() }
        compose.runOnIdle { status = SwipeActionStatus.FAILURE }
        compose.onNodeWithTag("swipeActionCard").assert(hasStateDescription(compose.activity.getString(R.string.swipe_add_failure)))
    }
}
