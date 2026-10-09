package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.setNothingRecordedShownOn
import com.shawnkowalchuk.milo.platform.clock.CLOCK_WATCH_EVERY_MS
import java.time.LocalDate
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
 * The "nothing recorded" check on a phone whose date is set one day ahead by hand for a few
 * seconds and then set back (the owner does this in the evening, several times on some
 * evenings; ADR-005). The phone, the process and Android's part are those of
 * [NothingRecordedClockJumpFixture].
 *
 * These began as the investigation's proofs of what the check did before MilO had a clock of
 * its own: "No trip recorded today" after a day of trips, tomorrow's notification used up, and
 * the alarm asked for the day after tomorrow. The scenes are the same; each now asserts that
 * nothing is shown, nothing is used up, Android is asked for no time of day while the clocks
 * disagree, and the alarm is asked for once, for the real next time, when they agree again.
 * What is asked of Android in between is in `NothingRecordedAlarmInAJumpTest`.
 */
// runCurrent() and letItLook() are how a test lets the check's coroutines run.
@OptIn(ExperimentalCoroutinesApi::class)
class NothingRecordedClockJumpTest : NothingRecordedClockJumpFixture() {
    private val wednesday = LocalDate.of(2026, 10, 7)
    private val thursday = LocalDate.of(2026, 10, 8)

    // ---- The evening of 7 October ----------------------------------------------------------------

    @Test
    fun `a day ahead for seconds shows nothing, uses nothing up, and asks for no time of day`() =
        runTest {
            trips = wednesdaysTrips()
            phoneAt("2026-10-07T18:23:13")
            val check = process()
            check.arm("process start")
            runCurrent()
            assertEquals(listOf(at("2026-10-08T12:00")), alarmsAskedFor)

            phone.advanceTo(at("2026-10-07T18:32:42"))
            dateSetAhead(check)

            // Today is still Wednesday, and Wednesday has its three trips.
            assertEquals(0, shown)
            assertNull(settings.current().nothingRecorded.shownOn)
            assertTrue(
                lines().any {
                    it.startsWith(
                        "Nothing-recorded check (the daily alarm): no notification: 3 trips " +
                            "were started or are being recorded today, Wed 2026-10-07.",
                    )
                },
            )
            // And while the clocks disagree Android is not asked for an alarm at a time of
            // day: it would deliver it at once, again and again. It is asked to count instead.
            assertEquals(1, alarmsAskedFor.size)
            assertTrue(lines().any { it.contains("Android was asked to count 17 h 27 min") })
        }

    @Test
    fun `once the clock is back the alarm is asked for once, for the real next noon`() = runTest {
        trips = wednesdaysTrips()
        phoneAt("2026-10-07T18:23:13")
        val check = process()
        check.arm("process start")
        runCurrent()
        phone.advanceTo(at("2026-10-07T18:32:42"))
        dateSetAhead(check)

        dateSetBack()

        assertEquals(listOf(at("2026-10-08T12:00"), at("2026-10-08T12:00")), alarmsAskedFor)
        assertEquals(0, shown)
        assertFalse(alarmIsDue())
        // Not due before the real Thursday's noon, and due at it.
        phone.advanceTo(at("2026-10-08T11:59:59"))
        assertFalse(alarmIsDue())
        phone.advanceTo(at("2026-10-08T12:00:00"))
        assertTrue(alarmIsDue())
        // The one line for the change, under the process's own category.
        assertEquals(
            listOf(
                "The phone's date was set 24 h 0 min ahead. MilO saw it back 9 s later and " +
                    "kept its own time.",
            ),
            lines(EventCategory.PROCESS),
        )
    }

