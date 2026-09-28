package com.tryniecki.kajutabot.ui.components

/** Keeps a removed row visible briefly so its swipe result can finish rendering. */
data class RetainedSwipeItem<T>(
    val item: T,
    val position: Int,
    val scopeId: String? = null,
)

fun <T> visibleSwipeItems(
    items: List<T>,
    retainedItems: Collection<RetainedSwipeItem<T>>,
    scopeId: String? = null,
    identity: (T) -> String,
): List<T> {
    if (retainedItems.isEmpty()) return items
    val visible = items.toMutableList()
    val visibleIds = items.mapTo(mutableSetOf(), identity)
    retainedItems.asSequence()
        .filter { it.scopeId == scopeId }
        .sortedBy { it.position }
        .forEach { retained ->
            if (visibleIds.add(identity(retained.item))) {
                visible.add(retained.position.coerceIn(0, visible.size), retained.item)
            }
        }
    return visible
}
