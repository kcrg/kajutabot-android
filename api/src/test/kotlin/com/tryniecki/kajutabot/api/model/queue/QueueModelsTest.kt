package com.tryniecki.kajutabot.api.model.queue

import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueModelsTest {
    @Test
    fun `snapshot decodes queue totals and playback instance from REST and realtime payload`() {
        val response = Json.decodeFromString<QueueSnapshotResponse>(
            """{"guildId":"g","voiceChannelId":"c","nowPlaying":{"contentId":"x","contentType":"YouTube","title":"Song","url":"https://example.com/x","durationMilliseconds":120000,"artworkUrl":"/api/v1/artwork/x","playCount":0},"nowPlayingFromRadio":false,"radio":{"isEnabled":false},"pendingEntries":[],"pendingEntriesCount":0,"pendingDurationMilliseconds":0,"version":3,"nowPlayingStartedAt":null,"playbackInstanceId":"c95a67b2-a247-456b-b33e-fc9cd03f79d1","playbackPositionMilliseconds":0}""",
        )
        assertEquals(0, response.pendingEntriesCount)
        assertEquals("c95a67b2-a247-456b-b33e-fc9cd03f79d1", response.playbackInstanceId)
        assertEquals(0L, response.playbackPositionMilliseconds)
        assertEquals("/api/v1/artwork/x", response.nowPlaying?.artworkUrl)
        assertEquals(null, response.nowPlayingStartedAt)
        assertEquals(null, response.addedTracks)
    }

    @Test
    fun `enqueue response reads added track independently of playback and pending entries`() {
        val response = Json.decodeFromString<QueueSnapshotResponse>(
            """{"guildId":"g","voiceChannelId":"c","nowPlaying":null,"nowPlayingFromRadio":false,"radio":{"isEnabled":false},"pendingEntries":[{"entryId":"entry-1","position":1,"track":{"contentId":"pending","contentType":"YouTube","title":"Pending track","url":"https://example.com/pending","durationMilliseconds":120000,"artworkUrl":null,"playCount":0}}],"pendingEntriesCount":1,"pendingDurationMilliseconds":120000,"version":4,"addedTracks":[{"contentId":"shared","contentType":"YouTube","title":"Shared track","url":"https://example.com/shared","durationMilliseconds":130000,"artworkUrl":"/api/v1/app/artwork/YouTube/shared","playCount":0,"artworkAccentColor":null}]}""",
        )
        assertEquals("Pending track", response.pendingEntries.single().track.title)
        assertEquals("Shared track", response.addedTracks?.single()?.title)
        assertEquals("/api/v1/app/artwork/YouTube/shared", response.addedTracks?.single()?.artworkUrl)
    }

    @Test
    fun `skip response carries repeated track outcome`() {
        val response = Json.decodeFromString<QueueSnapshotResponse>(
            """{"guildId":"g","voiceChannelId":"c","nowPlaying":null,"nowPlayingFromRadio":false,"radio":{"isEnabled":false},"pendingEntries":[],"pendingEntriesCount":0,"pendingDurationMilliseconds":0,"version":2,"playbackInstanceId":null,"playbackPositionMilliseconds":null,"skipOutcome":"RestartedRepeatedTrack"}""",
        )
        assertEquals(SkipOutcome.RestartedRepeatedTrack, response.skipOutcome)
    }

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
