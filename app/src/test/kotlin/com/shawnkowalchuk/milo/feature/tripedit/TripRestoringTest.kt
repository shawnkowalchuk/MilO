package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.IOException
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Restore recorded values" against the stand-in trips table, and the work schedule the edit
 * screen sorts by: what is stored, what is refused, and what the event log says.
 */
class TripRestoringTest : TripEditingFixture() {
    @Test
    fun `restore puts the trip back as recorded and says what went back`() = runTest {
        val trip = recorded()
        save(trip) { it.copy(start = LocalTime.of(8, 0), kilometres = "20", from = "Home") }

        assertTrue(editing.restore(trip.id, schedule, edmonton))

        assertEquals(
            trip.copy(
                // The address he typed is gone, and is the lookup's to find again.
                startAddress = null,
                recordedStartedAtMs = trip.startedAtMs,
                recordedEndedAtMs = trip.endedAtMs,
                recordedDistanceMetres = trip.distanceMetres,
            ),
            row(trip.id),
        )
        assertEquals(
            "Trip 1: recorded values restored on the edit screen: start 2026-10-05 08:00:00 → " +
                "2026-10-05 08:14:27; distance 20000 m → 12345 m; from \"Home\" → none (times " +
                "in America/Edmonton). It is no longer marked as edited. An address typed by " +
                "hand was removed and is looked up again.",
            logged(EventCategory.TRIP).last(),
        )
    }

    @Test
    fun `a trip that was never edited, or was added by hand, has nothing to restore`() = runTest {
        val untouched = recorded()
        editing.add(missed, schedule, edmonton)

        assertFalse(editing.restore(untouched.id, schedule, edmonton))
        assertFalse(editing.restore(2, schedule, edmonton))

        assertEquals(untouched, row(untouched.id))
        assertEquals(
            listOf(
                "Trip 1: restore recorded values refused: it has no recorded values to put " +
                    "back. Nothing changed",
                "Trip 2: restore recorded values refused: it has no recorded values to put " +
                    "back. Nothing changed",
            ),
            logged(EventCategory.TRIP).takeLast(2),
        )
    }

    @Test
    fun `a failure to restore is logged, and the trip stays edited`() = runTest {
        val trip = recorded()
        save(trip) { it.copy(kilometres = "20") }
        val edited = row(trip.id)
        trips.failNextByHandWrite = IOException("the disk is full")

        assertFalse(editing.restore(trip.id, schedule, edmonton))

        assertEquals(edited, row(trip.id))
        assertEquals(
            listOf("Trip 1: restore recorded values failed in storage"),
            logged(EventCategory.ERROR),
        )
    }

    // ---- The work schedule ----------------------------------------------------------------------

    @Test
    fun `the schedule is read from the settings, and an unreadable one is logged`() = runTest {
        val unreadable =
            TripEditing(
                trips = TripRepository(trips),
                settings = SettingsStore(UnreadableSettingsFile),
                eventLog = EventLogRepository(log),
                clock = { nowMs },
            )

        assertEquals(DEFAULT_WORK_SCHEDULE, editing.schedule())
        assertNull(unreadable.schedule())
        assertEquals(
            listOf("The edit screen could not read the work schedule"),
            logged(EventCategory.ERROR),
        )
    }

    @Test
    fun `without a schedule a moved trip is left unsorted for the catch-up`() = runTest {
        val trip = recorded()

        editing.save(
            trip,
            formFor(trip, edmonton, DistanceUnit.KILOMETRES).copy(start = LocalTime.of(7, 0)),
            schedule = null,
            edmonton,
        )

        assertNull(row(trip.id).category)
        assertFalse(row(trip.id).categorySetByHand)
    }
}
