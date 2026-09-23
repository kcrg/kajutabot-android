package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.model.common.SearchTrackResponse
import com.tryniecki.kajutabot.api.model.search.SearchItemResponse
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultFormattingTest {

    private val track = SearchTrackResponse(
        contentId = "video-id",
        contentType = "YouTube",
        title = "Demo",
        url = "https://www.youtube.com/watch?v=video-id",
        durationMilliseconds = 60_000,
        artworkUrl = null,
    )

    @Test
    fun `formats metric count and caption independently from upload date`() {
        val item = SearchItemResponse(
            input = track.url,
            track = track,
            metricCount = 1_234_567,
            metricCaption = "views",
            dateLabel = "Upload 2026-09-21",
        )

        assertEquals("1,234,567 views", formatSearchResultMetric(item, Locale.US))
        assertEquals("Upload 2026-09-21", searchResultUploadLabel(item))
    }

    @Test
    fun `omits upload label when date label is absent`() {
        val item = SearchItemResponse(
            input = track.url,
            track = track,
            metricCount = 42,
            metricCaption = "views",
            dateLabel = null,
        )

        assertNull(searchResultUploadLabel(item))
    }

    @Test
    fun `normalizes protocol relative search thumbnail to https`() {
        assertEquals(
            "https://i.ytimg.com/vi/video-id/hqdefault.jpg",
            normalizeSearchThumbnailUrl(
                "//i.ytimg.com/vi/video-id/hqdefault.jpg",
                "https://api.example.com",
            ),
        )
        assertEquals(
            "https://i.ytimg.com/vi/video-id/hqdefault.jpg",
            normalizeSearchThumbnailUrl(
                " https://i.ytimg.com/vi/video-id/hqdefault.jpg ",
                "https://api.example.com",
            ),
        )
        assertEquals(
            "https://api.example.com/api/v1/cache/thumbnails/YouTube/video-id",
            normalizeSearchThumbnailUrl(
                "/api/v1/cache/thumbnails/YouTube/video-id",
                "https://api.example.com",
            ),
        )
        assertNull(normalizeSearchThumbnailUrl("   ", "https://api.example.com"))
    }

    @Test
    fun `does not show empty state while query has not been searched`() {
        val ui = AddTrackUiState(searchQuery = "demo")

        assertFalse(shouldShowSearchEmptyState(ui, "demo", isUrlInput = false))
    }

    @Test
    fun `shows empty state only after current query completed with no results`() {
        val ui = AddTrackUiState(
            searchQuery = "demo",
            searchResults = emptyList(),
            lastCompletedSearchQuery = "demo",
            isSearching = false,
        )

        assertTrue(shouldShowSearchEmptyState(ui, "demo", isUrlInput = false))
        assertFalse(shouldShowSearchEmptyState(ui, "another", isUrlInput = false))
    }

    @Test
    fun `does not show empty state while search is in progress`() {
        val ui = AddTrackUiState(
            searchQuery = "demo",
            searchResults = emptyList(),
            lastCompletedSearchQuery = "demo",
            isSearching = true,
        )

        assertFalse(shouldShowSearchEmptyState(ui, "demo", isUrlInput = false))
    }
    @Test
    fun `search sources map to Control API values`() {
        assertEquals("YouTube", SearchSourceOption.YOUTUBE.apiValue)
        assertEquals("SoundCloud", SearchSourceOption.SOUNDCLOUD.apiValue)
        assertEquals("Database", SearchSourceOption.DATABASE.apiValue)
        assertEquals("Baza danych", SearchSourceOption.DATABASE.displayName)
    }

}
