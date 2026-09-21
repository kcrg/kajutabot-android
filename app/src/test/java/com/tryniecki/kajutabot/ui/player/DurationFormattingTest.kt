package com.tryniecki.kajutabot.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormattingTest {

    @Test
    fun `formats duration below one hour as minutes and seconds`() {
        assertEquals("3:07", formatDuration(187_000L))
        assertEquals("59:59", formatDuration(3_599_000L))
    }

    @Test
    fun `formats duration of at least one hour with hours`() {
        assertEquals("1:00:00", formatDuration(3_600_000L))
        assertEquals("1:05:09", formatDuration(3_909_000L))
        assertEquals("12:34:56", formatDuration(45_296_000L))
    }

    @Test
    fun `keeps non-positive duration placeholder`() {
        assertEquals("—", formatDuration(0L))
        assertEquals("—", formatDuration(-1L))
    }
}
