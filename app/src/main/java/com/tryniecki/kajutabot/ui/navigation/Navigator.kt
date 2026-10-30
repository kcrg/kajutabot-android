package com.tryniecki.kajutabot.ui.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** Handles navigation events by updating [NavigationState]. */
class Navigator(private val state: NavigationState) {
    private val commandEvents = MutableSharedFlow<Pair<AppDestination, ScreenCommand>>(extraBufferCapacity = 1)
    private val commandsByDestination = AppDestination.entries.associateWith { destination ->
        commandEvents.filter { (target, _) -> target == destination }.map { (_, command) -> command }
    }

    /** Requests addressed to the screen at the root of [destination]. */
    fun commands(destination: AppDestination): Flow<ScreenCommand> = commandsByDestination.getValue(destination)

    /** Sends [command] to the screen of the selected tab. */
    fun send(command: ScreenCommand) {
        commandEvents.tryEmit(state.currentDestination to command)
    }

    fun navigate(route: AppRoute) {
        val backStack = state.activeBackStack
        if (backStack.lastOrNull() != route) backStack.add(route)
    }

    fun selectTopLevel(destination: AppDestination) {
        if (state.currentDestination == destination) {
            send(ScreenCommand.SCROLL_TO_TOP)
        } else {
            state.currentDestination = destination
        }
    }

    fun openSearch() {
        state.currentDestination = AppDestination.PLAYER
        navigate(AppRoute.Search)
    }

    fun goBack() {
        val backStack = state.activeBackStack
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else if (state.currentDestination != AppDestination.PLAYER) {
            state.currentDestination = AppDestination.PLAYER
        }
    }

    /** Leaves a tab that stopped being available, such as Jellyfin after disconnecting. */
    fun leave(destination: AppDestination) {
        if (state.currentDestination == destination) state.currentDestination = AppDestination.PLAYER
    }

    fun showSharedTrack(route: AppRoute.SharedTrack) {
        state.currentDestination = AppDestination.PLAYER
        val backStack = state.activeBackStack
        while (backStack.size > 1) backStack.removeLastOrNull()
        backStack.add(route)
    }
}
