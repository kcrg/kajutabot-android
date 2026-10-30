package com.tryniecki.qumi.ui.player

import com.tryniecki.qumi.api.model.discord.DiscordGuildResponse

/**
 * Opens the app on a saved guild list while the real one loads. Deliberately leaves the selection
 * alone: clearing or auto-selecting a guild needs the confirmed list, not a possibly stale one.
 */
internal fun PlayerUiState.withCachedGuilds(cached: List<DiscordGuildResponse>): PlayerUiState {
    val loadsGuild = cached.any { it.id == selectedGuildId }
    return copy(
        guilds = cached,
        isLoadingGuilds = false,
        guildAccessState = GuildAccessState.AVAILABLE,
        isLoadingVoiceChannels = loadsGuild,
        isLoadingQueue = loadsGuild && queue == null,
        queueLoadState = when {
            !loadsGuild -> queueLoadState
            queue != null -> QueueLoadState.READY
            else -> QueueLoadState.LOADING
        },
    )
}

/** True if a confirmed list only updates what is on screen, with nothing left to load for it. */
internal fun PlayerUiState.alreadyShowsGuild(
    guildId: String?,
    confirmed: List<DiscordGuildResponse>,
): Boolean = guildId != null &&
    guilds.any { it.id == guildId } &&
    confirmed.any { it.id == guildId } &&
    (isLoadingVoiceChannels || voiceChannels.isNotEmpty())
