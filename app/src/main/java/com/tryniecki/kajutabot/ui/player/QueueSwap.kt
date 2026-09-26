package com.tryniecki.kajutabot.ui.player

import com.tryniecki.kajutabot.api.model.queue.QueueEntryResponse
import java.util.Collections

/** Optimistic preview of the API's atomic swap. */
internal fun swappedQueueEntries(
    entries: List<QueueEntryResponse>,
    sourceEntryId: String,
    targetEntryId: String,
): List<QueueEntryResponse>? {
    val sourceIndex = entries.indexOfFirst { it.entryId == sourceEntryId }
    val targetIndex = entries.indexOfFirst { it.entryId == targetEntryId }
    if (sourceIndex < 0 || targetIndex < 0 || sourceIndex == targetIndex) return null

    return entries.toMutableList()
        .apply { Collections.swap(this, sourceIndex, targetIndex) }
        .mapIndexed { index, entry -> entry.copy(position = index + 1) }
}
