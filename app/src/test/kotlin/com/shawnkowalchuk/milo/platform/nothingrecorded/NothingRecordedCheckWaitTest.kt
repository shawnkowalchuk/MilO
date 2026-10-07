package com.shawnkowalchuk.milo.platform.nothingrecorded

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.setNothingRecordedTime
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A settings file whose every read takes a moment, as the real one's does: the value is taken,
 * and whatever else is ready to run gets its turn before it is handed over. Two looks that are
 * not kept apart then both read "not shown today" before either has stored that it was.
 */
private class SlowSettingsFile(private val file: FakeSettingsFile) : DataStore<Preferences> {
    override val data: Flow<Preferences> = file.data.onEach { yield() }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = file.updateData(transform)
}

/**
 * Three things around a look of the "nothing recorded" check, beside the stand-ins of
 * `NothingRecordedCheckFixture`: the wait before a notification, for a trip that may be
 * starting; one look at a time; and the watch on the work schedule, which the daily alarm is
 * worked out from. The wait beside a real trip controller is in
 * `NothingRecordedWithControllerTest`.
 */
// runCurrent() and advanceTimeBy() are how a test lets the check's coroutines run. The API is
// marked experimental by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class NothingRecordedCheckWaitTest : NothingRecordedCheckFixture() {
    // ---- The wait before a notification -----------------------------------------------------------

    @Test
    fun `no trip is said only by a second look, and nothing is written before it`() = runTest {
        val check = check()

        check.look("app opened")
        runCurrent()
        assertEquals(0, shown)
        assertEquals(emptyList<String>(), lines())

        // A moment before the wait is over.
        advanceTimeBy(TRIP_START_WAIT_MS)
        assertEquals(0, shown)
        runCurrent()

        assertEquals(1, shown)
        assertEquals(2, tripReads)
        // One line for the asking, as for every asking, and one for the notification.
        assertEquals(2, lines().size)
    }

    @Test
    fun `a trip stored during the wait means no notification, and the line says why`() = runTest {
        val check = check()
        check.look("app opened")
        runCurrent()

        // The trip service has come up: the trip is stored, and published a moment later.
        val started = trip("2026-10-06T12:30", TripStatus.OPEN)
        trips = listOf(started)
        letItLook()

        assertEquals(0, shown)
        assertNull(settings.current().nothingRecorded.shownOn)
        assertEquals(
            listOf(
                "Nothing-recorded check (app opened): no notification: 1 trip was started or " +
                    "is being recorded today, Tue 2026-10-06. A first look, a moment earlier, " +
                    "found no trip and waited before notifying: one was just being started.",
            ),
            lines(),
        )
    }

    @Test
    fun `an answer other than no trip is acted on at once, without a second look`() = runTest {
        trips = listOf(trip("2026-10-06T08:12"))

        check().look("app opened")
        runCurrent()

        assertEquals(1, tripReads)
        assertEquals(1, withdrawn)
        assertEquals(1, lines().size)
    }

    // ---- One look at a time -----------------------------------------------------------------------

    @Test
    fun `two looks at the same moment bring one notification and one line that it was shown`() =
        runTest {
            val check = check(SlowSettingsFile(settingsFile))

            // The daily alarm starts MilO's process: the look of the process start and the
            // alarm's own run side by side.
            check.arm("process start")
            check.onAlarm {}
            letItLook()

            assertEquals(1, shown)
            assertEquals(1, lines().count { it.startsWith("Nothing-recorded notification shown") })
            assertEquals(
                1,
                lines().count { it.contains("no second notification: today's was already shown") },
            )
        }

    // ---- The work schedule ------------------------------------------------------------------------

    @Test
    fun `a day's start that is moved moves the daily alarm, without MilO being opened`() = runTest {
        // Set to 07:00, and Wednesday's hours start at 10:00: Wednesday is checked from 10:00.
        settings.setNothingRecordedTime(LocalTime.of(7, 0))
        val wednesdayAtTen =
            settings.current().schedule.withStart(DayOfWeek.WEDNESDAY, LocalTime.of(10, 0))
        settings.setSchedule(checkNotNull(wednesdayAtTen))
        trips = listOf(trip("2026-10-06T08:12"))
        nowMs = at("2026-10-06T19:00")
        check().arm("process start")
        runCurrent()
        assertEquals(listOf(at("2026-10-07T10:00")), alarmsAskedFor)

        // On Tuesday evening Shawn moves Wednesday's start to 08:00, on the Settings screen.
        val wednesdayAtEight =
            settings.current().schedule.withStart(DayOfWeek.WEDNESDAY, LocalTime.of(8, 0))
        settings.setSchedule(checkNotNull(wednesdayAtEight))
        runCurrent()

        assertEquals(at("2026-10-07T08:00"), alarmsAskedFor.last())
        assertEquals(2, alarmsAskedFor.size)
        assertEquals(
            "Nothing-recorded check: the next daily look is asked for at 2026-10-07 08:00:00, " +
                "or soon after it (the work schedule was changed). MilO also looks at every " +
                "start and every time it is opened.",
            lines()[2],
        )
    }

    @Test
    fun `a day switched off in the schedule takes its notification away at once`() = runTest {
        check().arm("process start")
        letItLook()
        assertEquals(1, shown)
        assertEquals(0, withdrawn)

        settings.setSchedule(settings.current().schedule.withTracked(DayOfWeek.TUESDAY, false))
        runCurrent()

        assertEquals(1, withdrawn)
        assertEquals(
            "Nothing-recorded check (the work schedule was changed): no notification: today, " +
                "Tue 2026-10-06, is not a work day in the schedule.",
            lines().last(),
        )
    }

    @Test
    fun `a setting that is not the schedule does not have the alarm asked for again`() = runTest {
        nowMs = at("2026-10-06T09:14")
        check().arm("process start")
        runCurrent()
        val linesBefore = lines().size

        settings.setGracePeriodSeconds(300)
        settings.setSoundEnabled(false)
        runCurrent()

        assertEquals(1, alarmsAskedFor.size)
        assertEquals(linesBefore, lines().size)
        assertTrue(log.entries.none { it.category == EventCategory.ERROR })
    }
}
