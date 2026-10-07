package com.shawnkowalchuk.milo.feature.eventlog

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the Log screen lists its lines by day, and how it writes a time and a day. */
class LogListTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val today = LocalDate.of(2026, 10, 6)
    private var nextId = 100L

    private fun at(day: LocalDate, hour: Int, minute: Int, second: Int = 0): EventLogEntry {
        val atMs =
            LocalDateTime
                .of(day, LocalTime.of(hour, minute, second))
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        return EventLogEntry(nextId--, atMs, EventCategory.TRIP, "line")
    }

    /** Each day as its label, or "today" for the one that has none, and its rows' places. */
    private fun List<LogDay>.shape(): List<String> = map { day ->
        val name = if (day.isToday) "today" else day.date.toString()
        name + ": " + day.lines.joinToString(" ") { it.place.name }
    }

    @Test
    fun `an empty log lists nothing`() {
        assertTrue(logDays(emptyList(), zone, today).isEmpty())
    }

    @Test
    fun `today's lines are one tile with no day above it`() {
        val entries = listOf(at(today, 17, 41), at(today, 17, 39), at(today, 6, 58))

        assertEquals(listOf("today: FIRST MIDDLE LAST"), logDays(entries, zone, today).shape())
    }

    @Test
    fun `every earlier day is named above its own tile`() {
        val yesterday = today.minusDays(1)
        val monday = today.minusDays(8)
        val entries =
            listOf(
                at(today, 9, 0),
                at(today, 8, 0),
                at(yesterday, 23, 59, 59),
                at(monday, 12, 0),
                at(monday, 11, 0),
                at(monday, 10, 0),
            )

        assertEquals(
            listOf("today: FIRST LAST", "2026-10-05: ONLY", "2026-09-28: FIRST MIDDLE LAST"),
            logDays(entries, zone, today).shape(),
        )
    }

    @Test
    fun `a log whose newest line is not from today starts with that line's day`() {
        val days = logDays(listOf(at(today.minusDays(3), 14, 0)), zone, today)

        assertEquals(listOf("2026-10-03: ONLY"), days.shape())
    }

    @Test
    fun `a line dated after today is named too`() {
        // The phone's clock was a day ahead when the line was written.
        val days = logDays(listOf(at(today.plusDays(1), 1, 0), at(today, 23, 0)), zone, today)

        assertEquals(listOf("2026-10-07: ONLY", "today: ONLY"), days.shape())
    }

    @Test
    fun `a day is the phone's own day, not the day in another time zone`() {
        // 23:30 in Edmonton is already the next day in UTC.
        val late = at(today, 23, 30)

        assertEquals(listOf("today: ONLY"), logDays(listOf(late), zone, today).shape())
        assertEquals(
            listOf("2026-10-07: ONLY"),
            logDays(listOf(late), ZoneId.of("UTC"), today).shape(),
        )
    }

    @Test
    fun `the lines keep the order they were read in, and none is lost`() {
        val entries = (0 until 30).map { at(today.minusDays(it / 7L), 12, 59 - it) }

        val lines = logDays(entries, zone, today).flatMap { it.lines }

        assertEquals(entries, lines.map { it.entry })
    }

    @Test
    fun `a time is written to the second with 24 hours, and a day with its weekday`() {
        val entry = at(today, 17, 4, 9)

        assertEquals("17:04:09", formatLogClock(entry.atMs, zone))
        assertEquals("Tue 2026-10-06", formatLogDay(today))
        assertEquals("Thu 2026-01-01", formatLogDay(LocalDate.of(2026, 1, 1)))
    }
}
