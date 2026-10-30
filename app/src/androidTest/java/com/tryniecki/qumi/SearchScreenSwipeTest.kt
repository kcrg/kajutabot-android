package com.tryniecki.qumi

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tryniecki.qumi.api.model.common.SearchTrackResponse
import com.tryniecki.qumi.api.model.search.SearchItemResponse
import com.tryniecki.qumi.ui.components.SwipeActionStatus
import com.tryniecki.qumi.ui.player.SearchScreen
import com.tryniecki.qumi.ui.player.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchScreenSwipeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun resultOnlySwipesRightWithoutMovingListWhenEnqueuing() {
        val result = SearchItemResponse(
            input = "https://example.com/track",
            track = SearchTrackResponse("track", "YouTube", "Search track", "https://example.com/track", 60_000, null),
            metricCount = 42,
            metricCaption = "views",
        )
        var ui by mutableStateOf(SearchUiState(searchQuery = "track", searchResults = listOf(result)))
        val swipedResults = mutableListOf<SearchItemResponse>()
        compose.setContent {
            MaterialTheme {
                SearchScreen(
                    ui = ui,
                    onClose = {},
                    onQueryChange = {},
                    onSearchSourceChange = {},
                    onSubmit = {},
                    onHistoryClick = {},
                    onResultSwipe = {
                        swipedResults += it
                        ui = ui.copy(isMutating = true, searchQueueStatuses = mapOf(it.input to SwipeActionStatus.PENDING))
                    },
                    isFavorite = { false },
                    onToggleFavorite = {},
                    favoritesBusy = false,
                    onDismissMessage = {},
                )
            }
        }

        compose.onNodeWithTag("swipeActionCard").performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertEquals(emptyList<SearchItemResponse>(), swipedResults)
        }
        compose.onNodeWithText("Search track").assertHasNoClickAction().performTouchInput { click() }
        compose.runOnIdle { assertEquals(emptyList<SearchItemResponse>(), swipedResults) }
        val initialTop = compose.onNodeWithTag("swipeActionCard").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("swipeActionCard").performTouchInput { swipeRight() }
        compose.runOnIdle {
            assertEquals(listOf(result), swipedResults)
        }
        val pendingTop = compose.onNodeWithTag("swipeActionCard").fetchSemanticsNode().boundsInRoot.top
        assertEquals(initialTop, pendingTop, 0.5f)
        compose.onNodeWithTag("swipeActionCard")
            .assert(hasStateDescription(compose.activity.getString(R.string.swipe_add_pending)))
        compose.runOnIdle {
            ui = ui.copy(isMutating = false, searchQueueStatuses = mapOf(result.input to SwipeActionStatus.SUCCESS))
        }
        compose.onNodeWithTag("swipeActionCard")
            .assert(hasStateDescription(compose.activity.getString(R.string.swipe_add_success)))
        compose.onNodeWithText("Search track").assertExists()
    }
}
