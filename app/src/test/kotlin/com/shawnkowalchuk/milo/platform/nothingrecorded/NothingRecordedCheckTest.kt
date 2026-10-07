package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.settings.setNothingRecordedEnabled
import com.shawnkowalchuk.milo.data.settings.setNothingRecordedTime
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "nothing recorded" check beside stand-ins for the alarm, the notification, the settings
 * file, the stored trips and the trip controller: what it shows, when it withdraws, that a day
 * brings one notification however often it looks, and that every asking leaves a line. What
 * goes wrong, a trip controller that does not answer included, is in
 * `NothingRecordedCheckFailureTest`; the wait before a notification, the lock and the watch on
 * the schedule are in `NothingRecordedCheckWaitTest`.
 */
// runCurrent() is how a test lets the check's coroutines run, and letItLook() where a
// notification is due (the fixture says why). The API is marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class NothingRecordedCheckTest : NothingRecordedCheckFixture() {
    // ---- Showing ----------------------------------------------------------------------------------

    @Test
    fun `a process start asks for the daily alarm and says that no trip has been recorded`() =
        runTest {
            check().arm("process start")
            letItLook()

            assertEquals(listOf(at("2026-10-07T12:00")), alarmsAskedFor)
            assertEquals(1, shown)
            assertEquals(tuesday, settings.current().nothingRecorded.shownOn)
            assertEquals(
                listOf(
                    "Nothing-recorded check: the next daily look is asked for at 2026-10-07 " +
                        "12:00:00, or soon after it (process start). MilO also looks at every " +
                        "start and every time it is opened.",
                    "Nothing-recorded check (process start): notify: no trip has been started " +
                        "today, Tue 2026-10-06. It is a work day, checked from 12:00, the time " +
                        "set, and it is 12:30.",
                    "Nothing-recorded notification shown for 2026-10-06: \"No trip recorded " +
                        "today\". A tap opens MilO on the Home screen.",
                ),
                lines(),
            )
        }

    @Test
    fun `however often MilO looks, a day brings one notification, and the next day another`() =
        runTest {
            val check = check()

            check.arm("process start")
            letItLook()
            repeat(3) { check.look("app opened") }
            runCurrent()
            // A new process on the same day finds in the settings that it was shown.
            check().look("app opened")
            runCurrent()
            assertEquals(1, shown)
            assertEquals(0, withdrawn)

            nowMs = at("2026-10-07T12:00")
            var done = 0
            check.onAlarm { done++ }
            letItLook()

            assertEquals(2, shown)
            // The alarm asked for the day after, and told the receiver when it was finished.
            assertEquals(at("2026-10-08T12:00"), alarmsAskedFor.last())
            assertEquals(1, done)
        }

    @Test
    fun `every asking leaves a line with what was decided, also when nothing has changed`() =
        runTest {
            nowMs = at("2026-10-06T09:14")
            val check = check()

            repeat(3) { check.look("app opened") }
            runCurrent()

            val line =
                "Nothing-recorded check (app opened): no notification yet: today, Tue " +
                    "2026-10-06, is checked from 12:00, the time set, and it is 09:14."
            assertEquals(listOf(line, line, line), lines())
            assertEquals(0, shown)
            // The detail holds what the decision went by, and no position or address.
            assertEquals(
                "Time zone America/Edmonton. Switch on, time set 12:00. Tue: tracked, 08:00 " +
                    "to 16:30. Notification last shown: never. Trips looked at: none.",
                log.entries.first().detail,
            )
        }

    @Test
    fun `a set time before the day's start is checked, and its alarm asked for, at the start`() =
        runTest {
            settings.setNothingRecordedTime(LocalTime.of(7, 0))
            nowMs = at("2026-10-06T07:10")

            check().arm("process start")
            runCurrent()

            assertEquals(listOf(at("2026-10-06T08:00")), alarmsAskedFor)
            assertEquals(0, shown)
            assertEquals(
                "Nothing-recorded check (process start): no notification yet: today, Tue " +
                    "2026-10-06, is checked from 08:00, the start of the day's work hours (the " +
                    "time set, 07:00, is earlier), and it is 07:10.",
                lines().last(),
            )
        }

    @Test
    fun `a day that is not a work day brings nothing, and its line says so`() = runTest {
        nowMs = at("2026-10-10T15:00")

        check().look("app opened")
        runCurrent()

        assertEquals(0, shown)
        assertEquals(
            listOf(
                "Nothing-recorded check (app opened): no notification: today, Sat 2026-10-10, " +
                    "is not a work day in the schedule.",
            ),
            lines(),
        )
    }

    // ---- The trips --------------------------------------------------------------------------------

    @Test
    fun `a trip recorded today means no notification`() = runTest {
        trips = listOf(trip("2026-10-06T08:12"), trip("2026-10-06T10:40", TripStatus.DISCARDED))

        check().look("app opened")
        runCurrent()

        assertEquals(0, shown)
        assertEquals(
            listOf(
                "Nothing-recorded check (app opened): no notification: 2 trips were started " +
                    "or are being recorded today, Tue 2026-10-06.",
            ),
            lines(),
        )
        assertTrue(log.entries.single().detail!!.contains("DISCARDED, started 2026-10-06 10:40:00"))
    }

    @Test
    fun `a trip added by hand does not stand in for a recorded one`() = runTest {
        trips = listOf(trip("2026-10-06T08:12").copy(addedByHand = true))

        check().look("app opened")
        letItLook()

        assertEquals(1, shown)
        assertEquals(
            "Nothing-recorded check (app opened): notify: no trip has been started today, Tue " +
                "2026-10-06. It is a work day, checked from 12:00, the time set, and it is " +
                "12:30. One trip of today was added by hand and is not counted.",
            lines().first(),
        )
    }

    @Test
    fun `a trip that is being recorded since before midnight counts`() = runTest {
        trips = listOf(trip("2026-10-05T23:50", TripStatus.OPEN))

        check().look("app opened")
        runCurrent()

        assertEquals(0, shown)
        assertEquals(
            "Nothing-recorded check (app opened): no notification: 1 trip was started or is " +
                "being recorded today, Tue 2026-10-06.",
            lines().single(),
        )
    }

    // ---- Withdrawing ------------------------------------------------------------------------------

    @Test
    fun `a trip that starts afterwards takes the notification away at once`() = runTest {
        val check = check()
        check.look("app opened")
        letItLook()
        assertEquals(1, shown)
        assertEquals(0, withdrawn)

        // The truck connects at one o'clock, and the trip controller publishes the trip.
        nowMs = at("2026-10-06T13:00")
        val started = trip("2026-10-06T13:00", TripStatus.OPEN)
        trips = listOf(started)
        tripActivity.value = recording(started)
        runCurrent()

        assertEquals(1, withdrawn)
        // Nothing was asked and no line was written for it: the trip's own lines say it began.
        assertEquals(2, lines().size)

        // And the next asking agrees.
        check.look("app opened")
        runCurrent()
        assertEquals(1, shown)
        assertEquals(2, withdrawn)
    }

    @Test
    fun `a trip that begins while the trips are being read takes the notification straight back`() =
        runTest {
            // Published by the controller, and not in the rows that were read a moment before.
            tripActivity.value = recording(trip("2026-10-06T12:30", TripStatus.OPEN))
            val check = check()
            runCurrent()
            assertEquals(1, withdrawn)

            check.look("app opened")
            letItLook()

            assertEquals(1, shown)
            assertEquals(2, withdrawn)
        }

    @Test
    fun `switched off, the alarm is taken back and a notification that is showing goes`() =
        runTest {
            val check = check()
            check.arm("process start")
            letItLook()
            assertEquals(1, shown)

            settings.setNothingRecordedEnabled(false)
            check.arm("the check was changed in Settings")
            runCurrent()

            assertEquals(1, alarmsCancelled)
            assertEquals(1, alarmsAskedFor.size)
            assertEquals(1, withdrawn)
            assertEquals(
                listOf(
                    "Nothing-recorded check: no daily look is asked for, because the check is " +
                        "switched off in Settings (the check was changed in Settings).",
                    "Nothing-recorded check (the check was changed in Settings): no " +
                        "notification: the check is switched off in Settings.",
                ),
                lines().takeLast(2),
            )

            // Switched on again on the same day, it does not come a second time.
            settings.setNothingRecordedEnabled(true)
            check.arm("the check was changed in Settings")
            runCurrent()
            assertEquals(1, shown)
            assertEquals(2, alarmsAskedFor.size)
        }

    @Test
    fun `after midnight yesterday's notification is taken away, and noon brings today's`() =
        runTest {
            val check = check()
            check.look("app opened")
            letItLook()

            nowMs = at("2026-10-07T06:00")
            check.look("app opened")
            runCurrent()
            assertEquals(1, withdrawn)
            assertEquals(1, shown)

            nowMs = at("2026-10-07T12:00")
            check.onAlarm {}
            letItLook()
            assertEquals(2, shown)
        }
}
