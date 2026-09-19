package com.tryniecki.kajutabot.ui.navigation

import androidx.annotation.DrawableRes
import com.tryniecki.kajutabot.R

enum class AppDestination(
    val label: String,
    @DrawableRes val icon: Int,
) {
    PLAYER("Odtwarzacz", com.composables.icons.tabler.outline.R.drawable.tabler_ic_playlist_outline),
    MY_AUDIO("Moje Audio", com.composables.icons.tabler.outline.R.drawable.tabler_ic_music_outline),
    FAVORITES("Ulubione", com.composables.icons.tabler.outline.R.drawable.tabler_ic_hearts_outline),
    MORE("Więcej", com.composables.icons.tabler.outline.R.drawable.tabler_ic_dots_outline),
}

enum class MoreDestination {
    ROOT,
    LIBRARIES,
    CONTACT,
}
