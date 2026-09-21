package com.tryniecki.kajutabot.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryPreferencesTest {
    @Test
    fun `newest query is first and history is capped at five`() {
        val history = listOf("one", "two", "three", "four", "five")

        assertEquals(
            listOf("six", "one", "two", "three", "four"),
            updateSearchHistory(history, "six"),
        )
    }

    @Test
    fun `repeated query moves to front without duplicates`() {
        val history = listOf("one", "two", "three")

        assertEquals(
            listOf("TWO", "one", "three"),
            updateSearchHistory(history, "  TWO  "),
        )
    }

    @Test
    fun `blank query is not added`() {
        val history = listOf("one", "two")

        assertEquals(history, updateSearchHistory(history, "   "))
    }
}
