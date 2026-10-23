package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.client.KajutaBotRealtimeClient
import com.tryniecki.kajutabot.ui.feedback.FeedbackEffect
import com.tryniecki.kajutabot.api.model.playback.LocalVolumeStateDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class LocalVolumeError {
    ACCESS_DENIED, INVALID_VOLUME, OFFLINE, UNAVAILABLE, RPC_FAILED, TIMEOUT, BUSY,
    UNSUPPORTED, FAILED,
}

internal fun localVolumeError(error: Throwable): LocalVolumeError {
    if (error is TimeoutCancellationException) return LocalVolumeError.TIMEOUT
    val message = generateSequence(error) { it.cause }.take(4)
        .joinToString(" ") { it.message.orEmpty() }.lowercase()
    return when {
        "local_volume_access_denied" in message -> LocalVolumeError.ACCESS_DENIED
        "invalid_volume" in message -> LocalVolumeError.INVALID_VOLUME
        "helper_offline" in message -> LocalVolumeError.OFFLINE
        "volume_unavailable" in message -> LocalVolumeError.UNAVAILABLE
        "discord_rpc_failed" in message -> LocalVolumeError.RPC_FAILED
        "discord_rpc_timeout" in message || "helper_timeout" in message -> LocalVolumeError.TIMEOUT
        "helper_busy" in message -> LocalVolumeError.BUSY
        "unknown hub method" in message || "method does not exist" in message ||
            "method" in message && "not found" in message -> LocalVolumeError.UNSUPPORTED
        else -> LocalVolumeError.FAILED
    }
}

data class LocalVolumeUiState(
    val isDiscordSession: Boolean = false,
    val connected: Boolean = false,
    val fresh: Boolean = false,
    val observed: LocalVolumeStateDto? = null,
    val draft: Int? = null,
    val dragging: Boolean = false,
    val sending: Boolean = false,
    val showSendingIndicator: Boolean = false,
    val error: LocalVolumeError? = null,
    val unsupported: Boolean = false,
) {
    val showVolumeButton: Boolean get() = isDiscordSession && observed?.installed == true
    val enabled: Boolean get() = isDiscordSession && connected && fresh && !unsupported &&
        observed?.let { it.isValid() && it.available } == true
    val displayedVolume: Int? get() = draft ?: observed?.volume
}

