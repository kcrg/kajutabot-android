package com.tryniecki.kajutabot.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueDragAutoScrollTest {
    @Test
    fun `scroll delta grows as pointer approaches either edge`() {
        assertEquals(-18f, queueDragAutoScrollDelta(0f, 600f, 72f, 18f))
        assertEquals(-9f, queueDragAutoScrollDelta(36f, 600f, 72f, 18f))
        assertEquals(0f, queueDragAutoScrollDelta(300f, 600f, 72f, 18f))
        assertEquals(9f, queueDragAutoScrollDelta(564f, 600f, 72f, 18f))
        assertEquals(18f, queueDragAutoScrollDelta(600f, 600f, 72f, 18f))
    }
}
