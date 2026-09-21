package com.tryniecki.kajutabot.prefs

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SearchHistoryPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun entries(): List<String> = prefs.getString(KEY_ENTRIES, null)
        ?.let { encoded -> runCatching { Json.decodeFromString<List<String>>(encoded) }.getOrNull() }
        ?.take(MAX_SEARCH_HISTORY_ENTRIES)
        .orEmpty()

    fun add(query: String): List<String> {
        val updated = updateSearchHistory(entries(), query)
        prefs.edit()
            .putString(KEY_ENTRIES, Json.encodeToString(updated))
            .apply()
        return updated
    }

    private companion object {
        const val PREFERENCES_NAME = "kajutabot_search_history"
        const val KEY_ENTRIES = "entries"
    }
}

internal const val MAX_SEARCH_HISTORY_ENTRIES = 5

internal fun updateSearchHistory(
    current: List<String>,
    query: String,
    maxEntries: Int = MAX_SEARCH_HISTORY_ENTRIES,
): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty() || maxEntries <= 0) return current.take(maxEntries.coerceAtLeast(0))

    return buildList(maxEntries) {
        add(trimmed)
        current.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { it.equals(trimmed, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(maxEntries - 1)
            .forEach(::add)
    }
}