    @Test
    fun `a process the alarm starts while the date is ahead does the same`() = runTest {
        trips = wednesdaysTrips()
        phoneAt("2026-10-07T17:30")
        phone.advanceTo(at("2026-10-07T18:32:42"))
        // No process of MilO is running. The alarm, a day overdue all at once, starts one:
        // `MiloApplication` arms the check, and the receiver hands it the alarm.
        phone.setAhead()
        phone.newProcess()
        val check = process()
        check.arm("process start")
        check.onAlarm {}
        letItLook()

        assertEquals(0, shown)
        assertNull(settings.current().nothingRecorded.shownOn)
        assertEquals(emptyList<Long>(), alarmsAskedFor)

        // The clock goes back and Android does not say so. MilO's next look at it sees it.
        phone.advance(9 * SECOND_MS)
        phone.setBack()
        phone.advance(CLOCK_WATCH_EVERY_MS)
        advanceTimeBy(CLOCK_WATCH_EVERY_MS)
        letItLook()

        assertEquals(listOf(at("2026-10-08T12:00")), alarmsAskedFor)
        assertEquals(0, shown)
    }

    @Test
    fun `the real Thursday, with no trip at all, gets its notification`() = runTest {
        trips = wednesdaysTrips()
        phoneAt("2026-10-07T18:23:13")
        val check = process()
        check.arm("process start")
        runCurrent()
        phone.advanceTo(at("2026-10-07T18:32:42"))
        dateSetAhead(check)
        dateSetBack()
        phone.advanceTo(at("2026-10-07T19:30:10"))
        check.look("app opened")
        runCurrent()
        assertEquals(0, shown)

        // Thursday for real. Trip detection has stopped: nothing is recorded all day. Before
        // the fix this day's one notification had been used up the evening before.
        phone.advanceTo(at("2026-10-08T12:00:05"))
        assertTrue(alarmIsDue())
        check.onAlarm {}
        letItLook()

        assertEquals(1, shown)
        assertEquals(thursday, settings.current().nothingRecorded.shownOn)
        assertEquals(at("2026-10-09T12:00"), alarmsAskedFor.last())
        // Once: MilO opened in the afternoon brings no second one.
        phone.advanceTo(at("2026-10-08T15:00"))
        check.look("app opened")
        letItLook()
        assertEquals(1, shown)
    }

    @Test
    fun `done every evening, each day's alarm still comes at its own noon, and nothing is shown`() =
        runTest {
            phoneAt("2026-10-05T18:00")
            trips = listOf(trip("2026-10-05T08:05"))
            val check = process()
            check.arm("process start")
            runCurrent()
            assertEquals(at("2026-10-06T12:00"), alarmsAskedFor.last())

            // Monday to Thursday evening at 18:30: a day ahead for ten seconds, then back.
            // Every one of the four real days has a trip.
            for (day in 5..8) {
                phone.advanceTo(at("2026-10-0${day}T18:30"))
                dateSetAhead(check)
                dateSetBack(afterSeconds = 10)
                // The alarm is for the next real noon, comes then, and finds that day's trip.
                trips = trips + trip("2026-10-0${day + 1}T08:05")
                phone.advanceTo(at("2026-10-0${day + 1}T11:59"))
                assertFalse("day $day", alarmIsDue())
                phone.advanceTo(at("2026-10-0${day + 1}T12:00:05"))
                assertTrue("day $day", alarmIsDue())
                check.onAlarm {}
                letItLook()
            }

            // Before the fix: four evenings, four notifications, each on a day that had a trip.
            assertEquals(0, shown)
            assertNull(settings.current().nothingRecorded.shownOn)
        }

    // ---- A day that has its true notification ----------------------------------------------------

    @Test
    fun `a Friday without a trip keeps its notification through the jump`() = runTest {
        // Friday 9 October, no trip all day: the notification at noon is true.
        phoneAt("2026-10-09T12:00:05")
        val check = process()
        check.arm("process start")
        letItLook()
        assertEquals(1, shown)
        assertEquals(0, withdrawn)

        // Friday evening: the phone reads Saturday. Before the fix "not a work day" took the
        // true notification away, and it was not shown again.
        phone.advanceTo(at("2026-10-09T18:30"))
        dateSetAhead(check)
        dateSetBack()
        check.look("app opened")
        letItLook()

        assertEquals(0, withdrawn)
        assertEquals(1, shown)
        assertEquals(LocalDate.of(2026, 10, 9), settings.current().nothingRecorded.shownOn)
        // Saturday is looked at too, at the time set.
        assertEquals(at("2026-10-10T12:00"), alarmsAskedFor.last())
    }

