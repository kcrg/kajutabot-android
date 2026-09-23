package com.tryniecki.kajutabot.ui.more

import com.tryniecki.kajutabot.R
import com.tryniecki.kajutabot.ui.text.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class RealtimeAgeTest {
    @Test
    fun `relative age uses monotonic timestamps and clamps clock anomalies`() {
        assertResource(realtimeAgeText(null, 10_000), R.string.realtime_no_data)
        assertResource(realtimeAgeText(7_000, 10_000), R.string.realtime_seconds_ago, 3L)
        assertResource(realtimeAgeText(0, 120_000), R.string.realtime_minutes_ago, 2L)
        assertResource(realtimeAgeText(11_000, 10_000), R.string.realtime_seconds_ago, 0L)
    }

    private fun assertResource(text: UiText, expectedId: Int, vararg expectedArgs: Any) {
        val resource = text as UiText.Resource
        assertEquals(expectedId, resource.id)
        assertEquals(expectedArgs.toList(), resource.args)
    }
}
