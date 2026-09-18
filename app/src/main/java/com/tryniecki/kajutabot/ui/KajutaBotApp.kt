package com.tryniecki.kajutabot.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.tryniecki.kajutabot.ui.favorites.FavoritesScreen
import com.tryniecki.kajutabot.ui.more.MoreScreen
import com.tryniecki.kajutabot.ui.myaudio.MyAudioScreen
import com.tryniecki.kajutabot.ui.navigation.AppDestination
import com.tryniecki.kajutabot.ui.player.PlayerScreen
import com.tryniecki.kajutabot.ui.theme.ThemeMode

@Composable
fun KajutaBotApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    var currentDestination by rememberSaveable {
        mutableStateOf(AppDestination.PLAYER)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination == destination,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(
                                painter = painterResource(destination.icon),
                                contentDescription = destination.label,
                            )
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (currentDestination) {
            AppDestination.PLAYER -> PlayerScreen()
            AppDestination.MY_AUDIO -> MyAudioScreen()
            AppDestination.FAVORITES -> FavoritesScreen()
            AppDestination.MORE -> MoreScreen(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
        }
    }
}
