package com.tryniecki.kajutabot.ui.navigation

import androidx.annotation.DrawableRes
import com.tryniecki.kajutabot.R

enum class AppDestination(
    val label: String,
    @DrawableRes val icon: Int,
) {
    PLAYER("Odtwarzacz", R.drawable.kb_ic_player_play),
    MY_AUDIO("Moje Audio", R.drawable.kb_ic_music),
    FAVORITES("Ulubione", R.drawable.kb_ic_heart),
    MORE("Więcej", R.drawable.kb_ic_dots),
}

enum class MoreDestination {
    ROOT,
    LIBRARIES,
    CONTACT,
}
