package com.tryniecki.kajutabot.api.model.search

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserializes search result thumbnail from track`() {
        val response = json.decodeFromString<SearchResponse>(
            """
            {
              "query": "demo",
              "items": [
                {
                  "input": "https://www.youtube.com/watch?v=abc",
                  "track": {
                    "contentId": "abc",
                    "contentType": "YouTube",
                    "title": "Demo",
                    "url": "https://www.youtube.com/watch?v=abc",
                    "durationMilliseconds": 60000,
                    "artworkUrl": "https://i.ytimg.com/vi/abc/hqdefault.jpg"
                  },
                  "metricCount": 1234,
                  "metricCaption": "views",
                  "dateLabel": "2026"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            response.items.single().track.artworkUrl,
        )
    }
}
