package com.tryniecki.qumi

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.test.advanceEventTime
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.bottomCenter
import androidx.compose.ui.test.center
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tryniecki.qumi.api.model.common.PlaybackTrackResponse
import com.tryniecki.qumi.api.model.discord.DiscordGuildResponse
import com.tryniecki.qumi.api.model.queue.QueueEntryResponse
import com.tryniecki.qumi.api.model.queue.QueueSnapshotResponse
import com.tryniecki.qumi.api.model.radio.RadioStateResponse
import com.tryniecki.qumi.ui.player.PlayerScreen
import com.tryniecki.qumi.ui.player.PlayerScreenState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerActionsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun track(title: String) = PlaybackTrackResponse(
        contentId = title,
        contentType = "YouTube",
        title = title,
        url = "https://www.youtube.com/watch?v=$title",
        durationMilliseconds = 60_000,
        artworkUrl = null,
        playCount = 0,
    )

    private fun state(nowPlaying: Boolean = false) = PlayerScreenState(
        selectedGuildId = "guild",
        selectedVoiceChannelId = "voice",
        queue = QueueSnapshotResponse(
            guildId = "guild",
            voiceChannelId = "voice",
            nowPlaying = if (nowPlaying) track("Playing") else null,
            nowPlayingFromRadio = false,
            radio = RadioStateResponse(false),
            pendingEntries = listOf(
                QueueEntryResponse("first", 1, track("First")),
                QueueEntryResponse("second", 2, track("Second")),
            ),
            pendingEntriesCount = 2,
            pendingDurationMilliseconds = 120_000,
            version = 7,
            queueVersion = 7,
        ),
    )

    @Test
    fun destructiveActionsWaitForConfirmation() {
        var stops = 0
        var clears = 0
        compose.setContent {
            MaterialTheme {
                PlayerScreen(
                    ui = state(nowPlaying = true),
                    onDiscordSelectionOpen = {},
                    onSkip = {}, onStop = { stops++ }, onRepeatToggle = {}, onRadioToggle = {},
                    onRemoveEntry = {}, onSwapEntries = { _, _, _ -> true }, onClearQueue = { clears++ },
                    isFavorite = { false }, onToggleFavorite = {}, favoritesBusy = false,
                    onDismissMessage = {}, onSearchOpen = {},
                )
            }
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_stop)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.player_stop_dialog_title)).assertIsDisplayed()
        assertEquals(0, stops)
        compose.onNodeWithText(compose.activity.getString(R.string.action_cancel)).performClick()
        assertEquals(0, stops)

        compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_clear_queue)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.player_clear_dialog_title)).assertIsDisplayed()
        assertEquals(0, clears)
        compose.onNodeWithText(compose.activity.getString(R.string.action_clear)).performClick()
        assertEquals(1, clears)
    }

    @Test
    fun selectedGuildWithoutVoiceChannelMarksTargetFabAndKeepsItClickable() {
        var pickerOpens = 0
        compose.setContent {
            MaterialTheme {
                PlayerScreen(
                    ui = state().copy(
                        selectedVoiceChannelId = null,
                        guilds = listOf(DiscordGuildResponse("guild", "Guild", null, true)),
                    ),
                    onDiscordSelectionOpen = { pickerOpens++ },
                    onSkip = {}, onStop = {}, onRepeatToggle = {}, onRadioToggle = {},
                    onRemoveEntry = {}, onSwapEntries = { _, _, _ -> true }, onClearQueue = {},
                    isFavorite = { false }, onToggleFavorite = {}, favoritesBusy = false,
                    onDismissMessage = {}, onSearchOpen = {},
                )
            }
        }

        compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_change_server_channel))
            .assert(hasStateDescription(compose.activity.getString(R.string.player_voice_channel_required)))
            .performClick()
        compose.runOnIdle { assertEquals(1, pickerOpens) }
    }

    @Test
    fun dragSwapsEntriesWithOriginalQueueVersion() {
        var moved: Triple<String, String, Long>? = null
        compose.setContent {
            MaterialTheme {
                PlayerScreen(
                    ui = state(),
                    onDiscordSelectionOpen = {},
                    onSkip = {}, onStop = {}, onRepeatToggle = {}, onRadioToggle = {},
                    onRemoveEntry = {}, onSwapEntries = { first, second, version ->
                        moved = Triple(first, second, version)
                        true
                    }, onClearQueue = {}, isFavorite = { false }, onToggleFavorite = {},
                    favoritesBusy = false, onDismissMessage = {}, onSearchOpen = {},
                )
            }
        }
        val handle = compose.onNodeWithTag("queue-drag-first", useUnmergedTree = true)
        handle.performScrollTo()
        handle.performTouchInput {
            down(center)
            advanceEventTime(700)
            repeat(6) {
                moveBy(Offset(0f, 100f))
                advanceEventTime(32)
            }
            up()
        }
        compose.waitForIdle()
        assertEquals(Triple("first", "second", 7L), moved)
    }

    @Test
    fun longPressAnywhereOnARowLiftsThatRowInPlace() {
        compose.setContent { MaterialTheme { screen(state()) } }
        val row = compose.onNodeWithTag("queue-drag-first", useUnmergedTree = true)
        row.performScrollTo()
        val rowBounds = row.getUnclippedBoundsInRoot()
        // The auto-scroll loop keeps Compose busy for a whole drag, so the clock is driven by hand.
        compose.mainClock.autoAdvance = false
        row.performTouchInput {
            down(bottomCenter - Offset(0f, 4f))
            advanceEventTime(700)
            moveBy(Offset(0f, 1f))
        }
        compose.mainClock.advanceTimeBy(100)
        val preview = compose.onNodeWithTag("queue-drag-preview", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertEquals(rowBounds.top.value, preview.top.value, 1f)
        assertEquals(rowBounds.bottom.value, preview.bottom.value, 1f)
        row.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun queueUpdateDuringADragDoesNotDropIt() {
        var moved: Triple<String, String, Long>? = null
        val ui = mutableStateOf(state())
        compose.setContent {
            MaterialTheme {
                screen(ui.value, onSwap = { first, second, version ->
                    moved = Triple(first, second, version)
                    true
                })
            }
        }
        val row = compose.onNodeWithTag("queue-drag-first", useUnmergedTree = true)
        row.performScrollTo()
        compose.mainClock.autoAdvance = false
        row.performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, 1f))
        }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnUiThread {
            ui.value = ui.value.copy(queue = ui.value.queue?.copy(version = 8, queueVersion = 8))
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("queue-drag-preview", useUnmergedTree = true).assertExists()
        row.performTouchInput {
            repeat(6) {
                moveBy(Offset(0f, 100f))
                advanceEventTime(32)
            }
            up()
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(Triple("first", "second", 7L), moved)
    }

    @Composable
    private fun screen(
        ui: PlayerScreenState,
        onSwap: (String, String, Long) -> Boolean = { _, _, _ -> true },
    ) {
        PlayerScreen(
            ui = ui,
            onDiscordSelectionOpen = {},
            onSkip = {}, onStop = {}, onRepeatToggle = {}, onRadioToggle = {},
            onRemoveEntry = {}, onSwapEntries = onSwap, onClearQueue = {},
            isFavorite = { false }, onToggleFavorite = {},
            favoritesBusy = false, onDismissMessage = {}, onSearchOpen = {},
        )
    }
}
