package com.tryniecki.kajutabot.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/** One back stack per tab and the selected tab. Changed only through [Navigator]. */
class NavigationState(
    currentDestination: MutableState<AppDestination>,
    val backStacks: Map<AppDestination, NavBackStack<NavKey>>,
) {
    var currentDestination: AppDestination by currentDestination

    val activeBackStack: NavBackStack<NavKey>
        get() = backStacks.getValue(currentDestination)

    val currentRoute: AppRoute
        get() = activeBackStack.lastOrNull() as? AppRoute ?: AppRoute.Player

    /**
     * Each stack owns its state holder, so a tab keeps its saveable state while hidden. ViewModels
     * are deliberately not scoped to entries: they belong to the session and outlive the tabs.
     */
    @Composable
    fun toDecoratedEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val decoratedEntries = backStacks.mapValues { (_, backStack) ->
            rememberDecoratedNavEntries(
                backStack = backStack,
                entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator<NavKey>()),
                entryProvider = entryProvider,
            )
        }
        // Player stays underneath the selected tab: the app exits through it, and Back and
        // predictive Back get a real previous scene.
        val startEntries = decoratedEntries.getValue(AppDestination.PLAYER)
        return if (currentDestination == AppDestination.PLAYER) {
            startEntries
        } else {
            startEntries + decoratedEntries.getValue(currentDestination)
        }
    }
}

@Composable
fun rememberNavigationState(): NavigationState {
    val currentDestination = rememberSaveable { mutableStateOf(AppDestination.PLAYER) }
    val backStacks = AppDestination.entries.associateWith { destination ->
        key(destination.name) { rememberNavBackStack(destination.route) }
    }
    return remember(currentDestination, backStacks) { NavigationState(currentDestination, backStacks) }
}
