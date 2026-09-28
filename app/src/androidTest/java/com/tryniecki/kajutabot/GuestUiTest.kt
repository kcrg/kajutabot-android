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
import com.tryniecki.kajutabot.ui.player.SearchScreen
import com.tryniecki.kajutabot.ui.player.SearchUiState
import com.tryniecki.kajutabot.ui.player.SharedEnqueueStatus
import com.tryniecki.kajutabot.ui.player.SharedTrackScreen
import com.tryniecki.kajutabot.api.model.search.SearchItemResponse
import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuestUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val playing = PlaybackTrackResponse(
        contentId = "demo", contentType = "YouTube", title = "Demo", url = "https://example.com/demo",
        durationMilliseconds = 60_000, artworkUrl = null, playCount = 0,
    )

    private fun playingState() = PlayerScreenState(
        selectedGuildId = "demo-guild",
        selectedVoiceChannelId = "voice",
        queue = QueueSnapshotResponse(
            guildId = "demo-guild", voiceChannelId = "voice", nowPlaying = playing,
            nowPlayingFromRadio = false, radio = RadioStateResponse(false),
            pendingEntries = emptyList(), pendingEntriesCount = 0,
            pendingDurationMilliseconds = 0, version = 1, queueVersion = 1,
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
        compose.onNodeWithText(compose.activity.getString(R.string.action_try_guest)).assertIsDisplayed().performClick()
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
                    onRemoveEntry = {}, onSwapEntries = { _, _, _ -> true }, onClearQueue = {},
                    isFavorite = { false }, onToggleFavorite = {}, favoritesBusy = false,
                    onDismissMessage = {}, onSearchOpen = {},
                )
            }
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_add_favorite)).assertExists()
    }

    @Test
    fun discordPlayerStillShowsFavoriteAction() {
        compose.setContent {
            MaterialTheme {
                PlayerScreen(
                    ui = playingState(),
                    onDiscordSelectionOpen = {},
                    onSkip = {}, onStop = {}, onRepeatToggle = {}, onRadioToggle = {},
                    onRemoveEntry = {}, onSwapEntries = { _, _, _ -> true }, onClearQueue = {},
                    isFavorite = { false }, onToggleFavorite = {}, favoritesBusy = false,
                    onDismissMessage = {}, onSearchOpen = {},
                )
            }
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_add_favorite)).assertExists()
    }

    @Test
    fun miniPlayerShowsFavoriteActionForSharedPlayer() {
        compose.setContent {
            MaterialTheme {
                MiniPlayer(
                    slide = NowPlayingSlide("demo", true, "Demo", null, null, 60_000, null, false),
                    track = playing,
                    isMutating = false,
                    isQueueReordering = false,
                    activeControlAction = null,
                    isFavorite = false,
                    favoritesBusy = false,
                    onToggleFavorite = {},
                    onOpenPlayer = {},
                    onSkip = {},
                )
            }
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_add_favorite)).assertExists()
    }

    @Test
    fun searchSearchResultShowsFavoriteAction() {
        compose.setContent {
            MaterialTheme {
                SearchScreen(
                    ui = SearchUiState(searchQuery = "Demo", searchResults = listOf(
                        SearchItemResponse(
                            input = "Demo",
                            track = com.tryniecki.kajutabot.api.model.common.SearchTrackResponse(
                                playing.contentId, playing.contentType, playing.title, playing.url,
                                playing.durationMilliseconds, playing.artworkUrl,
                            ),
                            metricCount = 7,
                            metricCaption = "wyświetleń",
                        ),
                    )),
                    onClose = {}, onQueryChange = {}, onSearchSourceChange = {}, onSubmit = {},
                    onHistoryClick = {}, onResultClick = {},
                    isFavorite = { false }, onToggleFavorite = {}, favoritesBusy = false,
                    onDismissMessage = {},
                )
            }
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.action_add_favorite)).assertExists()
        compose.onNodeWithText("7 wyświetleń").assertExists()
    }

    @Test
    fun searchShowsAndUsesSearchHistoryWhenQueryIsEmpty() {
        var clicked: String? = null

        compose.setContent {
            MaterialTheme {
                SearchScreen(
                    ui = SearchUiState(searchHistory = listOf("first query", "second query")),
                    onClose = {},
                    onQueryChange = {},
                    onSearchSourceChange = {},
                    onSubmit = {},
                    onHistoryClick = { clicked = it },
                    onResultClick = {},
                    isFavorite = { false },
                    onToggleFavorite = {},
                    favoritesBusy = false,
                    onDismissMessage = {},
                )
            }
        }

        compose.onNodeWithText("first query").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("first query", clicked) }
    }

    @Test
    fun sharedPlaylistShowsEveryAddedTrack() {
        compose.setContent {
            MaterialTheme {
                SharedTrackScreen(
                    status = SharedEnqueueStatus.Added(listOf(
                        playing.copy(title = "First track"),
                        playing.copy(contentId = "second", title = "Second track"),
                    )),
                    unconfirmed = false,
                    onClose = {},
                )
            }
        }

        compose.onNodeWithText("First track").assertIsDisplayed()
        compose.onNodeWithText("Second track").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.shared_tracks_added_count, 2)).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_queue_position, 1)).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.player_queue_position, 2)).assertIsDisplayed()
    }

}
