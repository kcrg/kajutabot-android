package com.tryniecki.kajutabot

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tryniecki.kajutabot.ui.auth.LoginScreen
import com.tryniecki.kajutabot.ui.player.PlayerScreen
import com.tryniecki.kajutabot.ui.player.PlayerScreenState
import com.tryniecki.kajutabot.ui.player.MiniPlayer
import com.tryniecki.kajutabot.ui.player.NowPlayingSlide
import com.tryniecki.kajutabot.ui.player.AddTrackScreen
import com.tryniecki.kajutabot.ui.player.AddTrackUiState
import com.tryniecki.kajutabot.api.model.search.SearchItemResponse
import com.tryniecki.kajutabot.api.model.common.TrackResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuestUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val playing = TrackResponse(
        contentId = "demo", contentType = "YouTube", title = "Demo", url = "https://example.com/demo",
        durationMilliseconds = 60_000, thumbnailUrl = null, playCount = 0,
        cachedAt = null, lastPlayedAt = null,
    )

    private fun playingState() = PlayerScreenState(
        selectedGuildId = "demo-guild",
        selectedVoiceChannelId = "voice",
        queue = QueueSnapshotResponse(
            guildId = "demo-guild", voiceChannelId = "voice", nowPlaying = playing,
            nowPlayingFromRadio = false, radio = RadioStateResponse(false),
            pendingEntries = emptyList(), pendingDurationMilliseconds = 0, version = 1,
        ),
    )

    @Test
    fun guestLoginActionWorksWithoutDiscordConfiguration() {
        var guestClicks = 0
        compose.setContent {
            MaterialTheme {
                LoginScreen(
                    isSigningIn = false,
                    errorMessage = null,
                    isOAuthConfigured = false,
                    onLoginClick = {},
                    onGuestClick = { guestClicks++ },
                )
            }
        }
        compose.onNodeWithText("Wypróbuj jako gość").assertIsDisplayed().performClick()
        assertEquals(1, guestClicks)
    }

    @Test
    fun guestPlayerShowsFavoriteAction() {
        compose.setContent {
            MaterialTheme {
                PlayerScreen(
                    ui = playingState(),
                    onDiscordSelectionOpen = {},
                    onSkip = {}, onStop = {}, onRepeatToggle = {}, onRadioToggle = {},
                    onRemoveEntry = {}, onMoveEntry = { _, _, _ -> }, onClearQueue = {},
                    isFavorite = { false }, onToggleFavorite = {}, favoritesBusy = false,
                    onDismissMessage = {}, onAddTrackOpen = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Dodaj do ulubionych").assertExists()
    }

    @Test
    fun discordPlayerStillShowsFavoriteAction() {
        compose.setContent {
            MaterialTheme {
                PlayerScreen(
                    ui = playingState(),
                    onDiscordSelectionOpen = {},
                    onSkip = {}, onStop = {}, onRepeatToggle = {}, onRadioToggle = {},
                    onRemoveEntry = {}, onMoveEntry = { _, _, _ -> }, onClearQueue = {},
                    isFavorite = { false }, onToggleFavorite = {}, favoritesBusy = false,
                    onDismissMessage = {}, onAddTrackOpen = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Dodaj do ulubionych").assertExists()
    }

    @Test
    fun miniPlayerShowsFavoriteActionForSharedPlayer() {
        compose.setContent {
            MaterialTheme {
                MiniPlayer(
                    slide = NowPlayingSlide("demo", true, "Demo", "", null, null, 60_000, null),
                    track = playing,
                    isMutating = false,
                    activeControlAction = null,
                    isFavorite = false,
                    favoritesBusy = false,
                    onToggleFavorite = {},
                    onOpenPlayer = {},
                    onSkip = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Dodaj do ulubionych").assertExists()
    }

    @Test
    fun addTrackSearchResultShowsFavoriteAction() {
        compose.setContent {
            MaterialTheme {
                AddTrackScreen(
                    ui = AddTrackUiState(searchQuery = "Demo", searchResults = listOf(
                        SearchItemResponse("Demo", playing, 1, null, "Odtworzenia"),
                    )),
                    onClose = {}, onQueryChange = {}, onSubmit = {}, onResultClick = {},
                    isFavorite = { false }, onToggleFavorite = {}, favoritesBusy = false,
                    onDismissMessage = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Dodaj do ulubionych").assertExists()
    }
}
