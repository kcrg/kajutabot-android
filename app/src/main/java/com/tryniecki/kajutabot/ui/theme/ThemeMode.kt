package com.tryniecki.kajutabot.ui.theme

import androidx.annotation.StringRes
import com.tryniecki.kajutabot.R

enum class ThemeMode(@StringRes val labelResId: Int) {
    NATIVE(R.string.theme_system),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark),
}
