package com.tryniecki.qumi.ui.player

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPanesTest {
    @Test fun `a phone keeps one column`() {
        assertEquals(PlayerPaneMode.SINGLE, playerPaneMode(411.dp, isTabletop = false))
    }

    @Test fun `what is playing moves beside the queue once both fit`() {
        assertEquals(PlayerPaneMode.SINGLE, playerPaneMode(719.dp, isTabletop = false))
        assertEquals(PlayerPaneMode.SIDE_BY_SIDE, playerPaneMode(720.dp, isTabletop = false))
    }

    @Test fun `what is playing keeps room for its controls on an unfolded phone`() {
        assertEquals(392.dp, nowPlayingPaneWidth(756.dp))
    }

    @Test fun `what is playing grows with a wide window`() {
        assertEquals(504f, nowPlayingPaneWidth(1200.dp).value, 0.01f)
    }

    @Test fun `a half-open foldable splits at the fold at any width`() {
        assertEquals(PlayerPaneMode.TABLETOP, playerPaneMode(411.dp, isTabletop = true))
        assertEquals(PlayerPaneMode.TABLETOP, playerPaneMode(900.dp, isTabletop = true))
    }
}
