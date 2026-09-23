package com.tryniecki.kajutabot

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.queue.QueueEntryResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import com.tryniecki.kajutabot.ui.player.PlayerScreen
import com.tryniecki.kajutabot.ui.player.PlayerScreenState
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
            pendingDurationMilliseconds = 120_000,
            version = 7,
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
                    onRemoveEntry = {}, onMoveEntry = { _, _, _ -> }, onClearQueue = { clears++ },
                    isFavorite = { false }, onToggleFavorite = {}, favoritesBusy = false,
                    onDismissMessage = {}, onAddTrackOpen = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Zatrzymaj").performClick()
        compose.onNodeWithText("Zatrzymać odtwarzanie?").assertIsDisplayed()
        assertEquals(0, stops)
        compose.onNodeWithText("Anuluj").performClick()
        assertEquals(0, stops)

        compose.onNodeWithContentDescription("Wyczyść kolejkę").performClick()
        compose.onNodeWithText("Wyczyścić kolejkę?").assertIsDisplayed()
        assertEquals(0, clears)
        compose.onNodeWithText("Wyczyść").performClick()
        assertEquals(1, clears)
    }

    @Test
    fun dragUsesOneBasedPositionAndOriginalQueueVersion() {
        var moved: Triple<String, Int, Long>? = null
        compose.setContent {
            MaterialTheme {
                PlayerScreen(
                    ui = state(),
                    onDiscordSelectionOpen = {},
                    onSkip = {}, onStop = {}, onRepeatToggle = {}, onRadioToggle = {},
                    onRemoveEntry = {}, onMoveEntry = { id, position, version ->
                        moved = Triple(id, position, version)
                    }, onClearQueue = {}, isFavorite = { false }, onToggleFavorite = {},
                    favoritesBusy = false, onDismissMessage = {}, onAddTrackOpen = {},
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
        assertEquals(Triple("first", 2, 7L), moved)
    }
}
