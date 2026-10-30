package com.tryniecki.kajutabot.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.tryniecki.kajutabot.R
import kotlinx.serialization.Serializable

sealed interface AppRoute : NavKey {
    @Serializable data object Player : AppRoute
    @Serializable data object Jellyfin : AppRoute
    @Serializable data object Favorites : AppRoute
    @Serializable data object History : AppRoute
    @Serializable data object More : AppRoute
    @Serializable data object Search : AppRoute
    @Serializable data class SharedTrack(val requestId: Long, val url: String) : AppRoute
    @Serializable data object DiscordSelection : AppRoute
}

internal fun AppRoute.hidesBottomNavigation(): Boolean =
    this == AppRoute.Search || this is AppRoute.SharedTrack || this == AppRoute.DiscordSelection

enum class AppDestination(
    @StringRes val labelResId: Int,
    @DrawableRes val icon: Int,
    val route: AppRoute,
) {
    PLAYER(R.string.nav_player, com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_outline, AppRoute.Player),
    JELLYFIN(R.string.nav_jellyfin, com.composables.icons.tabler.outline.R.drawable.tabler_ic_music_outline, AppRoute.Jellyfin),
    FAVORITES(R.string.nav_favorites, com.composables.icons.tabler.outline.R.drawable.tabler_ic_hearts_outline, AppRoute.Favorites),
    HISTORY(R.string.nav_history, com.composables.icons.tabler.outline.R.drawable.tabler_ic_history_outline, AppRoute.History),
    MORE(R.string.nav_more, com.composables.icons.tabler.outline.R.drawable.tabler_ic_dots_outline, AppRoute.More),
}
