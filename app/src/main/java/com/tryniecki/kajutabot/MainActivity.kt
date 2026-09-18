package com.tryniecki.kajutabot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tryniecki.kajutabot.ui.KajutaBotApp
import com.tryniecki.kajutabot.ui.theme.KajutaBotTheme
import com.tryniecki.kajutabot.ui.theme.ThemePreferences

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val themePreferences = ThemePreferences(this)

        setContent {
            var themeMode by remember {
                mutableStateOf(themePreferences.themeMode)
            }

            KajutaBotTheme(themeMode = themeMode) {
                KajutaBotApp(
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        themePreferences.themeMode = mode
                    },
                )
            }
        }
    }
}
