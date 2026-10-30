package com.tryniecki.qumi

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tryniecki.qumi.api.model.playback.LocalVolumeStateDto
import com.tryniecki.qumi.ui.player.LocalVolumeSheet
import com.tryniecki.qumi.ui.player.LocalVolumeSheetContent
import com.tryniecki.qumi.ui.player.LocalVolumeUiState
import com.tryniecki.qumi.ui.player.PlayerScreen
import com.tryniecki.qumi.ui.player.PlayerScreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalVolumeSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun ready() = LocalVolumeUiState(isDiscordSession = true, connected = true,
        fresh = true, observed = LocalVolumeStateDto(true, true, true, 100))

    @Test fun delayedIndicatorKeepsSliderAndPercentageStable() {
        val state = mutableStateOf(ready())
        compose.setContent { MaterialTheme { LocalVolumeSheetContent(state.value, {}, {}, {}) } }
        val slider = compose.onNodeWithTag("local-volume-slider").fetchSemanticsNode().boundsInRoot
        val percentage = compose.onNodeWithTag("local-volume-percentage").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state.value = state.value.copy(sending = true) }
        compose.onNodeWithTag("local-volume-sending").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(showSendingIndicator = true) }
        compose.onNodeWithTag("local-volume-sending").assertIsDisplayed()
        assertEquals(slider, compose.onNodeWithTag("local-volume-slider").fetchSemanticsNode().boundsInRoot)
        assertEquals(percentage, compose.onNodeWithTag("local-volume-percentage").fetchSemanticsNode().boundsInRoot)
        val indicator = compose.onNodeWithTag("local-volume-sending").fetchSemanticsNode().boundsInRoot
        assertTrue(indicator.left > percentage.right)
        assertEquals(percentage.center.y, indicator.center.y, 1f)
        compose.runOnIdle { state.value = state.value.copy(sending = false, showSendingIndicator = false) }
        compose.onNodeWithTag("local-volume-sending").assertDoesNotExist()
        assertEquals(slider, compose.onNodeWithTag("local-volume-slider").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun compactSheetHasHorizontalSliderAndPercentageWithoutHeading() {
        compose.setContent { MaterialTheme { LocalVolumeSheet(ready(), {}, {}, {}, {}) } }
        compose.onNodeWithTag("local-volume-sheet").assertIsDisplayed()
        val slider = compose.onNodeWithTag("local-volume-slider").fetchSemanticsNode().boundsInRoot
        val percentage = compose.onNodeWithTag("local-volume-percentage").fetchSemanticsNode().boundsInRoot
        assertTrue(slider.width > slider.height * 3)
        assertTrue(percentage.top >= slider.bottom)
        compose.onNodeWithText(compose.activity.getString(R.string.local_volume_title)).assertDoesNotExist()
        compose.onNodeWithTag("local-volume-status").assertDoesNotExist()
        compose.onNodeWithTag("local-volume-percentage").assertIsDisplayed()
        compose.onNodeWithTag("local-volume-reference").assertIsDisplayed()
    }

    @Test fun installedHelperButtonOpensSheetAndSwipeDismissesIt() {
        val installed = mutableStateOf(false)
        val open = mutableStateOf(false)
        var cancellations = 0
        compose.setContent {
            MaterialTheme {
                PlayerScreen(ui = PlayerScreenState(),
                    onDiscordSelectionOpen = {}, onSkip = {}, onStop = {},
                    onRepeatToggle = {}, onRadioToggle = {}, onRemoveEntry = {},
                    onSwapEntries = { _, _, _ -> true }, onClearQueue = {},
                    isFavorite = { false }, onToggleFavorite = {}, favoritesBusy = false,
                    onDismissMessage = {}, onSearchOpen = {},
                    showLocalVolumeButton = installed.value,
                    onLocalVolumeOpen = { open.value = true })
                if (open.value) LocalVolumeSheet(ready(), {}, {}, { cancellations++ }, { open.value = false })
            }
        }
        compose.onNodeWithTag("local-volume-open").assertDoesNotExist()
        compose.runOnIdle { installed.value = true }
        compose.onNodeWithTag("local-volume-open").performScrollTo().performClick()
        compose.onNodeWithTag("local-volume-sheet").assertIsDisplayed().performTouchInput { swipeDown() }
        compose.onNodeWithTag("local-volume-sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, cancellations); installed.value = false }
        compose.onNodeWithTag("local-volume-open").assertDoesNotExist()
    }

    @Test fun sheetBackDismissesAndCancelsDraft() {
        val open = mutableStateOf(true)
        var cancellations = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("underlying-player"))
                if (open.value) LocalVolumeSheet(ready(), {}, {}, { cancellations++ }, { open.value = false })
            }
        }
        compose.onNodeWithTag("local-volume-sheet").assertIsDisplayed()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("local-volume-sheet").assertDoesNotExist()
        compose.onNodeWithTag("underlying-player").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, cancellations) }
    }

    @Test fun guestShowsLoginRequirementAndNoSyntheticSlider() {
        compose.setContent { MaterialTheme { LocalVolumeSheetContent(LocalVolumeUiState(), {}, {}, {}) } }
        compose.onNodeWithText(compose.activity.getString(R.string.local_volume_guest)).assertIsDisplayed()
        compose.onNodeWithText("—").assertIsDisplayed()
        compose.onNodeWithTag("local-volume-slider").assertDoesNotExist()
    }

    @Test fun offlineHelperOffersItsLinkInsteadOfControls() {
        val offline = ready().copy(observed = LocalVolumeStateDto(true, false, false, null))
        compose.setContent { MaterialTheme { LocalVolumeSheet(offline, {}, {}, {}, {}) } }
        compose.onNodeWithTag("local-volume-helper-notice").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.local_volume_helper_start)).assertIsDisplayed()
        compose.onNodeWithTag("local-volume-slider").assertDoesNotExist()
    }

    @Test fun staleApiStateDisablesSlider() {
        compose.setContent {
            MaterialTheme {
                LocalVolumeSheetContent(LocalVolumeUiState(isDiscordSession = true,
                    observed = LocalVolumeStateDto(true, true, true, 87)), {}, {}, {})
            }
        }
        compose.onNodeWithTag("local-volume-slider").assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.local_volume_disconnected)).assertIsDisplayed()
    }

    @Test fun accessibilityProgressIsIntegerAndCommitsOneIntent() {
        val state = mutableStateOf(ready())
        var draft: Int? = null
        var finished = 0
        compose.setContent {
            MaterialTheme {
                LocalVolumeSheetContent(state.value, {
                    draft = it
                    state.value = state.value.copy(draft = it, dragging = true)
                }, { finished++ }, {})
            }
        }
        compose.onNodeWithTag("local-volume-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(125f) }
        compose.runOnIdle { assertEquals(125, draft); assertEquals(1, finished) }
        compose.onNodeWithText(compose.activity.getString(R.string.local_volume_percent, 125)).assertIsDisplayed()
    }

    @Test fun horizontalGestureCommitsOnlyAtEnd() {
        val state = mutableStateOf(ready())
        val drafts = mutableListOf<Int>()
        var finished = 0
        compose.setContent {
            MaterialTheme {
                LocalVolumeSheetContent(state.value, {
                    drafts += it
                    state.value = state.value.copy(draft = it, dragging = true)
                }, { finished++ }, {})
            }
        }
        compose.onNodeWithTag("local-volume-slider").performTouchInput {
            swipe(Offset(width * 0.3f, center.y), Offset(width * 0.8f, center.y))
        }
        compose.runOnIdle {
            assertTrue(drafts.all { it in 0..200 })
            assertTrue(drafts.last() > 100)
            assertEquals(1, finished)
        }
    }

    @Test fun touchDetentHoldsAtReferenceAndReleasesBeyondItsRange() {
        val state = mutableStateOf(ready().copy(observed = LocalVolumeStateDto(true, true, true, 60)))
        var draft: Int? = null
        compose.setContent {
            MaterialTheme {
                LocalVolumeSheetContent(state.value, {
                    draft = it
                    state.value = state.value.copy(draft = it, dragging = true)
                }, {}, {})
            }
        }
        compose.onNodeWithTag("local-volume-reference").assertIsDisplayed()
        compose.onNodeWithTag("local-volume-slider").performTouchInput {
            swipe(Offset(width * 0.3f, center.y), Offset(width * 0.505f, center.y), durationMillis = 500)
        }
        compose.runOnIdle { assertEquals(100, draft) }
        compose.onNodeWithTag("local-volume-slider").performTouchInput {
            swipe(Offset(width * 0.5f, center.y), Offset(width * 0.65f, center.y), durationMillis = 500)
        }
        compose.runOnIdle { assertTrue(requireNotNull(draft) > 103) }
        compose.onNodeWithTag("local-volume-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(99f) }
        compose.runOnIdle { assertEquals(99, draft) }
    }
}
