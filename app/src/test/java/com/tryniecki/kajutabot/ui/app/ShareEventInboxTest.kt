package com.tryniecki.kajutabot.ui.app

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ShareEventInboxTest {
    @Test fun `each intent is consumed once and same URL can be shared again`() = runBlocking {
        val inbox = ShareEventInbox()
        inbox.offer("https://example.test/track")
        val first = inbox.events.first()
        assertEquals(SharedUrlEvent(1, "https://example.test/track"), first)
        inbox.offer("https://example.test/track")
        val second = inbox.events.first()
        assertEquals(SharedUrlEvent(2, "https://example.test/track"), second)
    }

    @Test fun `rapid intents preserve delivery order`() = runBlocking {
        val inbox = ShareEventInbox()
        inbox.offer("https://example.test/a")
        inbox.offer("https://example.test/b")
        assertEquals("https://example.test/a", inbox.events.first().url)
        assertEquals("https://example.test/b", inbox.events.first().url)
    }
}
