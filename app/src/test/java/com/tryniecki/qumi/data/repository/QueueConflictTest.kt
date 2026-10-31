package com.tryniecki.qumi.data.repository

import com.tryniecki.qumi.api.model.queue.QueueSnapshotResponse
import com.tryniecki.qumi.api.model.radio.RadioStateResponse
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class QueueConflictTest {
    private val queueJson = """{"guildId":"g","voiceChannelId":null,"nowPlaying":null,"nowPlayingFromRadio":false,""" +
        """"radio":{"isEnabled":false},"pendingEntries":[],"pendingEntriesCount":0,""" +
        """"pendingDurationMilliseconds":0,"version":9,"queueVersion":8}"""

    private val fetched = QueueSnapshotResponse(
        guildId = "g",
        voiceChannelId = null,
        nowPlaying = null,
        nowPlayingFromRadio = false,
        radio = RadioStateResponse(isEnabled = false),
        pendingEntries = emptyList(),
        pendingEntriesCount = 0,
        pendingDurationMilliseconds = 0,
        version = 1,
        queueVersion = 1,
    )

    private fun conflict(body: String) = HttpException(Response.error<Unit>(409, body.toResponseBody()))

    private fun failMutation(error: Exception, fetch: () -> Unit = {}): Pair<Int, QueueMutationCoordinator> {
        var fetches = 0
        val coordinator = QueueMutationCoordinator()
        coordinator.observe(fetched)
        assertThrows(HttpException::class.java) {
            runBlocking {
                coordinator.run(
                    guildId = "g",
                    fetch = { fetches++; fetch(); fetched },
                    mutation = { _, _ -> throw error },
                )
            }
        }
        return fetches to coordinator
    }

    @Test fun `conflict with a snapshot needs no extra request`() {
        val (fetches) = failMutation(conflict("""{"errorCode":"queue_version_conflict","snapshot":$queueJson}"""))
        assertEquals(0, fetches)
    }

    @Test fun `conflict without a snapshot fetches the queue`() {
        val (fetches) = failMutation(conflict("""{"errorCode":"queue_version_conflict"}"""))
        assertEquals(1, fetches)
    }

    @Test fun `next mutation uses the version from the conflict snapshot`() = runBlocking {
        val (_, coordinator) = failMutation(conflict("""{"errorCode":"queue_version_conflict","snapshot":$queueJson}"""))
        var token = 0L
        coordinator.run("g", fetch = { fetched }, mutation = { version, _ -> token = version; fetched })
        assertEquals(8L, token)
    }
}
