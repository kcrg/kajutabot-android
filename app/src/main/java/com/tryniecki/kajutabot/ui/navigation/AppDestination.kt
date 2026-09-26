package com.tryniecki.kajutabot.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.tryniecki.kajutabot.R
import kotlinx.serialization.Serializable

sealed interface AppRoute : NavKey {
    @Serializable data object Player : AppRoute
    @Serializable data object Favorites : AppRoute
    @Serializable data object More : AppRoute
    @Serializable data object Libraries : AppRoute
    @Serializable data object Contact : AppRoute
    @Serializable data object AddTrack : AppRoute
    @Serializable data object DiscordSelection : AppRoute
}

enum class AppDestination(
    @StringRes val labelResId: Int,
    @DrawableRes val icon: Int,
    val route: AppRoute,
) {
    PLAYER(R.string.nav_player, com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_outline, AppRoute.Player),
    FAVORITES(R.string.nav_favorites, com.composables.icons.tabler.outline.R.drawable.tabler_ic_hearts_outline, AppRoute.Favorites),
    MORE(R.string.nav_more, com.composables.icons.tabler.outline.R.drawable.tabler_ic_dots_outline, AppRoute.More),
}
