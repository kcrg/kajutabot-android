package com.tryniecki.kajutabot.api.model.queue

import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueModelsTest {
    @Test
    fun `move request contains only position and version`() {
        val body = Json.encodeToString(MoveQueueEntryRequest(newPosition = 2, expectedVersion = 7))
        assertTrue(body.contains("\"newPosition\":2"))
        assertTrue(body.contains("\"expectedVersion\":7"))
        assertFalse(body.contains("entryId"))
    }

    @Test
    fun `swap request contains both entry ids and version`() {
        val body = Json.encodeToString(SwapQueueEntriesRequest("first", "second", 123))
        assertTrue(body.contains("\"firstEntryId\":\"first\""))
        assertTrue(body.contains("\"secondEntryId\":\"second\""))
        assertTrue(body.contains("\"expectedVersion\":123"))
    }

    @Test
    fun `playback track contains artwork URL but no internal cache fields`() {
        val body = Json.encodeToString(PlaybackTrackResponse(
            contentId = "id", contentType = "YouTube", title = "Title", url = "https://example.com",
            durationMilliseconds = 1000, artworkUrl = "https://example.com/art.jpg", playCount = 4,
        ))
        assertTrue(body.contains("\"artworkUrl\":\"https://example.com/art.jpg\""))
        assertFalse(body.contains("artworkReference"))
        assertFalse(body.contains("cachedAt"))
        assertFalse(body.contains("thumbnailVersion"))
    }
}
