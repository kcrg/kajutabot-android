package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.model.common.PlaybackTrackResponse
import com.tryniecki.kajutabot.api.model.queue.QueueEntryResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueSwapTest {
    private val entries = listOf("A", "B", "C").mapIndexed { index, id ->
        QueueEntryResponse(
            entryId = id,
            position = index + 1,
            track = PlaybackTrackResponse(id, "youtube", id, "https://example.com/$id", 1L, null, 0L),
        )
    }

    @Test
    fun `dropping first entry on third swaps only those entries`() {
        val swapped = swappedQueueEntries(entries, "A", "C")!!

        assertEquals(listOf("C", "B", "A"), swapped.map { it.entryId })
        assertEquals(listOf(1, 2, 3), swapped.map { it.position })
    }

    @Test
    fun `reverse and adjacent swaps preserve other entries`() {
        assertEquals(listOf("C", "B", "A"), swappedQueueEntries(entries, "C", "A")!!.map { it.entryId })
        assertEquals(listOf("B", "A", "C"), swappedQueueEntries(entries, "A", "B")!!.map { it.entryId })
        assertNull(swappedQueueEntries(entries, "A", "A"))
        assertNull(swappedQueueEntries(entries, "A", "missing"))
    }
}