/** Confined to the ViewModel dispatcher, just like PlayerRealtime. Never owns a connection. */
internal class LocalVolumeController(
    private val scope: CoroutineScope,
    private val expectedIdentity: Long,
    private val sessionIdentity: StateFlow<Long?>,
    private val discordSession: Boolean,
    private val timeoutMs: Long = 10_000L,
    private val onFeedback: (FeedbackEffect) -> Unit = {},
) {
    private val _state = MutableStateFlow(LocalVolumeUiState(isDiscordSession = discordSession))
    val state: StateFlow<LocalVolumeUiState> = _state.asStateFlow()
    private var client: KajutaBotRealtimeClient? = null
    private var generation = 0L
    private var eventCount = 0L
    private var readCount = 0L
    private var pending: Int? = null
    private var eventsJob: Job? = null
    private var readJob: Job? = null
    private var commandJob: Job? = null
    private val sessionJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        sessionIdentity.collect { if (it != expectedIdentity) disconnect() }
    }

    private fun current(epoch: Long): Boolean = generation == epoch &&
        sessionIdentity.value == expectedIdentity && discordSession

    /** Called before start(), so events emitted during handshake are already observed. */
    fun bind(connection: KajutaBotRealtimeClient) {
        disconnect()
        if (!current(generation)) return
        client = connection
        _state.value = _state.value.copy(error = null, unsupported = false)
        val epoch = generation
        eventsJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            connection.localVolumeUpdates.collect { observation ->
                if (current(epoch)) {
                    eventCount++
                    applyObservation(observation)
                }
            }
        }
    }

    fun connected() {
        if (client == null || !current(generation)) return
        _state.value = _state.value.copy(connected = true)
        readJob = scope.launch { refresh(generation) }
    }

    private fun applyObservation(observation: LocalVolumeStateDto) {
        if (!observation.isValid()) {
            pending = null
            _state.value = _state.value.copy(fresh = false, draft = null, dragging = false,
                error = LocalVolumeError.FAILED)
            return
        }
        if (!observation.available) pending = null
        _state.value = _state.value.copy(
            observed = observation,
            fresh = true,
            draft = if (observation.available && (_state.value.dragging || _state.value.sending))
                _state.value.draft else null,
            dragging = observation.available && _state.value.dragging,
        )
    }

    private suspend fun refresh(epoch: Long) {
        val connection = client ?: return
        val eventsBeforeRead = eventCount
        val request = ++readCount
        try {
            val observation = withTimeout(timeoutMs) { connection.getLocalVolumeState() }
            if (current(epoch) && request == readCount && eventCount == eventsBeforeRead) {
                applyObservation(observation)
            }
        } catch (error: Exception) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            if (!current(epoch) || request != readCount) return
            val reason = localVolumeError(error)
            // A valid event received during a failed read is still authoritative.
            _state.value = _state.value.copy(error = _state.value.error ?: reason,
                unsupported = reason == LocalVolumeError.UNSUPPORTED,
                fresh = eventCount != eventsBeforeRead && _state.value.fresh)
        }
    }

    fun changeDraft(volume: Int) {
        if (!current(generation) || !_state.value.enabled) return
        if (volume !in 0..200) {
            _state.value = _state.value.copy(error = LocalVolumeError.INVALID_VOLUME)
            return
        }
        _state.value = _state.value.copy(draft = volume, dragging = true, error = null)
    }

    fun finishGesture() {
        val volume = _state.value.draft ?: return
        _state.value = _state.value.copy(dragging = false)
        if (!_state.value.sending && volume == _state.value.observed?.volume) {
            _state.value = _state.value.copy(draft = null)
            return
        }
        submit(volume)
    }

    fun cancelGesture() {
        _state.value = _state.value.copy(dragging = false,
            draft = if (_state.value.sending) _state.value.draft else null)
    }

    fun submit(volume: Int) {
        if (!current(generation)) return
        if (volume !in 0..200) {
            _state.value = _state.value.copy(error = LocalVolumeError.INVALID_VOLUME, draft = null)
            return
        }
        if (!_state.value.enabled) return
        if (_state.value.sending) {
            pending = volume
            _state.value = _state.value.copy(draft = volume)
            return
        }
        val connection = client ?: return
        val epoch = generation
        _state.value = _state.value.copy(sending = true, showSendingIndicator = false, draft = volume, error = null)
        commandJob = scope.launch {
            var next = volume
            while (current(epoch)) {
                val indicatorJob = launch {
                    delay(100L)
                    if (current(epoch)) _state.value = _state.value.copy(showSendingIndicator = true)
                }
                try {
                    withTimeout(timeoutMs) { connection.setLocalVolume(next) }
                } catch (error: Exception) {
                    indicatorJob.cancel()
                    if (error is CancellationException && error !is TimeoutCancellationException) throw error
                    if (!current(epoch)) return@launch
                    onFeedback(FeedbackEffect.Reject)
                    pending = null
                    val reason = localVolumeError(error)
                    _state.value = _state.value.copy(sending = false, showSendingIndicator = false,
                        draft = null, dragging = false,
                        error = reason, fresh = false, unsupported = reason == LocalVolumeError.UNSUPPORTED)
                    refresh(epoch) // One read; never retry a possibly applied command.
                    return@launch
                } finally {
                    indicatorJob.cancel()
                }
                if (!current(epoch)) return@launch
                _state.value = _state.value.copy(showSendingIndicator = false)
                val queued = pending
                pending = null
                if (queued != null && _state.value.enabled) next = queued
                else {
                    _state.value = _state.value.copy(sending = false,
                        draft = if (_state.value.dragging) _state.value.draft else null)
                    onFeedback(FeedbackEffect.GestureEnd)
                    return@launch
                }
            }
        }
    }

    fun disconnect() {
        generation++
        client = null
        eventsJob?.cancel()
        readJob?.cancel()
        commandJob?.cancel()
        pending = null
        _state.value = if (sessionIdentity.value != expectedIdentity) LocalVolumeUiState()
        else _state.value.copy(connected = false, fresh = false, sending = false, showSendingIndicator = false,
            draft = null, dragging = false)
    }

    fun close() {
        disconnect()
        sessionJob.cancel()
    }
}
