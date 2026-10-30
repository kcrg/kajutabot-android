package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.ui.feedback.FeedbackEffect
import com.tryniecki.kajutabot.api.client.KajutaBotRealtimeClient
import com.tryniecki.kajutabot.api.client.RealtimeDisconnect
import com.tryniecki.kajutabot.api.model.playback.LocalVolumeStateDto
import com.tryniecki.kajutabot.api.model.queue.QueueSnapshotResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocalVolumeControllerTest {
    @Test fun `volume button requires confirmed installation and remains visible when helper is offline`() {
        val signedIn = LocalVolumeUiState(isDiscordSession = true)
        assertFalse(signedIn.showVolumeButton)
        assertFalse(signedIn.copy(observed = LocalVolumeStateDto(false, false, false, null)).showVolumeButton)
        assertTrue(signedIn.copy(observed = LocalVolumeStateDto(true, false, false, null)).showVolumeButton)
        assertTrue(signedIn.copy(observed = LocalVolumeStateDto(true, true, false, null)).showVolumeButton)
        assertTrue(signedIn.copy(observed = ready()).showVolumeButton)
    }
    @Test fun `the helper notice is for a helper with nothing to report`() {
        val signedIn = LocalVolumeUiState(isDiscordSession = true)
        assertTrue(signedIn.copy(observed = LocalVolumeStateDto(true, false, false, null)).isHelperOffline)
        assertFalse(signedIn.copy(observed = LocalVolumeStateDto(true, true, false, null)).isHelperOffline)
        assertFalse(signedIn.copy(observed = ready()).isHelperOffline)
        assertFalse(LocalVolumeUiState(observed = LocalVolumeStateDto(true, false, false, null)).isHelperOffline)
    }

    @Test fun `a guest always gets the button, which leads to the demo notice`() {
        assertTrue(LocalVolumeUiState(isDiscordSession = false).showVolumeButton)
        assertFalse(LocalVolumeUiState(isDiscordSession = false, observed = ready()).enabled)
    }

    private fun ready(volume: Int = 100) = LocalVolumeStateDto(true, true, true, volume)

    private class FakeClient(var observation: LocalVolumeStateDto) : KajutaBotRealtimeClient {
        override val updates = emptyFlow<QueueSnapshotResponse>()
        override val heartbeats = emptyFlow<Unit>()
        override val closed = MutableSharedFlow<RealtimeDisconnect>()
        override val localVolumeUpdates = MutableSharedFlow<LocalVolumeStateDto>(extraBufferCapacity = 8)
        val commands = mutableListOf<Int>()
        val completions = mutableListOf<CompletableDeferred<Unit>>()
        var snapshot: CompletableDeferred<LocalVolumeStateDto>? = null
        var reads = 0
        var readError: Exception? = null
        override suspend fun start() = Unit
        override suspend fun subscribeGuild(guildId: String) = false
        override suspend fun stop() = Unit
        override suspend fun getLocalVolumeState(): LocalVolumeStateDto {
            reads++
            readError?.let { throw it }
            return snapshot?.await() ?: observation
        }
        override suspend fun setLocalVolume(volume: Int) {
            commands += volume
            CompletableDeferred<Unit>().also(completions::add).await()
        }
        fun event(state: LocalVolumeStateDto) {
            observation = state
            localVolumeUpdates.tryEmit(state)
        }
    }

    private fun TestScope.controller(
        client: FakeClient,
        identity: MutableStateFlow<Long?> = MutableStateFlow(7L),
        discord: Boolean = true,
        onFeedback: (FeedbackEffect) -> Unit = {},
    ): LocalVolumeController = LocalVolumeController(backgroundScope, 7, identity, discord, onFeedback = onFeedback).also {
        it.bind(client)
        it.connected()
        runCurrent()
    }

    @Test fun `only acknowledged final local volume command ends gesture and external updates stay silent`() = runTest {
        val effects = mutableListOf<FeedbackEffect>()
        val client = FakeClient(ready(87))
        val controller = controller(client, onFeedback = effects::add)
        client.event(ready(90)); runCurrent()
        assertTrue(effects.isEmpty())
        controller.changeDraft(100)
        controller.cancelGesture()
        controller.finishGesture()
        runCurrent()
        assertTrue(effects.isEmpty())
        controller.submit(120); runCurrent()
        controller.submit(130)
        client.completions[0].complete(Unit); runCurrent()
        assertTrue(effects.isEmpty())
        client.completions[1].complete(Unit); runCurrent()
        assertEquals(listOf(FeedbackEffect.GestureEnd), effects)
        controller.submit(140); runCurrent()
        client.completions[2].completeExceptionally(IllegalStateException("helper_busy")); runCurrent()
        assertEquals(listOf(FeedbackEffect.GestureEnd, FeedbackEffect.Reject), effects)
        controller.close()
    }

    @Test fun `initial snapshot and events from desktop or another client are authoritative`() = runTest {
        val client = FakeClient(ready(87))
        val controller = controller(client)
        assertEquals(87, controller.state.value.displayedVolume)
        client.event(ready(123)); runCurrent()
        assertEquals(123, controller.state.value.displayedVolume)
        assertEquals(1, client.reads)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, client.reads) // No polling.
    }

    @Test fun `installation and helper availability are distinguished without invented volume`() = runTest {
        val states = listOf(
            LocalVolumeStateDto(false, false, false, null) to R.string.local_volume_install,
            LocalVolumeStateDto(true, false, false, null) to R.string.local_volume_offline,
            LocalVolumeStateDto(true, true, false, null) to R.string.local_volume_unavailable,
        )
        for ((state, message) in states) {
            val controller = controller(FakeClient(state))
            assertFalse(controller.state.value.enabled)
            assertNull(controller.state.value.displayedVolume)
            assertEquals(message, localVolumeStatus(controller.state.value))
            controller.close()
        }
        assertTrue(controller(FakeClient(ready())).state.value.enabled)
    }

    @Test fun `guest never reads sends or consumes user state`() = runTest {
        val client = FakeClient(ready())
        val controller = controller(client, discord = false)
        controller.submit(100)
        client.event(ready(200)); runCurrent()
        assertEquals(0, client.reads)
        assertTrue(client.commands.isEmpty())
        assertNull(controller.state.value.observed)
        assertEquals(R.string.local_volume_guest, localVolumeStatus(controller.state.value))
    }

    @Test fun `integer boundaries sent unchanged and invalid values rejected`() = runTest {
        for (volume in listOf(0, 100, 200)) {
            val client = FakeClient(ready())
            val controller = controller(client)
            controller.submit(volume); runCurrent()
            assertEquals(listOf(volume), client.commands)
            controller.close()
        }
        val client = FakeClient(ready())
        val controller = controller(client)
        controller.submit(-1); controller.submit(201); runCurrent()
        assertTrue(client.commands.isEmpty())
        assertEquals(LocalVolumeError.INVALID_VOLUME, controller.state.value.error)
    }

    @Test fun `one in flight command keeps only latest conscious intent`() = runTest {
        val client = FakeClient(ready())
        val controller = controller(client)
        controller.submit(110); runCurrent()
        controller.submit(120); controller.submit(130); runCurrent()
        assertEquals(listOf(110), client.commands)
        client.completions[0].complete(Unit); runCurrent()
        assertEquals(listOf(110, 130), client.commands)
        client.completions[1].complete(Unit); runCurrent()
        assertFalse(controller.state.value.sending)
        assertEquals(100, controller.state.value.displayedVolume) // No fabricated observation.
    }

    @Test fun `event before completion uses actual discord value and keeps drag stable`() = runTest {
        val client = FakeClient(ready())
        val controller = controller(client)
        controller.changeDraft(125)
        client.event(ready(91)); runCurrent()
        assertEquals(125, controller.state.value.displayedVolume)
        assertEquals(91, controller.state.value.observed?.volume)
        controller.finishGesture(); runCurrent()
        client.event(ready(124)); runCurrent()
        assertEquals(125, controller.state.value.displayedVolume)
        client.completions.single().complete(Unit); runCurrent()
        assertEquals(124, controller.state.value.displayedVolume)
    }

    @Test fun `successful unchanged command needs no event or extra read`() = runTest {
        val client = FakeClient(ready())
        val controller = controller(client)
        controller.submit(100); runCurrent()
        client.completions.single().complete(Unit); runCurrent()
        assertEquals(100, controller.state.value.displayedVolume)
        assertFalse(controller.state.value.sending)
        assertEquals(1, client.reads)
    }

    @Test fun `fast successful completion never shows indicator`() = runTest {
        val client = FakeClient(ready())
        val controller = controller(client)
        controller.submit(125); runCurrent()
        advanceTimeBy(99); runCurrent()
        assertTrue(controller.state.value.sending)
        assertFalse(controller.state.value.showSendingIndicator)
        client.completions.single().complete(Unit); runCurrent()
        advanceTimeBy(200); runCurrent()
        assertFalse(controller.state.value.sending)
        assertFalse(controller.state.value.showSendingIndicator)
    }

    @Test fun `indicator appears at 100ms and event is not command completion`() = runTest {
        val client = FakeClient(ready())
        val controller = controller(client)
        controller.submit(125); runCurrent()
        client.event(ready(124)); runCurrent()
        advanceTimeBy(99); runCurrent()
        assertFalse(controller.state.value.showSendingIndicator)
        advanceTimeBy(1); runCurrent()
        assertTrue(controller.state.value.showSendingIndicator)
        client.completions.single().complete(Unit); runCurrent()
        assertFalse(controller.state.value.showSendingIndicator)
        assertEquals(124, controller.state.value.displayedVolume)
    }

    @Test fun `each queued command gets its own indicator delay`() = runTest {
        val client = FakeClient(ready())
        val controller = controller(client)
        controller.submit(125); runCurrent()
        controller.submit(150)
        advanceTimeBy(100); runCurrent()
        assertTrue(controller.state.value.showSendingIndicator)
        client.completions[0].complete(Unit); runCurrent()
        assertEquals(listOf(125, 150), client.commands)
        assertFalse(controller.state.value.showSendingIndicator)
        advanceTimeBy(99); runCurrent()
        assertFalse(controller.state.value.showSendingIndicator)
        client.completions[1].complete(Unit); runCurrent()
        advanceTimeBy(100); runCurrent()
        assertFalse(controller.state.value.showSendingIndicator)
    }

    @Test fun `fast error does not show delayed indicator during recovery read`() = runTest {
        val client = FakeClient(ready())
        val controller = controller(client)
        controller.submit(125); runCurrent()
        client.snapshot = CompletableDeferred()
        client.completions.single().completeExceptionally(IllegalStateException("helper_busy"))
        runCurrent()
        advanceTimeBy(200); runCurrent()
        assertFalse(controller.state.value.sending)
        assertFalse(controller.state.value.showSendingIndicator)
        assertEquals(LocalVolumeError.BUSY, controller.state.value.error)
    }

    @Test fun `disconnect cancels delayed indicator and clears visible indicator`() = runTest {
        for (elapsed in listOf(50L, 150L)) {
            val client = FakeClient(ready())
            val controller = controller(client)
            controller.submit(125); runCurrent()
            advanceTimeBy(elapsed); runCurrent()
            controller.disconnect()
            advanceTimeBy(200); runCurrent()
            assertFalse(controller.state.value.sending)
            assertFalse(controller.state.value.showSendingIndicator)
            controller.close()
        }
    }

    @Test fun `client timeout clears pending and reads once without retry`() = runTest {
        val client = FakeClient(ready())
        val controller = controller(client)
        controller.submit(125); runCurrent()
        controller.submit(140)
        client.observation = ready(124)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(listOf(125), client.commands)
        assertEquals(2, client.reads)
        assertEquals(124, controller.state.value.displayedVolume)
        assertEquals(LocalVolumeError.TIMEOUT, controller.state.value.error)
        assertFalse(controller.state.value.sending)
        client.completions.single().complete(Unit); runCurrent()
        assertEquals(listOf(125), client.commands)
    }

    @Test fun `hub error removes pending and recovers exactly once`() = runTest {
        val client = FakeClient(ready())
        val controller = controller(client)
        controller.submit(125); runCurrent(); controller.submit(150)
        client.completions.single().completeExceptionally(IllegalStateException("Invocation: helper_busy"))
        runCurrent()
        assertEquals(LocalVolumeError.BUSY, controller.state.value.error)
        assertEquals(2, client.reads)
        assertEquals(listOf(125), client.commands)
        assertFalse(controller.state.value.sending)
    }

    @Test fun `snapshot cannot overwrite event received during read`() = runTest {
        val client = FakeClient(ready(80))
        client.snapshot = CompletableDeferred()
        val controller = controller(client)
        client.event(ready(130)); runCurrent()
        client.snapshot!!.complete(ready(80)); runCurrent()
        assertEquals(130, controller.state.value.observed?.volume)
        assertTrue(controller.state.value.enabled)
    }

    @Test fun `reconnect discards commands retains stale installation and requires fresh snapshot`() = runTest {
        val old = FakeClient(ready(90))
        val controller = controller(old)
        controller.submit(125); runCurrent(); controller.submit(150)
        controller.disconnect()
        assertFalse(controller.state.value.enabled)
        assertFalse(controller.state.value.fresh)
        assertTrue(controller.state.value.observed!!.installed)
        assertNull(controller.state.value.draft)
        val new = FakeClient(ready(95)).also { it.snapshot = CompletableDeferred() }
        controller.bind(new); controller.connected(); runCurrent()
        assertFalse(controller.state.value.enabled)
        old.event(ready(200)); old.completions.single().complete(Unit); runCurrent()
        new.snapshot!!.complete(ready(95)); runCurrent()
        assertEquals(95, controller.state.value.observed?.volume)
        assertTrue(controller.state.value.enabled)
        assertTrue(new.commands.isEmpty())
    }

    @Test fun `logout and account switch clear all state and ignore late callbacks`() = runTest {
        for (nextIdentity in listOf(null, 8L)) {
            val identity = MutableStateFlow<Long?>(7L)
            val client = FakeClient(ready())
            val controller = controller(client, identity)
            controller.submit(125); runCurrent(); controller.submit(150)
            identity.value = nextIdentity
            client.event(ready(200)); client.completions.single().complete(Unit); runCurrent()
            assertEquals(LocalVolumeUiState(), controller.state.value)
            controller.submit(175); runCurrent()
            assertEquals(listOf(125), client.commands)
            controller.close()
        }
    }

    @Test fun `loss of availability discards queued intent even if availability returns`() = runTest {
        val client = FakeClient(ready())
        val controller = controller(client)
        controller.submit(120); runCurrent(); controller.submit(180)
        client.event(LocalVolumeStateDto(true, false, false, null)); runCurrent()
        assertNull(controller.state.value.displayedVolume)
        client.event(ready(119)); runCurrent()
        client.completions.single().complete(Unit); runCurrent()
        assertEquals(listOf(120), client.commands)
        assertEquals(119, controller.state.value.displayedVolume)
    }

    @Test fun `older API missing methods is controlled and does not affect playback`() = runTest {
        val client = FakeClient(ready()).also { it.readError = IllegalStateException("Method does not exist.") }
        val controller = controller(client)
        assertTrue(controller.state.value.unsupported)
        assertFalse(controller.state.value.enabled)
        assertEquals(R.string.local_volume_unsupported, localVolumeStatus(controller.state.value))
        assertEquals(1, client.reads)
    }

    @Test fun `all wrapped backend errors map to safe categories`() {
        val errors = mapOf(
            "local_volume_access_denied" to LocalVolumeError.ACCESS_DENIED,
            "invalid_volume" to LocalVolumeError.INVALID_VOLUME,
            "helper_offline" to LocalVolumeError.OFFLINE,
            "volume_unavailable" to LocalVolumeError.UNAVAILABLE,
            "discord_rpc_failed" to LocalVolumeError.RPC_FAILED,
            "discord_rpc_timeout" to LocalVolumeError.TIMEOUT,
            "helper_busy" to LocalVolumeError.BUSY,
            "helper_timeout" to LocalVolumeError.TIMEOUT,
        )
        errors.forEach { (code, category) ->
            assertEquals(category, localVolumeError(RuntimeException("Invocation failed",
                IllegalStateException("HubException: $code"))))
        }
    }

    @Test fun `missing Set method stays unsupported even when recovery Get succeeds`() = runTest {
        val client = FakeClient(ready())
        val controller = controller(client)
        controller.submit(125); runCurrent(); controller.submit(150)
        client.completions.single().completeExceptionally(IllegalStateException("Method does not exist."))
        runCurrent()
        assertTrue(controller.state.value.unsupported)
        assertFalse(controller.state.value.enabled)
        assertEquals(2, client.reads)
        assertEquals(listOf(125), client.commands)
    }

    @Test fun `invalid DTO cannot enable slider or turn null into a volume`() = runTest {
        val client = FakeClient(LocalVolumeStateDto(true, true, true, null))
        val controller = controller(client)
        assertFalse(controller.state.value.enabled)
        assertNull(controller.state.value.displayedVolume)
        client.event(ready(100)); runCurrent()
        controller.submit(125); runCurrent(); controller.submit(150)
        client.event(ready(201)); runCurrent()
        assertFalse(controller.state.value.enabled)
        client.completions.single().complete(Unit); runCurrent()
        assertEquals(listOf(125), client.commands)
    }
}
