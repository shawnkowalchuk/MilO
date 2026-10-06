package com.shawnkowalchuk.milo.data.eventlog

import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val DAY_MS = 24 * 60 * 60 * 1000L
private const val NOW_MS = 1_791_300_000_000L

/**
 * The event log's housekeeping against the stand-in table: which lines are trimmed, that the
 * whole log is read in order and in pieces, and the filter by category.
 */
class EventLogRepositoryTest {
    private val dao = FakeEventLogDao()
    private val log = EventLogRepository(dao)

    /** Adds [count] lines, a second apart, the newest of them [newestAgeDays] days old. */
    private suspend fun addLines(count: Int, newestAgeDays: Int, category: EventCategory) {
        val newestAtMs = NOW_MS - newestAgeDays * DAY_MS
        repeat(count) { i ->
            log.add(newestAtMs - (count - 1 - i) * 1_000L, category, "line $i of $category")
        }
    }

    // ---- The rule ---------------------------------------------------------------------------------

    @Test
    fun `nothing is trimmed from a log that is no longer than the lines always kept`() {
        assertNull(trimBeforeMs(NOW_MS, oldestKeptAtMs = null))
    }

    @Test
    fun `a line goes when it is older than 90 days and not among the newest thousand`() {
        val ninetyDaysAgo = NOW_MS - EVENT_LOG_KEEP_DAYS * DAY_MS

        // The newest thousand reach back only a week: the 90 days decide.
        assertEquals(ninetyDaysAgo, trimBeforeMs(NOW_MS, oldestKeptAtMs = NOW_MS - 7 * DAY_MS))
        // The newest thousand reach back a year: they are kept, every one of them.
        val aYearAgo = NOW_MS - 365 * DAY_MS
        assertEquals(aYearAgo, trimBeforeMs(NOW_MS, oldestKeptAtMs = aYearAgo))
    }

    @Test
    fun `a clock that is far ahead cannot empty the log`() {
        // The phone claims it is ten years later. The newest thousand lines stay all the same.
        val wrongNow = NOW_MS + 3_650 * DAY_MS
        val oldestKept = NOW_MS - 30 * DAY_MS

        assertEquals(oldestKept, trimBeforeMs(wrongNow, oldestKeptAtMs = oldestKept))
    }

    // ---- Against the table ------------------------------------------------------------------------

    @Test
    fun `lines older than 90 days are removed, and newer ones are not`() = runTest {
        addLines(count = 300, newestAgeDays = 120, category = EventCategory.TRIGGER)
        addLines(count = 1_200, newestAgeDays = 0, category = EventCategory.TRIP)

        val removed = log.trim(NOW_MS)

        assertEquals(300, removed)
        assertEquals(1_200, log.count())
        assertEquals(setOf(EventCategory.TRIP), dao.entries.map { it.category }.toSet())
    }

    @Test
    fun `a line exactly 90 days old is kept, and one a millisecond older is not`() = runTest {
        val edge = NOW_MS - EVENT_LOG_KEEP_DAYS * DAY_MS
        log.add(edge - 1, EventCategory.TRIP, "a millisecond too old")
        log.add(edge, EventCategory.TRIP, "exactly 90 days old")
        addLines(count = EVENT_LOG_KEEP_NEWEST, newestAgeDays = 0, category = EventCategory.TRIGGER)

        assertEquals(1, log.trim(NOW_MS))

        assertEquals(1, dao.entries.count { it.message == "exactly 90 days old" })
        assertEquals(0, dao.entries.count { it.message == "a millisecond too old" })
    }

    @Test
    fun `the newest thousand lines are kept however old they are`() = runTest {
        // A MilO that was not opened for half a year: everything it holds is old.
        addLines(count = 1_400, newestAgeDays = 180, category = EventCategory.TRIP)

        val removed = log.trim(NOW_MS)

        assertEquals(400, removed)
        assertEquals(EVENT_LOG_KEEP_NEWEST, log.count())
        // The ones that stayed are the newest.
        assertEquals("line 400 of TRIP", dao.entries.minBy { it.atMs }.message)
    }

    @Test
    fun `a short log is never trimmed, and a second trim removes nothing more`() = runTest {
        addLines(count = 50, newestAgeDays = 400, category = EventCategory.TRIP)
        assertEquals(0, log.trim(NOW_MS))

        addLines(count = 2_000, newestAgeDays = 100, category = EventCategory.TRIGGER)
        assertEquals(1_050, log.trim(NOW_MS))
        assertEquals(0, log.trim(NOW_MS))
        assertEquals(EVENT_LOG_KEEP_NEWEST, log.count())
    }

    // ---- Reading ----------------------------------------------------------------------------------

    @Test
    fun `the whole log is read oldest first, in pieces, with no line twice and none left out`() =
        runTest {
            addLines(count = 1_234, newestAgeDays = 0, category = EventCategory.TRIP)
            // Two lines of the very same time: the order between them is the order written.
            log.add(NOW_MS + 1, EventCategory.ERROR, "first of two")
            log.add(NOW_MS + 1, EventCategory.ERROR, "second of two")

            val pages = mutableListOf<List<EventLogEntry>>()
            log.readAll { pages += it }

            val read = pages.flatten()
            assertEquals(1_236, read.size)
            assertEquals(read.size, read.map { it.id }.toSet().size)
            assertEquals(read.sortedWith(compareBy({ it.atMs }, { it.id })), read)
            assertEquals(
                listOf("first of two", "second of two"),
                read.takeLast(2).map {
                    it.message
                },
            )
            // Never all at once.
            assertEquals(3, pages.size)
        }

    @Test
    fun `an empty log is read as nothing`() = runTest {
        var pages = 0

        log.readAll { pages++ }

        assertEquals(0, pages)
    }

    @Test
    fun `narrowed to a category only its lines are read, newest first`() = runTest {
        addLines(count = 5, newestAgeDays = 3, category = EventCategory.TRIP)
        addLines(count = 400, newestAgeDays = 0, category = EventCategory.TRIGGER)

        val trips = log.observeNewest(limit = 201, category = EventCategory.TRIP).first()
        val all = log.observeNewest(limit = 201).first()

        // The five TRIP lines are older than every one of the newest 201 lines, and are found.
        assertEquals(5, trips.size)
        assertEquals("line 4 of TRIP", trips.first().message)
        assertEquals(201, all.size)
        assertEquals(setOf(EventCategory.TRIGGER), all.map { it.category }.toSet())
    }
}