    @Test
    fun `a work day without a trip is told of once, however often the date is set ahead`() =
        runTest {
            // Tuesday 6 October 12:30, no trip: the true notification.
            phoneAt("2026-10-06T12:30")
            val check = process()
            check.arm("process start")
            letItLook()
            assertEquals(1, shown)

            // Before the fix the jump posted it again "for Wednesday", and the way back a
            // third time.
            phone.advanceTo(at("2026-10-06T18:30"))
            dateSetAhead(check)
            dateSetBack()
            check.look("app opened")
            letItLook()
            phone.advance(7 * 60 * SECOND_MS)
            dateSetAhead(check)
            dateSetBack()

            assertEquals(1, shown)
            assertEquals(tuesday, settings.current().nothingRecorded.shownOn)
        }

    @Test
    fun `while a trip is open the jump posts nothing, and the alarm is for the real next noon`() =
        runTest {
            // Wednesday 18:20: just arrived, the trip still open, the parked rule counting.
            val open = trip("2026-10-07T17:55", TripStatus.OPEN)
            trips = listOf(trip("2026-10-07T08:05"), open)
            phoneAt("2026-10-07T18:20")
            val check = process()
            check.arm("process start")
            runCurrent()

            phone.advanceTo(at("2026-10-07T18:22"))
            dateSetAhead(check)
            dateSetBack()

            assertEquals(0, shown)
            assertNull(settings.current().nothingRecorded.shownOn)
            assertEquals(at("2026-10-08T12:00"), alarmsAskedFor.last())
        }

    @Test
    fun `a jump before noon leaves the real day its own check`() = runTest {
        // Wednesday 07:10, at home, nothing recorded yet: the check is due at noon.
        phoneAt("2026-10-07T07:10")
        val check = process()
        check.arm("process start")
        runCurrent()
        assertEquals(at("2026-10-07T12:00"), alarmsAskedFor.last())

        // A day ahead: the phone reads Thursday 07:10, Wednesday noon has "passed", and the
        // alarm arrives. It is too early in Wednesday to say anything. Before the fix the
        // alarm was then asked for Thursday noon, and Wednesday had none.
        dateSetAhead(check)
        assertEquals(0, shown)
        dateSetBack()

        assertEquals(at("2026-10-07T12:00"), alarmsAskedFor.last())
        phone.advanceTo(at("2026-10-07T12:00:05"))
        assertTrue(alarmIsDue())
        check.onAlarm {}
        letItLook()
        assertEquals(1, shown)
        assertEquals(wednesday, settings.current().nothingRecorded.shownOn)
    }

    // ---- What the build before this one left in the settings --------------------------------------

    @Test
    fun `a stored 'shown' day that lies after today counts as not shown, and is taken out`() =
        runTest {
            // What the phone held on the evening of 2026-10-07: "shown on Thursday", stored
            // while its date was a day ahead.
            settings.setNothingRecordedShownOn(thursday)
            trips = wednesdaysTrips()
            phoneAt("2026-10-07T19:30:10")
            val check = process()

            check.look("app opened")
            runCurrent()

            assertNull(settings.current().nothingRecorded.shownOn)
            assertEquals(0, shown)
            assertTrue(
                lines().any {
                    it.startsWith(
                        "Nothing-recorded check: the settings said the notification was " +
                            "shown on 2026-10-08, a day after today, 2026-10-07.",
                    )
                },
            )

            // So the real Thursday, with no trip, is told of.
            trips = wednesdaysTrips()
            phone.advanceTo(at("2026-10-08T12:30"))
            check.look("app opened")
            letItLook()
            assertEquals(1, shown)
            assertEquals(thursday, settings.current().nothingRecorded.shownOn)
        }

    @Test
    fun `a stored 'shown' day that is today or earlier is left as it is`() = runTest {
        settings.setNothingRecordedShownOn(wednesday)
        phoneAt("2026-10-07T19:30:10")
        val check = process()

        check.look("app opened")
        letItLook()

        assertEquals(wednesday, settings.current().nothingRecorded.shownOn)
        assertEquals(0, shown)
        assertTrue(lines().last().contains("no second notification: today's was already shown"))
    }
}
