package com.tryniecki.kajutabot.data.repository

import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import com.tryniecki.kajutabot.api.model.radio.RadioStateResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

class QueueMutationCoordinatorTest {
    private fun snapshot(version: Long, queueVersion: Long) = QueueSnapshotResponse(
        guildId = "guild", voiceChannelId = "voice", nowPlaying = null,
        nowPlayingFromRadio = false, radio = RadioStateResponse(false),
        pendingEntries = emptyList(), pendingEntriesCount = 0,
        pendingDurationMilliseconds = 0, version = version, queueVersion = queueVersion,
    )

    @Test fun `local mutations serialize and take queue token from previous response`() = runBlocking {
        val coordinator = QueueMutationCoordinator()
        coordinator.observe(snapshot(20, 10))
        val firstStarted = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Unit>()
        val tokens = mutableListOf<Long>()
        val first = async {
            coordinator.run("guild", { error("unexpected fetch") }) { token ->
                tokens += token
                firstStarted.complete(Unit)
                finishFirst.await()
                snapshot(21, 11)
            }
        }
        firstStarted.await()
        val second = async {
            coordinator.run("guild", { error("unexpected fetch") }) { token ->
                tokens += token
                snapshot(22, 12)
            }
        }
        finishFirst.complete(Unit)
        first.await()
        second.await()
        assertEquals(listOf(10L, 11L), tokens)
    }

    @Test fun `transport failure reconciles but never retries mutation`() = runBlocking {
        val coordinator = QueueMutationCoordinator()
        coordinator.observe(snapshot(20, 10))
        val failure = IllegalStateException("timeout")
        var calls = 0
        var fetches = 0
        val caught = runCatching {
            coordinator.run("guild", { fetches++; snapshot(21, 11) }) {
                calls++
                throw failure
            }
        }.exceptionOrNull()
        assertSame(failure, caught)
        assertEquals(1, calls)
        assertEquals(1, fetches)
        var nextToken = -1L
        coordinator.run("guild", { error("unexpected fetch") }) {
            nextToken = it
            snapshot(22, 12)
        }
        assertEquals(11L, nextToken)
    }

    @Test fun `older snapshot cannot replace queue token even when its token is higher`() = runBlocking {
        val coordinator = QueueMutationCoordinator()
        coordinator.observe(snapshot(20, 10))
        coordinator.observe(snapshot(19, 99))
        var token = -1L
        coordinator.run("guild", { error("unexpected fetch") }) {
            token = it
            snapshot(21, 11)
        }
        assertEquals(10L, token)
    }

    @Test fun `409 and 503 each reconcile once without blind retry`() = runBlocking {
        for (status in listOf(409, 503)) {
            val coordinator = QueueMutationCoordinator()
            coordinator.observe(snapshot(20, 10))
            var calls = 0
            var fetches = 0
            val failure = HttpException(Response.error<Any>(status, "failure".toResponseBody()))
            val caught = runCatching {
                coordinator.run("guild", { fetches++; snapshot(21, 11) }) {
                    calls++
                    throw failure
                }
            }.exceptionOrNull()
            assertSame(failure, caught)
            assertEquals(1, calls)
            assertEquals(1, fetches)
        }
    }

    @Test fun `failed reconciliation forces a fresh snapshot before next action`() = runBlocking {
        val coordinator = QueueMutationCoordinator()
        coordinator.observe(snapshot(20, 10))
        runCatching {
            coordinator.run("guild", { error("offline") }) { error("timeout") }
        }
        var fetched = 0
        var token = -1L
        coordinator.run("guild", { fetched++; snapshot(21, 11) }) {
            token = it
            snapshot(22, 12)
        }
        assertEquals(1, fetched)
        assertEquals(11L, token)
    }
}
