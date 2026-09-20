package com.tryniecki.kajutabot.ui.more

import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeAgeTest {
    @Test
    fun `relative age uses monotonic timestamps and clamps clock anomalies`() {
        assertEquals("brak danych", realtimeAgeLabel(null, 10_000))
        assertEquals("3 s temu", realtimeAgeLabel(7_000, 10_000))
        assertEquals("2 min temu", realtimeAgeLabel(0, 120_000))
        assertEquals("0 s temu", realtimeAgeLabel(11_000, 10_000))
    }
}
