package com.shawnkowalchuk.milo.feature.eventlog

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the event log screen cuts what it read down to what it shows. */
class EventLogPageTest {
    private fun entries(count: Int): List<EventLogEntry> = (count downTo 1).map { number ->
        EventLogEntry(
            id = number.toLong(),
            atMs = number * 1_000L,
            category = EventCategory.TRIGGER,
            message = "entry $number",
        )
    }

    @Test
    fun `a log shorter than the page is shown whole, with nothing older to ask for`() {
        val page = eventLogPage(entries(3), wanted = 200)

        assertEquals(3, page.entries.size)
        assertFalse(page.hasOlder)
    }

    @Test
    fun `a log of exactly one page has nothing older`() {
        val page = eventLogPage(entries(200), wanted = 200)

        assertEquals(200, page.entries.size)
        assertFalse(page.hasOlder)
    }

    @Test
    fun `the one extra entry that was read says there are older ones, and is not shown`() {
        val page = eventLogPage(entries(201), wanted = 200)

        assertEquals(200, page.entries.size)
        // The newest are kept: the extra, oldest entry is the one left out.
        assertEquals(201L, page.entries.first().id)
        assertEquals(2L, page.entries.last().id)
        assertTrue(page.hasOlder)
    }
}
