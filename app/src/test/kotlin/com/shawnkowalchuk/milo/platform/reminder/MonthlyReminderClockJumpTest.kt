package com.shawnkowalchuk.milo.platform.reminder

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.ReminderShown
import com.shawnkowalchuk.milo.platform.clock.CLOCK_WATCH_EVERY_MS
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SECOND_MS = 1_000L

/**
 * The monthly reminder on a phone whose date is set one day ahead by hand for a few seconds and
 * then set back (ADR-006). The phone, the process and Android's part are those of
 * [MonthlyReminderClockJumpFixture].
 *
 * These began as the investigation's proofs of what the reminder did before MilO had a clock of
 * its own (2026-10-07): on the last evening of a month it announced a report for a month that
 * had not ended, the real 1st then went without, and the alarm slipped a day. The scenes are
 * the same; the outcomes are now the right ones. What is asked of Android while the clocks
 * disagree is in `MonthlyReminderAlarmInAJumpTest`.
 */
// runCurrent() is how a test lets the reminder's coroutines run.
@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyReminderClockJumpTest : MonthlyReminderClockJumpFixture() {
    private val october: YearMonth = YearMonth.of(2026, 10)

    @Test
    fun `on the last evening of a month the jump announces nothing and asks for no time of day`() =
        runTest {
            lastDayOfOctober()
            phoneAt("2026-10-31T09:00:05")
            val reminder = process()
            reminder.arm("process start")
            runCurrent()
            // September is sent: nothing to remind of, and the alarm is for 1 November.
            assertEquals(emptyList<YearMonth>(), shownFor)
            assertEquals(listOf(at("2026-11-01T09:00")), alarmsAskedFor)

            // Saturday 31 October, 19:00: the phone's date is set to 1 November. Before the
            // fix: "October's report has not been sent", on 31 October.
            phone.advanceTo(at("2026-10-31T19:00"))
            dateSetAhead(reminder)

            assertEquals(emptyList<YearMonth>(), shownFor)
            assertNull(settings.current().reminderShown)
            assertEquals(1, alarmsAskedFor.size)
            // Fifteen hours, not fourteen: the clocks go back an hour in that night.
            assertTrue(lines().any { it.contains("Android was asked to count 15 h 0 min") })
        }

    @Test
    fun `the real 1st gets its reminder, from an alarm asked for when the clock was back`() =
        runTest {
            lastDayOfOctober()
            phoneAt("2026-10-31T09:00:05")
            val reminder = process()
            reminder.arm("process start")
            runCurrent()
            phone.advanceTo(at("2026-10-31T19:00"))
            dateSetAhead(reminder)

            dateSetBack()

            // Asked for once more, for the real next morning.
            assertEquals(listOf(at("2026-11-01T09:00"), at("2026-11-01T09:00")), alarmsAskedFor)
            assertFalse(alarmIsDue())
            assertEquals(
                listOf(
                    "The phone's date was set 24 h 0 min ahead. MilO saw it back 10 s later " +
                        "and kept its own time.",
                ),
                lines(EventCategory.PROCESS),
            )

            // The real 1 November: the alarm arrives at 09:00, and October is reminded of.
            // Before the fix the day passed without: its reminder "was already shown today".
            phone.advanceTo(at("2026-11-01T09:00:05"))
            assertTrue(alarmIsDue())
            reminder.onAlarm {}
            runCurrent()

            assertEquals(listOf(october), shownFor)
            assertEquals(
                ReminderShown(october, LocalDate.of(2026, 11, 1)),
                settings.current().reminderShown,
            )
            assertEquals(at("2026-11-02T09:00"), alarmsAskedFor.last())
        }

    @Test
    fun `a process the alarm starts while the date is ahead does the same`() = runTest {
        lastDayOfOctober()
        phoneAt("2026-10-31T12:00")
        phone.advanceTo(at("2026-10-31T19:00"))
        // No process of MilO is running. The alarm, overdue all at once, starts one.
        phone.setAhead()
        phone.newProcess()
        val reminder = process()
        reminder.arm("process start")
        reminder.onAlarm {}
        runCurrent()

        assertEquals(emptyList<YearMonth>(), shownFor)
        assertEquals(emptyList<Long>(), alarmsAskedFor)

        // The clock goes back and Android does not say so. MilO's next look at it sees it.
        phone.advance(10 * SECOND_MS)
        phone.setBack()
        phone.advance(CLOCK_WATCH_EVERY_MS)
        advanceTimeBy(CLOCK_WATCH_EVERY_MS)
        runCurrent()

        assertEquals(listOf(at("2026-11-01T09:00")), alarmsAskedFor)
        assertEquals(emptyList<YearMonth>(), shownFor)
    }

    @Test
    fun `in mid-month the day's one reminder stays one, and the next day has its alarm`() =
        runTest {
            // Tuesday 6 October 10:00, September unsent, two trips: the reminder is due.
            phoneAt("2026-10-06T10:00")
            val reminder = process()
            reminder.arm("process start")
            runCurrent()
            assertEquals(listOf(september), shownFor)

            // Before the fix the jump posted it again, and the way back a third time.
            phone.advanceTo(at("2026-10-06T18:30"))
            dateSetAhead(reminder)
            dateSetBack()
            reminder.look("app opened")
            runCurrent()

            assertEquals(listOf(september), shownFor)
            assertEquals(
                ReminderShown(september, LocalDate.of(2026, 10, 6)),
                settings.current().reminderShown,
            )
            // The real 7th has its alarm at 09:00.
            assertEquals(at("2026-10-07T09:00"), alarmsAskedFor.last())
            phone.advanceTo(at("2026-10-07T09:00:05"))
            assertTrue(alarmIsDue())
        }

    // ---- What the build before this one left in the settings --------------------------------------

    @Test
    fun `a stored reminder 'shown' on a day after today counts as not shown`() = runTest {
        // Stored on the evening of the 6th while the phone's date read the 7th.
        settings.setReminderShown(ReminderShown(september, LocalDate.of(2026, 10, 7)))
        sent = listOf(sentFor(september))
        phoneAt("2026-10-06T19:30")
        val reminder = process()

        reminder.look("app opened")
        runCurrent()

        assertNull(settings.current().reminderShown)
        assertTrue(
            lines().any {
                it.startsWith(
                    "Monthly reminder: the settings said a reminder for 2026-09 was shown " +
                        "on 2026-10-07, a day after today, 2026-10-06.",
                )
            },
        )
        // So if September turns out not to be sent after all, the real 7th reminds of it.
        sent = emptyList()
        phone.advanceTo(at("2026-10-07T09:00:05"))
        reminder.look("app opened")
        runCurrent()
        assertEquals(listOf(september), shownFor)
    }
}
