package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.data.settings.setNothingRecordedEnabled
import com.shawnkowalchuk.milo.platform.clock.AskedAlarm
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SECOND_MS = 1_000L
private const val HOUR_MS = 3_600 * SECOND_MS

/**
 * What the "nothing recorded" check asks of Android's alarm service while MilO's clock and the
 * phone's disagree (ADR-006, decided on 2026-10-09): the same alarm, on the clock that counts
 * from boot, due after the time that is really left. Android delivers the daily alarm the
 * moment the date goes ahead; until this decision the check then asked for nothing and relied
 * on being woken when the date came back. A MilO that was not woken had no alarm at all.
 */
// runCurrent() and letItLook() are how a test lets the check's coroutines run.
@OptIn(ExperimentalCoroutinesApi::class)
class NothingRecordedAlarmInAJumpTest : NothingRecordedClockJumpFixture() {
    private val thursday = LocalDate.of(2026, 10, 8)

    @Test
    fun `inside the jump the alarm is asked for once, counted from boot, for the real next noon`() =
        runTest {
            trips = wednesdaysTrips()
            phoneAt("2026-10-07T18:23:13")
            val check = process()
            check.arm("process start")
            runCurrent()
            phone.advanceTo(at("2026-10-07T18:32:42"))

            dateSetAhead(check)

            // One request that counts, for exactly the time left until Thursday noon, and
            // none for a time of day beyond the one of the process start.
            val untilNoon = at("2026-10-08T12:00") - at("2026-10-07T18:32:42")
            assertEquals(listOf(untilNoon), alarmsAskedAfter)
            assertEquals(listOf(at("2026-10-08T12:00")), alarmsAskedFor)
            // The phone reads Thursday 18:32, past the noon it was asked for. It is not due.
            assertFalse(alarmIsDue())
            assertTrue(
                lines().any {
                    it.startsWith(
                        "Nothing-recorded check: the next daily look is asked for at " +
                            "2026-10-08 12:00:00, or soon after it (the daily alarm). The " +
                            "phone's clock is set differently from MilO's own, so Android was " +
                            "asked to count 17 h 27 min from now",
                    )
                },
            )
        }

    @Test
    fun `asked for again and again inside the jump, it is never due there`() = runTest {
        trips = wednesdaysTrips()
        phoneAt("2026-10-07T18:32:42")
        val check = process()
        check.arm("process start")
        runCurrent()
        dateSetAhead(check)

        // Before MilO kept a clock this was a loop: each alarm asked for the next, which the
        // phone's clock had already passed. Whatever asks now, nothing is due.
        repeat(5) {
            phone.advance(SECOND_MS)
            check.arm("the work schedule was changed")
            runCurrent()
            assertFalse(alarmIsDue())
        }
        assertEquals(6, alarmsAskedAfter.size)
        assertEquals(1, alarmsAskedFor.size)
    }

    @Test
    fun `after the return the alarm is asked for once in the normal way, in the other's place`() =
        runTest {
            trips = wednesdaysTrips()
            phoneAt("2026-10-07T18:32:42")
            val check = process()
            check.arm("process start")
            runCurrent()
            dateSetAhead(check)

            dateSetBack()

            assertEquals(listOf(at("2026-10-08T12:00"), at("2026-10-08T12:00")), alarmsAskedFor)
            assertEquals(1, alarmsAskedAfter.size)
            assertEquals(AskedAlarm.AtTimeOfDay(at("2026-10-08T12:00")), alarmHeld)
        }

    @Test
    fun `with MilO gone before the date is back, the counted alarm remains and comes at noon`() =
        runTest {
            trips = wednesdaysTrips()
            phoneAt("2026-10-07T18:32:42")
            val check = process()
            check.arm("process start")
            runCurrent()
            dateSetAhead(check)

            // Android ends MilO's process, or freezes it. Nine seconds later the date is
            // back, and nothing tells MilO: no broadcast reaches it, and its own waits do not
            // run. (No coroutine time passes from here on.)
            phone.advance(9 * SECOND_MS)
            phone.setBack()

            // The night passes with the date right, and then with the trick done once more.
            assertFalse(alarmIsDue())
            phone.advanceTo(at("2026-10-07T23:00"))
            phone.setAhead()
            assertFalse(alarmIsDue())
            phone.advance(9 * SECOND_MS)
            phone.setBack()
            phone.advanceTo(at("2026-10-08T11:59:59"))
            assertFalse(alarmIsDue())
            phone.advanceTo(at("2026-10-08T12:00:00"))
            assertTrue(alarmIsDue())

            // It starts MilO, a few seconds later. Thursday has no trip: the notification,
            // and Friday's alarm in the normal way.
            phone.advance(5 * SECOND_MS)
            phone.newProcess()
            val woken = process()
            woken.arm("process start")
            woken.onAlarm {}
            letItLook()

            assertEquals(1, shown)
            assertEquals(thursday, settings.current().nothingRecorded.shownOn)
            assertEquals(AskedAlarm.AtTimeOfDay(at("2026-10-09T12:00")), alarmHeld)
        }

    @Test
    fun `a clock that is set back has the alarm count from boot at once`() = runTest {
        trips = wednesdaysTrips()
        phoneAt("2026-10-07T18:32:42")
        val check = process()
        check.arm("process start")
        runCurrent()
        assertEquals(AskedAlarm.AtTimeOfDay(at("2026-10-08T12:00")), alarmHeld)

        // Set back three hours by hand. Android delivers no alarm for that, and the one it
        // holds would come when the phone's clock reads noon: three hours late.
        phone.setBy(-3 * HOUR_MS)
        watch.onPhoneClockSet {}
        letItLook()

        val untilNoon = at("2026-10-08T12:00") - at("2026-10-07T18:32:42")
        assertEquals(listOf(untilNoon), alarmsAskedAfter)
        assertTrue(lines().any { it.contains("(the phone's clock was set back and MilO keeps") })
        phone.advanceTo(at("2026-10-08T11:59:59"))
        assertFalse(alarmIsDue())
        phone.advanceTo(at("2026-10-08T12:00:00"))
        assertTrue(alarmIsDue())
    }

    @Test
    fun `a check that is switched off asks for no alarm in the jump either`() = runTest {
        trips = wednesdaysTrips()
        phoneAt("2026-10-07T18:32:42")
        val check = process()
        check.arm("process start")
        runCurrent()
        settings.setNothingRecordedEnabled(false)
        phone.setAhead()

        check.arm("the Settings card was changed")
        runCurrent()

        assertEquals(emptyList<Long>(), alarmsAskedAfter)
        assertEquals(null, alarmHeld)
    }
}
