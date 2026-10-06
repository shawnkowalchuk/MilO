package com.shawnkowalchuk.milo.platform.reminder

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.report.kind
import com.shawnkowalchuk.milo.data.settings.ReminderShown
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The monthly reminder beside stand-ins for the alarm, the notification, the settings file, the
 * list of sent reports and the trips: what it shows, when it withdraws, and that one day brings
 * one reminder however often it looks. What goes wrong is in `MonthlyReminderFailureTest`.
 */
// runCurrent() is how a test lets the reminder's coroutines run. The API is marked experimental
// by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyReminderTest : MonthlyReminderFixture() {
    // ---- Showing ----------------------------------------------------------------------------------

    @Test
    fun `a process start asks for the daily alarm and shows the reminder that is due`() = runTest {
        reminder().arm("process start")
        runCurrent()

        assertEquals(listOf(at("2026-10-07T09:00")), alarmsAskedFor)
        assertEquals(listOf(september), shownFor)
        assertEquals(
            ReminderShown(september, LocalDate.of(2026, 10, 6)),
            settings.current().reminderShown,
        )
        assertEquals(
            listOf(
                "Monthly reminder: the next daily look is asked for at 2026-10-07 09:00:00, or " +
                    "within about an hour after it (process start). MilO also looks at every " +
                    "start and every time it is opened.",
                "Monthly reminder shown for 2026-09 (process start): today, 2026-10-06, is on " +
                    "or after 2026-10-01, 2026-09 has 2 Business trips, and no report for it " +
                    "is recorded as sent.",
            ),
            lines(),
        )
    }

    @Test
    fun `however often MilO looks, one day brings one reminder, and the next day another`() =
        runTest {
            val reminder = reminder()

            reminder.arm("process start")
            runCurrent()
            repeat(3) { reminder.look("app opened") }
            runCurrent()
            // A new process on the same day finds in the settings that it was shown.
            reminder().look("app opened")
            runCurrent()
            assertEquals(listOf(september), shownFor)

            nowMs = at("2026-10-07T09:00")
            reminder.onAlarm {}
            runCurrent()

            assertEquals(listOf(september, september), shownFor)
            // The alarm asked for the day after, and nothing was withdrawn along the way.
            assertEquals(at("2026-10-08T09:00"), alarmsAskedFor.last())
            assertEquals(0, withdrawn)
        }

    @Test
    fun `looks that change nothing are not written to the log again and again`() = runTest {
        val reminder = reminder()
        reminder.look("app opened")
        runCurrent()
        val written = lines().size

        repeat(5) { reminder.look("app opened") }
        runCurrent()

        assertEquals(written, lines().size)
    }

    @Test
    fun `the alarm tells its receiver when it is done, whatever happened`() = runTest {
        var done = 0
        failReadingTrips = IllegalStateException("the trips table is broken")

        reminder().onAlarm { done++ }
        runCurrent()

        assertEquals(1, done)
    }

    // ---- Not showing ------------------------------------------------------------------------------

    @Test
    fun `a month that is recorded as sent brings none, and one that is showing is withdrawn`() =
        runTest {
            val reminder = reminder()
            reminder.look("app opened")
            runCurrent()
            assertEquals(listOf(september), shownFor)

            sent = listOf(sentFor(september))
            reminder.look("a report was recorded as sent")
            runCurrent()

            assertEquals(listOf(september), shownFor)
            assertEquals(1, withdrawn)
            assertEquals(
                "Monthly reminder: nothing shown for 2026-09 (a report was recorded as sent): " +
                    "a report for 2026-09 is recorded as sent.",
                lines().last(),
            )
        }

    @Test
    fun `a report for another month, or for a date range, does not stand in for last month's`() =
        runTest {
            val range = ReportPeriod.Range(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
            sent =
                listOf(
                    sentFor(YearMonth.of(2026, 8)),
                    sentFor(september).copy(kind = range.kind),
                )

            reminder().look("app opened")
            runCurrent()

            assertEquals(listOf(september), shownFor)
        }

    @Test
    fun `only the trips the report would list count, Business, finished, started last month`() =
        runTest {
            trips =
                listOf(
                    businessTrip("2026-09-14T08:10", TripCategory.PERSONAL),
                    businessTrip("2026-09-15T08:10", category = null),
                    businessTrip("2026-09-16T08:10").copy(status = TripStatus.DELETED),
                    businessTrip("2026-08-31T23:59"),
                    businessTrip("2026-10-01T00:00"),
                )

            reminder().look("app opened")
            runCurrent()

            assertEquals(emptyList<YearMonth>(), shownFor)
            assertEquals(
                "Monthly reminder: nothing shown for 2026-09 (app opened): 2026-09 has no " +
                    "Business trip to report.",
                lines().single(),
            )
        }

    @Test
    fun `switched off in Settings it shows nothing, and withdraws what was showing`() = runTest {
        val reminder = reminder()
        reminder.look("app opened")
        runCurrent()

        settings.setReminderEnabled(false)
        reminder.look("the reminder was changed in Settings")
        runCurrent()

        assertEquals(listOf(september), shownFor)
        assertEquals(1, withdrawn)
        assertTrue(lines().last().endsWith("the reminder is switched off in Settings."))
    }

    @Test
    fun `before the chosen day it shows nothing`() = runTest {
        settings.setReminderDay(15)

        reminder().look("app opened")
        runCurrent()

        assertEquals(emptyList<YearMonth>(), shownFor)
        assertEquals(
            "Monthly reminder: nothing shown for 2026-09 (app opened): the reminder starts on " +
                "2026-10-15 (day 15 of the month, as set), and today is 2026-10-06.",
            lines().single(),
        )
    }
}
