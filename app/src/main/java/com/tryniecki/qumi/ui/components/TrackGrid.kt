package com.tryniecki.qumi.ui.components

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.ui.unit.dp

/** Track lists gain columns as the window widens; a phone in portrait keeps a single one. */
internal val TrackGridCells = GridCells.Adaptive(minSize = 360.dp)

/** Headers, errors and footers span the whole row. */
internal val fullLineSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
