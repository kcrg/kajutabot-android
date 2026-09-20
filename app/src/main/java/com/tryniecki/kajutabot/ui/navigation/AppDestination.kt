package com.tryniecki.kajutabot.ui.navigation

import androidx.annotation.DrawableRes
import com.tryniecki.kajutabot.api.model.auth.SessionType
import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable data object Player : AppRoute
    @Serializable data object MyAudio : AppRoute
    @Serializable data object Favorites : AppRoute
    @Serializable data object More : AppRoute
    @Serializable data object Libraries : AppRoute
    @Serializable data object Contact : AppRoute
    @Serializable data object AddTrack : AppRoute
    @Serializable data object DiscordSelection : AppRoute
}

enum class AppDestination(
    val label: String,
    @DrawableRes val icon: Int,
    val route: AppRoute,
) {
    PLAYER("Odtwarzacz", com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_outline, AppRoute.Player),
    //MY_AUDIO("Moje Audio", com.composables.icons.tabler.outline.R.drawable.tabler_ic_music_outline, AppRoute.MyAudio),
    FAVORITES("Ulubione", com.composables.icons.tabler.outline.R.drawable.tabler_ic_hearts_outline, AppRoute.Favorites),
    MORE("Więcej", com.composables.icons.tabler.outline.R.drawable.tabler_ic_dots_outline, AppRoute.More),
}

internal fun visibleDestinations(sessionType: SessionType): List<AppDestination> =
    AppDestination.entries.filter { it.route != AppRoute.MyAudio || sessionType.canUseUserMedia }

internal val SessionType.canUseUserMedia: Boolean get() = this == SessionType.DISCORD
