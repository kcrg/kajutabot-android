package com.tryniecki.qumi.api.model

import com.tryniecki.qumi.api.client.ApiErrors
import com.tryniecki.qumi.api.client.ApiJson
import com.tryniecki.qumi.api.model.common.PlaybackTrackResponse
import com.tryniecki.qumi.api.model.error.ProblemDetailsParser
import com.tryniecki.qumi.api.model.favorites.FavoriteResponse
import com.tryniecki.qumi.api.model.history.PlaybackHistoryEntryResponse
import com.tryniecki.qumi.api.model.jellyfin.JellyfinAudioItemResponse
import com.tryniecki.qumi.api.model.jellyfin.JellyfinConnectionStateResponse
import com.tryniecki.qumi.api.model.queue.EnqueueRequest
import com.tryniecki.qumi.api.model.search.SearchResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ApiContractTest {
    private val queue = """{"guildId":"g","voiceChannelId":null,"nowPlaying":null,"nowPlayingFromRadio":false,""" +
        """"radio":{"isEnabled":false},"pendingEntries":[],"pendingEntriesCount":0,""" +
        """"pendingDurationMilliseconds":0,"version":9,"queueVersion":8}"""

    private fun track(extra: String = "") = ApiJson.decodeFromString<PlaybackTrackResponse>(
        """{"contentId":"a","contentType":"YouTube","title":"t","url":"u","durationMilliseconds":1,""" +
            """"artworkUrl":"/art/a?v=1","playCount":0$extra}""",
    )

    @Test fun `lists use the thumbnail and fall back to the full artwork`() {
        assertEquals("/art/a?v=1", track().listArtworkUrl)
        assertEquals(
            "/art/a?size=thumb&v=1",
            track(""","artworkThumbnailUrl":"/art/a?size=thumb&v=1"""").listArtworkUrl,
        )
    }

    @Test fun `favorite decodes with and without the optional fields`() {
        val base = """{"contentUrl":"u","title":"t","addedAt":"d","contentType":"YouTube","contentId":"a""""
        val bare = ApiJson.decodeFromString<FavoriteResponse>("$base}")
        assertNull(bare.durationMilliseconds)
        assertNull(bare.listArtworkUrl)
        val full = ApiJson.decodeFromString<FavoriteResponse>(
            """$base,"durationMilliseconds":5,"artworkUrl":"/full","artworkThumbnailUrl":"/thumb"}""",
        )
        assertEquals(5L, full.durationMilliseconds)
        assertEquals("/thumb", full.listArtworkUrl)
    }

    @Test fun `history keeps a missing duration and artwork as null`() {
        val base = """{"id":1,"contentType":"Jellyfin","contentId":"a","title":"t","queueInput":"q","startedAtUtc":"d""""
        val bare = ApiJson.decodeFromString<PlaybackHistoryEntryResponse>("$base}")
        assertNull(bare.durationMilliseconds)
        assertNull(bare.listArtworkUrl)
        val full = ApiJson.decodeFromString<PlaybackHistoryEntryResponse>(
            """$base,"durationMilliseconds":7,"artworkUrl":"/full"}""",
        )
        assertEquals(7L, full.durationMilliseconds)
        assertEquals("/full", full.listArtworkUrl)
    }

    @Test fun `Jellyfin item decodes with and without the optional fields`() {
        val bare = ApiJson.decodeFromString<JellyfinAudioItemResponse>("""{"itemId":"i","title":"t"}""")
        assertNull(bare.durationMilliseconds)
        val full = ApiJson.decodeFromString<JellyfinAudioItemResponse>(
            """{"itemId":"i","title":"t","durationMilliseconds":5,"artworkUrl":"/a","artworkThumbnailUrl":"/a"}""",
        )
        assertEquals(5L, full.durationMilliseconds)
        assertEquals("/a", full.artworkThumbnailUrl)
    }

    @Test fun `Jellyfin connection state tells a disconnected account from a connected one`() {
        assertNull(ApiJson.decodeFromString<JellyfinConnectionStateResponse>("""{"connected":false}""").connectionOrNull())
        val connection = ApiJson.decodeFromString<JellyfinConnectionStateResponse>(
            """{"connected":true,"serverUrl":"https://jf","serverName":"Home","jellyfinUserName":"me",""" +
                """"createdAtUtc":"d"}""",
        ).connectionOrNull()
        assertEquals("Home", connection?.serverName)
    }

    @Test fun `search reports sources that did not answer`() {
        assertNull(ApiJson.decodeFromString<SearchResponse>("""{"query":"q","items":[]}""").unavailableSources)
        val partial = ApiJson.decodeFromString<SearchResponse>(
            """{"query":"q","items":[],"unavailableSources":["soundcloud"]}""",
        )
        assertEquals(listOf("soundcloud"), partial.unavailableSources)
    }

    @Test fun `content enqueue sends no inputs`() {
        val body = ApiJson.encodeToString(
            EnqueueRequest("c", expectedQueueVersion = 3, contentType = "Jellyfin", contentId = "i"),
        )
        assertFalse(body.contains("inputs"))
        assertTrue(body.contains(""""contentType":"Jellyfin""""))
    }

    @Test fun `conflict snapshot is read only from a queue version conflict`() {
        val conflict = ProblemDetailsParser.parse("""{"errorCode":"queue_version_conflict","snapshot":$queue}""")
        assertEquals(8L, ApiErrors.conflictSnapshotOf(conflict)?.queueVersion)
        assertNull(ApiErrors.conflictSnapshotOf(ProblemDetailsParser.parse("""{"errorCode":"queue_version_conflict"}""")))
        assertNull(ApiErrors.conflictSnapshotOf(ProblemDetailsParser.parse("""{"errorCode":"other","snapshot":$queue}""")))
    }

    @Test fun `a broken conflict snapshot keeps the error code`() {
        val problem = ProblemDetailsParser.parse("""{"errorCode":"queue_version_conflict","snapshot":{"guildId":1}}""")
        assertEquals(ApiErrors.QUEUE_VERSION_CONFLICT, problem?.errorCode)
        assertNull(ApiErrors.conflictSnapshotOf(problem))
    }

    @Test fun `an error body stays readable after the first look`() {
        val error = HttpException(
            Response.error<Unit>(409, """{"errorCode":"queue_version_conflict"}""".toResponseBody()),
        )
        val first = ApiErrors.problemDetailsOf(error)
        assertNotNull(first)
        assertSame(first, ApiErrors.problemDetailsOf(error))
    }
}
