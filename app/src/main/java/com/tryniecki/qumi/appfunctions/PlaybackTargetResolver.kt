package com.tryniecki.qumi.appfunctions

import androidx.appfunctions.AppFunctionDeniedException
import androidx.appfunctions.AppFunctionElementNotFoundException
import com.tryniecki.qumi.data.preferences.GuildSelection
import com.tryniecki.qumi.data.repository.PlayerRepository

internal data class PlaybackTarget(
    val identity: Long,
    val guildId: String,
    val selectedChannelId: String?,
)

/** Finds the session and the server that a command acts on when no screen has loaded them. */
internal class PlaybackTargetResolver(
    private val player: PlayerRepository,
    private val guildSelection: suspend () -> GuildSelection,
    private val restoreSession: suspend () -> Unit,
) {
    suspend fun sessionIdentity(): Long {
        // The system can start this process without any Activity, before the app restores it.
        if (player.sessionIdentity.value == null) restoreSession()
        return player.sessionIdentity.value ?: throw AppFunctionDeniedException("Not signed in to Qumi.")
    }

    suspend fun target(): PlaybackTarget {
        val identity = sessionIdentity()
        val selection = guildSelection()
        val guildId = selection.guildId
            ?: throw AppFunctionElementNotFoundException("No server is selected in Qumi.")
        return PlaybackTarget(identity, guildId, selection.voiceChannelId)
    }

    fun missingVoiceChannel() =
        AppFunctionElementNotFoundException("No voice channel is selected in Qumi.")
}
