package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.trip.ByHandMark
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.byHandMark
import com.shawnkowalchuk.milo.data.trip.isCounted
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adding a trip MilO missed, against the stand-in trips table: what is stored, what is refused,
 * and the line it leaves in the event log.
 */
class TripAddingTest : TripEditingFixture() {
    @Test
    fun `a missed trip is stored as a finished trip marked as added by hand`() = runTest {
        assertEquals(SaveResult.Stored, editing.add(missed, schedule, edmonton))

        assertEquals(
            Trip(
                id = 1,
                startedAtMs = local("2026-10-05T10:00:00"),
                endedAtMs = local("2026-10-05T10:40:00"),
                status = TripStatus.FINISHED,
                startedBy = TripStartCause.MANUAL,
                truckSeen = false,
                distanceMetres = 23_400.0,
                startAddress = "Shop",
                endAddress = "Site 7",
                category = TripCategory.BUSINESS,
                addedByHand = true,
                startAddressByHand = true,
                endAddressByHand = true,
            ),
            row(1),
        )
        assertEquals(ByHandMark.ADDED, row(1).byHandMark)
        assertTrue(row(1).isCounted)
        assertEquals(
            listOf(
                "Trip 1: added by hand on the edit screen: 2026-10-05 10:00:00 to " +
                    "2026-10-05 10:40:00 (America/Edmonton), 23400 m, from \"Shop\" to " +
                    "\"Site 7\", Business (sorted by the work schedule). It has no GPS points, " +
                    "is marked as added by hand and is counted like any finished trip",
            ),
            logged(EventCategory.TRIP),
        )
    }

    @Test
    fun `a missed trip outside the work hours is Personal, unless Business is chosen`() = runTest {
        val evening = missed.copy(date = LocalDate.of(2026, 10, 4), start = LocalTime.of(19, 0))
            .copy(end = LocalTime.of(19, 40))

        editing.add(evening, schedule, edmonton)
        editing.add(evening.copy(chosenCategory = TripCategory.BUSINESS), schedule, edmonton)

        assertEquals(TripCategory.PERSONAL, row(1).category)
        assertFalse(row(1).categorySetByHand)
        assertEquals(TripCategory.BUSINESS, row(2).category)
        assertTrue(row(2).categorySetByHand)
    }

    @Test
    fun `a missed trip that is not filled in properly is not added`() = runTest {
        val result = editing.add(missed.copy(end = null, kilometres = "x"), schedule, edmonton)

        assertEquals(
            SaveResult.Invalid(
                listOf(FormProblem.END_MISSING, FormProblem.DISTANCE_NOT_A_NUMBER),
                FormCheck(nowMs, recordingSinceMs = null),
            ),
            result,
        )
        assertTrue(trips.rows.isEmpty())
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `a missed trip may not be added over the trip that is being recorded`() = runTest {
        val recording = recorded(TripStatus.OPEN)

        val result = editing.add(missed, schedule, edmonton)

        // The recording started at 08:14, and the missed trip would run from 10:00.
        assertEquals(
            SaveResult.Invalid(
                listOf(FormProblem.OVERLAPS_RECORDING),
                FormCheck(nowMs, recordingSinceMs = recording.startedAtMs),
            ),
            result,
        )
        assertEquals(listOf(recording), trips.rows)
    }

    @Test
    fun `a trip that was added by hand is edited like any other and stays added by hand`() =
        runTest {
            editing.add(missed, schedule, edmonton)
            val added = row(1)

            val result = save(added) { it.copy(kilometres = "25", to = "Site 9") }

            assertEquals(SaveResult.Stored, result)
            assertEquals(added.copy(distanceMetres = 25_000.0, endAddress = "Site 9"), row(1))
            assertTrue(logged(EventCategory.TRIP).last().endsWith("stays marked so"))
        }

    @Test
    fun `a failure to add is logged, and no trip is stored`() = runTest {
        trips.failNextInsert = IOException("the disk is full")

        assertEquals(SaveResult.Failed, editing.add(missed, schedule, edmonton))

        assertTrue(trips.rows.isEmpty())
        assertEquals(
            listOf("A trip typed in by hand failed in storage"),
            logged(EventCategory.ERROR),
        )
    }
}
