package com.shawnkowalchuk.milo.platform.reminder

import com.shawnkowalchuk.milo.data.settings.ReminderShown
import com.shawnkowalchuk.milo.platform.clock.AskedAlarm
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SECOND_MS = 1_000L

/**
 * What the monthly reminder asks of Android's alarm service while MilO's clock and the phone's
 * disagree (ADR-005, decided on 2026-10-09): the same alarm, on the clock that counts from
 * boot, due after the time that is really left. Android delivers the daily alarm the moment
 * the date goes ahead; until this decision the reminder then asked for nothing and relied on
 * being woken when the date came back. A MilO that was not woken had no alarm at all.
 */
// runCurrent() is how a test lets the reminder's coroutines run.
@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyReminderAlarmInAJumpTest : MonthlyReminderClockJumpFixture() {
    private val october: YearMonth = YearMonth.of(2026, 10)

    @Test
    fun `inside the jump the alarm is asked for once, counted from boot, for the real next 09h`() =
        runTest {
            // Tuesday 6 October, 18:30. September is sent, so nothing is shown.
            sent = listOf(sentFor(september))
            phoneAt("2026-10-06T10:00")
            val reminder = process()
            reminder.arm("process start")
            runCurrent()
            phone.advanceTo(at("2026-10-06T18:30"))

            dateSetAhead(reminder)

            // One request that counts, for exactly the time left until Wednesday 09:00, and
            // none for a time of day beyond the one of the process start.
            assertEquals(listOf(at("2026-10-07T09:00") - at("2026-10-06T18:30")), alarmsAskedAfter)
            assertEquals(listOf(at("2026-10-07T09:00")), alarmsAskedFor)
            // The phone reads Wednesday 18:30, past the 09:00 it was asked for. It is not due.
            assertFalse(alarmIsDue())
            assertTrue(
                lines().any {
                    it.startsWith(
                        "Monthly reminder: the next daily look is asked for at 2026-10-07 " +
                            "09:00:00, or within about an hour after it (the daily alarm). The " +
                            "phone's clock is set differently from MilO's own, so Android was " +
                            "asked to count 14 h 30 min from now",
                    )
                },
            )
        }

    @Test
    fun `after the return the alarm is asked for once in the normal way, in the other's place`() =
        runTest {
            sent = listOf(sentFor(september))
            phoneAt("2026-10-06T18:30")
            val reminder = process()
            reminder.arm("process start")
            runCurrent()
            dateSetAhead(reminder)

            dateSetBack()

            assertEquals(listOf(at("2026-10-07T09:00"), at("2026-10-07T09:00")), alarmsAskedFor)
            assertEquals(1, alarmsAskedAfter.size)
            assertEquals(AskedAlarm.AtTimeOfDay(at("2026-10-07T09:00")), alarmHeld)
        }

    @Test
    fun `with MilO gone before the date is back, the counted alarm remains and the 1st is told`() =
        runTest {
            lastDayOfOctober()
            phoneAt("2026-10-31T09:00:05")
            val reminder = process()
            reminder.arm("process start")
            runCurrent()
            // Saturday 31 October, 19:00: the phone's date is set to 1 November.
            phone.advanceTo(at("2026-10-31T19:00"))
            dateSetAhead(reminder)
            assertEquals(emptyList<YearMonth>(), shownFor)

            // Android ends MilO's process, or freezes it. Ten seconds later the date is back,
            // and nothing tells MilO: no broadcast reaches it, and its own waits do not run.
            // (No coroutine time passes from here on.)
            phone.advance(10 * SECOND_MS)
            phone.setBack()

            // The night has 25 hours here, the clocks going back. The alarm counts through it.
            assertFalse(alarmIsDue())
            phone.advanceTo(at("2026-11-01T08:59:59"))
            assertFalse(alarmIsDue())
            phone.advanceTo(at("2026-11-01T09:00:00"))
            assertTrue(alarmIsDue())

            // It starts MilO, a few seconds later: October is reminded of on the real 1st,
            // and the 2nd has its alarm in the normal way.
            phone.advance(5 * SECOND_MS)
            phone.newProcess()
            val woken = process()
            woken.arm("process start")
            woken.onAlarm {}
            runCurrent()

            assertEquals(listOf(october), shownFor)
            assertEquals(
                ReminderShown(october, LocalDate.of(2026, 11, 1)),
                settings.current().reminderShown,
            )
            assertEquals(AskedAlarm.AtTimeOfDay(at("2026-11-02T09:00")), alarmHeld)
        }
}
