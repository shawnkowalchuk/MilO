package com.shawnkowalchuk.milo.feature.eventlog

import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.eventlog.eventLogFileLines
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Where the lines that are written while the phone's date is one day ahead stand on the Log
 * screen and in the shared log file (ADR-005).
 *
 * Two kinds of line, and the tests keep them apart:
 * - **Lines MilO writes now** are dated by its own clock ([JumpingPhone]), also in the seconds
 *   ahead, and stand under today in the order they were written.
 * - **Lines the build before this one wrote** in such seconds (rows 717 to 728 of the phone's
 *   log of 2026-10-07, shortened here) are dated tomorrow for good. They are not changed, and
 *   the first two tests are the investigation's own, kept as written but for one message cut
 *   to fit its line: this is what is on the phone.
 *
 * The log is read by time and then by id (`EventLogDao`), and the stand-in table sorts the same
 * way, so the order below is the order the screen and the file get.
 */
class LogListClockJumpTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val wednesday = LocalDate.of(2026, 10, 7)
    private val thursday = LocalDate.of(2026, 10, 8)
    private val table = FakeEventLogDao()
    private val log = EventLogRepository(table)

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun write(time: String, message: String) = runBlocking {
        log.add(at(time), EventCategory.TRIP, message)
    }

    /** The evening of Wednesday 7 October, as the build before this one wrote it. */
    private fun theEveningAsTheOldBuildWroteIt() {
        write("2026-10-07T17:52:32", "Trip 15: finished")
        write("2026-10-07T18:23:13", "check (app opened): 10 trips today, Wed 2026-10-07")
        // The date is set ahead: the two daily alarms arrive, a day in the future.
        write("2026-10-08T18:32:42", "Monthly reminder: the next daily look is 2026-10-09 09:00")
        write("2026-10-08T18:32:46", "check (the daily alarm): notify: no trip, Thu 2026-10-08")
        write("2026-10-08T18:32:46", "notification shown for 2026-10-08")
        // The date is back.
        write("2026-10-07T19:30:10", "check (app opened): 10 trips today, Wed 2026-10-07")
    }

    /** The same evening on MilO's own clock: each line dated with what that clock reads. */
    private fun theEveningOnMilosClock() {
        val phone = JumpingPhone(at("2026-10-07T17:52:32"))
        fun writeNow(message: String) = runBlocking {
            log.add(phone.clock.now(), EventCategory.TRIP, message)
        }
        writeNow("Trip 15: finished")
        phone.advanceTo(at("2026-10-07T18:23:13"))
        writeNow("check (app opened): 10 trips today, Wed 2026-10-07")
        phone.advanceTo(at("2026-10-07T18:32:42"))
        phone.setAhead()
        writeNow("Monthly reminder: the next daily look is not asked for now")
        phone.advance(4_000)
        writeNow("check (the daily alarm): 10 trips today, Wed 2026-10-07")
        phone.advance(5_000)
        phone.setBack()
        writeNow("The phone's date was set 24 h 0 min ahead. MilO saw it back")
        phone.advanceTo(at("2026-10-07T19:30:10"))
        writeNow("check (app opened): 10 trips today, Wed 2026-10-07")
    }

    private fun screen(today: LocalDate): List<String> = runBlocking {
        logDays(log.observeNewest(EVENT_LOG_PAGE_SIZE).first(), zone, today).flatMap { day ->
            val heading = if (day.isToday) "(today, no heading)" else formatLogDay(day.date)
            val lines =
                day.lines.map { "  ${formatLogClock(it.entry.atMs, zone)} ${it.entry.message}" }
            listOf(heading) + lines
        }
    }

    // ---- What the build before this one left -----------------------------------------------------

    @Test
    fun `the old build's three lines of the jump stand on top under tomorrow's name`() {
        theEveningAsTheOldBuildWroteIt()

        assertEquals(
            listOf(
                "Thu 2026-10-08",
                "  18:32:46 notification shown for 2026-10-08",
                "  18:32:46 check (the daily alarm): notify: no trip, Thu 2026-10-08",
                "  18:32:42 Monthly reminder: the next daily look is 2026-10-09 09:00",
                "(today, no heading)",
                "  19:30:10 check (app opened): 10 trips today, Wed 2026-10-07",
                "  18:23:13 check (app opened): 10 trips today, Wed 2026-10-07",
                "  17:52:32 Trip 15: finished",
            ),
            screen(today = wednesday),
        )
    }

    @Test
    fun `the next day they head today's tile until 18-32, above everything the real day writes`() {
        theEveningAsTheOldBuildWroteIt()
        write("2026-10-08T07:41:02", "Trip 16: started by the truck")
        write("2026-10-08T12:00:04", "check (the daily alarm): 1 trip today, Thu 2026-10-08")

        assertEquals(
            listOf(
                "(today, no heading)",
                "  18:32:46 notification shown for 2026-10-08",
                "  18:32:46 check (the daily alarm): notify: no trip, Thu 2026-10-08",
                "  18:32:42 Monthly reminder: the next daily look is 2026-10-09 09:00",
                "  12:00:04 check (the daily alarm): 1 trip today, Thu 2026-10-08",
                "  07:41:02 Trip 16: started by the truck",
                "Wed 2026-10-07",
                "  19:30:10 check (app opened): 10 trips today, Wed 2026-10-07",
                "  18:23:13 check (app opened): 10 trips today, Wed 2026-10-07",
                "  17:52:32 Trip 15: finished",
            ),
            screen(today = thursday),
        )
    }

    // ---- What MilO writes now ---------------------------------------------------------------------

    @Test
    fun `lines written while the date is ahead stand under today, in the order written`() {
        theEveningOnMilosClock()

        assertEquals(
            listOf(
                "(today, no heading)",
                "  19:30:10 check (app opened): 10 trips today, Wed 2026-10-07",
                "  18:32:51 The phone's date was set 24 h 0 min ahead. MilO saw it back",
                "  18:32:46 check (the daily alarm): 10 trips today, Wed 2026-10-07",
                "  18:32:42 Monthly reminder: the next daily look is not asked for now",
                "  18:23:13 check (app opened): 10 trips today, Wed 2026-10-07",
                "  17:52:32 Trip 15: finished",
            ),
            screen(today = wednesday),
        )
    }

    @Test
    fun `in the shared file they are Wednesday's lines, between the lines around them`() {
        theEveningOnMilosClock()
        write("2026-10-08T07:41:02", "Trip 16: started by the truck")

        val file = runBlocking {
            buildString { log.readAll { page -> append(eventLogFileLines(page, zone)) } }
        }

        assertEquals(
            """
            2026-10-07 17:52:32  TRIP  Trip 15: finished
            2026-10-07 18:23:13  TRIP  check (app opened): 10 trips today, Wed 2026-10-07
            2026-10-07 18:32:42  TRIP  Monthly reminder: the next daily look is not asked for now
            2026-10-07 18:32:46  TRIP  check (the daily alarm): 10 trips today, Wed 2026-10-07
            2026-10-07 18:32:51  TRIP  The phone's date was set 24 h 0 min ahead. MilO saw it back
            2026-10-07 19:30:10  TRIP  check (app opened): 10 trips today, Wed 2026-10-07
            2026-10-08 07:41:02  TRIP  Trip 16: started by the truck
            """.trimIndent() + "\n",
            file,
        )
    }
}
