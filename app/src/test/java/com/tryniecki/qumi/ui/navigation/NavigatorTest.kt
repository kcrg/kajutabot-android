package com.tryniecki.qumi.ui.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigatorTest {
    private val state = NavigationState(
        mutableStateOf(AppDestination.PLAYER),
        AppDestination.entries.associateWith { NavBackStack<NavKey>(it.route) },
    )
    private val navigator = Navigator(state)

    @Test fun `routes open on the selected tab without stacking duplicates`() {
        navigator.navigate(AppRoute.Search)
        navigator.navigate(AppRoute.Search)

        assertEquals(listOf<NavKey>(AppRoute.Player, AppRoute.Search), state.backStacks[AppDestination.PLAYER]?.toList())
        assertEquals(AppRoute.Search, state.currentRoute)
    }

    @Test fun `back pops the stack first and then exits a tab through Player`() {
        navigator.selectTopLevel(AppDestination.HISTORY)
        navigator.navigate(AppRoute.Search)

        navigator.goBack()
        assertEquals(AppRoute.History, state.currentRoute)

        navigator.goBack()
        assertEquals(AppDestination.PLAYER, state.currentDestination)
    }

    @Test fun `switching tabs keeps each tab's stack`() {
        navigator.navigate(AppRoute.Search)
        navigator.selectTopLevel(AppDestination.MORE)
        navigator.selectTopLevel(AppDestination.PLAYER)

        assertEquals(AppRoute.Search, state.currentRoute)
    }

    @Test fun `tapping the selected tab reports a reselect only for that tab`() = runTest(UnconfinedTestDispatcher()) {
        val favorites = mutableListOf<ScreenCommand>()
        val history = mutableListOf<ScreenCommand>()
        val jobs = listOf(
            launch { navigator.commands(AppDestination.FAVORITES).toList(favorites) },
            launch { navigator.commands(AppDestination.HISTORY).toList(history) },
        )

        navigator.selectTopLevel(AppDestination.FAVORITES)
        assertEquals(0, favorites.size)

        navigator.selectTopLevel(AppDestination.FAVORITES)
        assertEquals(listOf(ScreenCommand.SCROLL_TO_TOP), favorites)
        assertEquals(0, history.size)
        jobs.forEach { it.cancel() }
    }

    @Test fun `search opens over Player from any tab`() {
        navigator.selectTopLevel(AppDestination.MORE)

        navigator.openSearch()

        assertEquals(AppDestination.PLAYER, state.currentDestination)
        assertEquals(AppRoute.Search, state.currentRoute)
    }

    @Test fun `leaving falls back to Player only from that tab`() {
        navigator.selectTopLevel(AppDestination.JELLYFIN)
        navigator.leave(AppDestination.FAVORITES)
        assertEquals(AppDestination.JELLYFIN, state.currentDestination)

        navigator.leave(AppDestination.JELLYFIN)
        assertEquals(AppDestination.PLAYER, state.currentDestination)
    }

    @Test fun `shared track replaces whatever Player was showing`() {
        navigator.navigate(AppRoute.Search)
        navigator.selectTopLevel(AppDestination.MORE)
        val route = AppRoute.SharedTrack(1, "https://example.com")

        navigator.showSharedTrack(route)

        assertEquals(AppDestination.PLAYER, state.currentDestination)
        assertEquals(listOf<NavKey>(AppRoute.Player, route), state.backStacks[AppDestination.PLAYER]?.toList())
    }
}
