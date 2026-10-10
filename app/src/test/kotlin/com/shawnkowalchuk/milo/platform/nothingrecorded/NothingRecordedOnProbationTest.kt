package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.core.clock.PROBATION_MS
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.platform.clock.AskedAlarm
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SECOND_MS = 1_000L

private const val STARTED_AHEAD_LINE =
    "MilO started while the phone's date was set 24 h 0 min ahead, and took the phone's time " +
        "when it came back."

/**
 * The "nothing recorded" check in a MilO whose clock is on probation (ADR-006): the first MilO
 * process after a restart of the phone, or the first after the update that brought the clock.
 * It has only the phone's clock, and that may be in the seconds the date is set ahead.
 *
 * On an emulator on 2026-10-09 exactly that start showed "No trip recorded today", with its
 * sound, for the nine seconds until the date was back, after a day with six trips. Since then
 * such a MilO shows no notification, and asks to be woken two minutes on, when its clock can
 * be confirmed. A notification that is truly due comes then.
 */
// letItLook() is how a test lets the check's coroutines run.
@OptIn(ExperimentalCoroutinesApi::class)
class NothingRecordedOnProbationTest : NothingRecordedClockJumpFixture() {
    private val thursday = LocalDate.of(2026, 10, 8)

    /** The first MilO process after a restart of the phone, and what its start does. */
    private fun TestScope.firstProcessOfTheBoot(): NothingRecordedCheck {
        phone.reboot()
        val check = process()
        check.arm("process start")
        letItLook()
        return check
    }

    @Test
    fun `a MilO that started inside the jump with no anchor shows nothing by the date`() = runTest {
        trips = wednesdaysTrips()
        phoneAt("2026-10-07T18:32:42")
        // Android starts MilO for the change of the date itself. The phone reads Thursday
        // evening: a work day with no trip, long past noon.
        phone.setAhead()
        val check = firstProcessOfTheBoot()

        // Nothing is shown and nothing is stored, and no alarm is asked for by that date.
        assertEquals(0, shown)
        assertNull(settings.current().nothingRecorded.shownOn)
        assertEquals(emptyList<Long>(), alarmsAskedFor)
        assertEquals(listOf(PROBATION_MS), alarmsAskedAfter)
        assertTrue(
            lines().any {
                it.startsWith(
                    "Nothing-recorded check (process start): no notification yet: no trip has " +
                        "been started today, Thu 2026-10-08, but MilO's time is not confirmed yet.",
                )
            },
        )
        assertTrue(
            lines().any {
                it.startsWith(
                    "Nothing-recorded check: MilO's time is not confirmed yet: it was taken " +
                        "from the phone's clock at a start with nothing to check it against " +
                        "(process start). So Android was asked to count 2 min from now.",
                )
            },
        )

        dateSetBack()

        // The date is back and MilO takes the phone's time, which is on probation in turn.
        // Wednesday has its trips: still nothing shown.
        assertEquals(listOf(STARTED_AHEAD_LINE), lines(EventCategory.PROCESS))
        assertEquals(0, shown)
        assertEquals(emptyList<Long>(), alarmsAskedFor)
        assertEquals(listOf(PROBATION_MS, PROBATION_MS), alarmsAskedAfter)

        // Two minutes on Android delivers the alarm. The clock is confirmed at that reading,
        // and the daily look is asked for in the normal way: the real Thursday noon.
        phone.advance(PROBATION_MS)
        assertTrue(alarmIsDue())
        check.onAlarm {}
        letItLook()
        assertEquals(AskedAlarm.AtTimeOfDay(at("2026-10-08T12:00")), alarmHeld)

        // So the real Thursday, with no trip, has its notification at noon: its only one.
        phone.advanceTo(at("2026-10-08T12:00:05"))
        assertTrue(alarmIsDue())
        check.onAlarm {}
        letItLook()
        assertEquals(1, shown)
        assertEquals(thursday, settings.current().nothingRecorded.shownOn)
    }

    @Test
    fun `frozen inside the jump and never told, MilO is woken two minutes on and is right`() =
        runTest {
            trips = wednesdaysTrips()
            phoneAt("2026-10-07T18:32:42")
            phone.setAhead()
            val check = firstProcessOfTheBoot()

            // Android freezes MilO's process. Nine seconds later the date is back, and nothing
            // tells MilO. Its alarm counts from boot, so the date does not move it.
            phone.advance(9 * SECOND_MS)
            phone.setBack()
            assertFalse(alarmIsDue())
            phone.advance(PROBATION_MS - 9 * SECOND_MS)
            assertTrue(alarmIsDue())
            check.onAlarm {}
            letItLook()

            // That reading saw the date back. Two more minutes, and the clock is confirmed.
            assertEquals(listOf(STARTED_AHEAD_LINE), lines(EventCategory.PROCESS))
            phone.advance(PROBATION_MS)
            assertTrue(alarmIsDue())
            check.onAlarm {}
            letItLook()

            // Never an alarm for Friday noon, which is what the date said: the real Thursday.
            assertEquals(listOf(at("2026-10-08T12:00")), alarmsAskedFor)
            assertEquals(AskedAlarm.AtTimeOfDay(at("2026-10-08T12:00")), alarmHeld)
            assertEquals(0, shown)
            assertNull(settings.current().nothingRecorded.shownOn)
        }

    @Test
    fun `after a restart with the date right, a notification that is due comes two minutes on`() =
        runTest {
            // Thursday 8 October, 13:00: a work day, past noon, and no trip.
            phoneAt("2026-10-08T13:00")
            val check = firstProcessOfTheBoot()
            assertEquals(0, shown)
            assertEquals(listOf(PROBATION_MS), alarmsAskedAfter)

            // MilO opened in between does not show it either, and the alarm is not due yet.
            phone.advance(60 * SECOND_MS)
            check.look("MilO was opened")
            letItLook()
            assertEquals(0, shown)
            assertFalse(alarmIsDue())

            phone.advance(60 * SECOND_MS)
            assertTrue(alarmIsDue())
            check.onAlarm {}
            letItLook()

            assertEquals(1, shown)
            assertEquals(thursday, settings.current().nothingRecorded.shownOn)
            assertEquals(AskedAlarm.AtTimeOfDay(at("2026-10-09T12:00")), alarmHeld)
        }

    @Test
    fun `a date that goes ahead and stays during the probation gets the counted alarm`() = runTest {
        trips = wednesdaysTrips()
        phoneAt("2026-10-07T18:32:42")
        val check = firstProcessOfTheBoot()
        assertEquals(listOf(PROBATION_MS), alarmsAskedAfter)

        // Half a minute after the start the date is set ahead, and left there.
        phone.advance(30 * SECOND_MS)
        phone.setAhead()
        watch.onPhoneClockSet {}
        phone.advance(PROBATION_MS)
        assertTrue(alarmIsDue())
        check.onAlarm {}
        letItLook()

        // Not two minutes again, and again for as long as the date stays: the time that is
        // really left until Thursday noon, by the time MilO took at its start.
        val untilNoon = at("2026-10-08T12:00") - phone.trueNowMs
        assertEquals(listOf(PROBATION_MS, untilNoon), alarmsAskedAfter)
        assertEquals(emptyList<Long>(), alarmsAskedFor)
        assertEquals(0, shown)
    }
}
