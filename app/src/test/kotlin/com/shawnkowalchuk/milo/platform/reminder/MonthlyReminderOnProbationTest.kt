package com.shawnkowalchuk.milo.platform.reminder

import com.shawnkowalchuk.milo.core.clock.PROBATION_MS
import com.shawnkowalchuk.milo.data.settings.ReminderShown
import com.shawnkowalchuk.milo.platform.clock.AskedAlarm
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The monthly reminder in a MilO whose clock is on probation (ADR-006): the first MilO process
 * after a restart of the phone, or the first after the update that brought the clock. It has
 * only the phone's clock, and that may be in the seconds the date is set ahead. Such a MilO
 * shows no reminder, and asks to be woken two minutes on, when its clock can be confirmed.
 */
// runCurrent() is how a test lets the reminder's coroutines run.
@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyReminderOnProbationTest : MonthlyReminderClockJumpFixture() {
    private val october: YearMonth = YearMonth.of(2026, 10)

    /** The first MilO process after a restart of the phone, and what its start does. */
    private fun TestScope.firstProcessOfTheBoot(): MonthlyReminder {
        phone.reboot()
        val reminder = process()
        reminder.arm("process start")
        runCurrent()
        return reminder
    }

    @Test
    fun `started inside the jump on a month's last evening, it announces no month`() = runTest {
        lastDayOfOctober()
        phoneAt("2026-10-31T19:00")
        // The phone reads 1 November, and by that date October's report is due.
        phone.setAhead()
        val reminder = firstProcessOfTheBoot()

        assertEquals(emptyList<YearMonth>(), shownFor)
        assertNull(settings.current().reminderShown)
        assertEquals(emptyList<Long>(), alarmsAskedFor)
        assertEquals(listOf(PROBATION_MS), alarmsAskedAfter)
        assertTrue(
            lines().any {
                it.startsWith(
                    "Monthly reminder: nothing shown for 2026-10 yet (process start): it is " +
                        "due by today's date, 2026-11-01, but MilO's time is not confirmed yet.",
                )
            },
        )

        // The date comes back, and two minutes on the clock is confirmed: the alarm is asked
        // for in the normal way, for the real 1 November.
        dateSetBack()
        phone.advance(PROBATION_MS)
        assertTrue(alarmIsDue())
        reminder.onAlarm {}
        runCurrent()
        assertEquals(emptyList<YearMonth>(), shownFor)
        assertEquals(AskedAlarm.AtTimeOfDay(at("2026-11-01T09:00")), alarmHeld)

        // The real 1st has its reminder, and it is the only one.
        phone.advanceTo(at("2026-11-01T09:00:05"))
        assertTrue(alarmIsDue())
        reminder.onAlarm {}
        runCurrent()
        assertEquals(listOf(october), shownFor)
        assertEquals(
            ReminderShown(october, LocalDate.of(2026, 11, 1)),
            settings.current().reminderShown,
        )
    }

    @Test
    fun `after a restart with the date right, a reminder that is due comes two minutes on`() =
        runTest {
            // Tuesday 6 October: September has Business trips and no report is sent.
            phoneAt("2026-10-06T10:00")
            val reminder = firstProcessOfTheBoot()
            assertEquals(emptyList<YearMonth>(), shownFor)
            assertEquals(listOf(PROBATION_MS), alarmsAskedAfter)

            // MilO opened in between shows nothing either, and says so once, not twice.
            reminder.look("MilO was opened")
            runCurrent()
            assertEquals(emptyList<YearMonth>(), shownFor)
            assertEquals(1, lines().count { it.contains("nothing shown for 2026-09 yet") })

            phone.advance(PROBATION_MS)
            assertTrue(alarmIsDue())
            reminder.onAlarm {}
            runCurrent()

            assertEquals(listOf(september), shownFor)
            assertEquals(
                ReminderShown(september, LocalDate.of(2026, 10, 6)),
                settings.current().reminderShown,
            )
            assertEquals(AskedAlarm.AtTimeOfDay(at("2026-10-07T09:00")), alarmHeld)
        }
}
