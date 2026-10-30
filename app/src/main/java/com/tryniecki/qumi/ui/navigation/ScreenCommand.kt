package com.tryniecki.qumi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** What the app shell can ask a tab's screen to do, from a repeated tab tap or a keyboard shortcut. */
enum class ScreenCommand { SCROLL_TO_TOP, REFRESH, FOCUS_SEARCH }

@Composable
internal fun ScreenCommandEffect(
    commands: Flow<ScreenCommand>,
    onScrollToTop: suspend () -> Unit,
    onRefresh: () -> Unit = {},
    onFocusSearch: suspend () -> Unit = {},
) {
    val scrollToTop by rememberUpdatedState(onScrollToTop)
    val refresh by rememberUpdatedState(onRefresh)
    val focusSearch by rememberUpdatedState(onFocusSearch)
    LaunchedEffect(commands) {
        commands.collectLatest { command ->
            when (command) {
                ScreenCommand.SCROLL_TO_TOP -> scrollToTop()
                ScreenCommand.REFRESH -> refresh()
                ScreenCommand.FOCUS_SEARCH -> focusSearch()
            }
        }
    }
}
