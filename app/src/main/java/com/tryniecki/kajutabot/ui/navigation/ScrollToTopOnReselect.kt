package com.tryniecki.kajutabot.ui.navigation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun ScrollToTopOnReselect(listState: LazyListState, reselected: Flow<Unit>) {
    LaunchedEffect(listState, reselected) {
        reselected.collectLatest { listState.animateScrollToItem(0) }
    }
}
