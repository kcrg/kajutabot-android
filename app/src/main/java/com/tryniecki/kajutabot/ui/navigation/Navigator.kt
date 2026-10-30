package com.tryniecki.kajutabot.ui.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** Handles navigation events by updating [NavigationState]. */
class Navigator(private val state: NavigationState) {
    private val reselectEvents = MutableSharedFlow<AppDestination>(extraBufferCapacity = 1)
    private val reselectedByDestination = AppDestination.entries.associateWith { destination ->
        reselectEvents.filter { it == destination }.map { }
    }

    /** Emits when the selected tab is tapped again. */
    fun reselected(destination: AppDestination): Flow<Unit> = reselectedByDestination.getValue(destination)

    fun navigate(route: AppRoute) {
        val backStack = state.activeBackStack
        if (backStack.lastOrNull() != route) backStack.add(route)
    }

    fun selectTopLevel(destination: AppDestination) {
        if (state.currentDestination == destination) {
            reselectEvents.tryEmit(destination)
        } else {
            state.currentDestination = destination
        }
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
