package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.trip.isCounted
import java.io.IOException
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saving an edit against the stand-in trips table, through the repository the screen uses:
 * what is stored afterwards, what is refused, and the one line each attempt leaves in the
 * event log. Adding a trip and restoring one are in `TripAddingTest` and `TripRestoringTest`.
 */
class TripEditingTest : TripEditingFixture() {
    @Test
    fun `a saved edit is stored, marks the trip, and leaves one line with old and new`() = runTest {
        val trip = recorded()

        val result = save(trip) { it.copy(end = LocalTime.of(8, 45), kilometres = "13") }

        assertEquals(SaveResult.Stored, result)
        assertEquals(
            trip.copy(
                endedAtMs = local("2026-10-05T08:45:00"),
                distanceMetres = 13_000.0,
                editedByHand = true,
                recordedStartedAtMs = trip.startedAtMs,
                recordedEndedAtMs = trip.endedAtMs,
                recordedDistanceMetres = trip.distanceMetres,
            ),
            row(trip.id),
        )
        assertEquals(
            listOf(
                "Trip 1: changed by hand on the edit screen: end 2026-10-05 08:39:02 → " +
                    "2026-10-05 08:45:00; distance 12345 m → 13000 m (times in " +
                    "America/Edmonton). It is now marked as edited, and \"Restore recorded " +
                    "values\" puts back what MilO recorded",
            ),
            logged(EventCategory.TRIP),
        )
    }

    @Test
    fun `an edited trip is still a finished, counted trip`() = runTest {
        val trip = recorded()

        save(trip) { it.copy(kilometres = "13") }

        assertEquals(TripStatus.FINISHED, row(trip.id).status)
        assertTrue(row(trip.id).isCounted)
    }

    @Test
    fun `a save that changes nothing writes nothing and says so`() = runTest {
        val trip = recorded()

        assertEquals(SaveResult.Stored, save(trip) { it })

        assertEquals(trip, row(trip.id))
        assertEquals(
            listOf("Trip 1: saved on the edit screen with nothing changed. Nothing was written"),
            logged(EventCategory.TRIP),
        )
    }

    @Test
    fun `a form with a problem stores nothing and logs nothing`() = runTest {
        val trip = recorded()

        val result = save(trip) { it.copy(end = LocalTime.of(8, 0), kilometres = "-1") }

        assertEquals(
            SaveResult.Invalid(
                listOf(FormProblem.END_NOT_AFTER_START, FormProblem.DISTANCE_NEGATIVE),
                FormCheck(nowMs, recordingSinceMs = null),
            ),
            result,
        )
        assertEquals(trip, row(trip.id))
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `a trip may not be moved over the trip that is being recorded`() = runTest {
        val trip = recorded()
        val recording = recorded(TripStatus.OPEN).copy(startedAtMs = local("2026-10-05T13:30:00"))
        trips.rows[trips.rows.lastIndex] = recording

        val result = save(trip) { it.copy(end = LocalTime.of(13, 45)) }

        assertEquals(
            SaveResult.Invalid(
                listOf(FormProblem.OVERLAPS_RECORDING),
                FormCheck(nowMs, recordingSinceMs = recording.startedAtMs),
            ),
            result,
        )
        assertEquals(trip, row(trip.id))
    }

    @Test
    fun `a trip may be edited while another is recording, as long as it ends before it`() =
        runTest {
            val trip = recorded()
            val recording = recorded(
                TripStatus.OPEN,
            ).copy(startedAtMs = local("2026-10-05T13:30:00"))
            trips.rows[trips.rows.lastIndex] = recording

            assertEquals(SaveResult.Stored, save(trip) { it.copy(end = LocalTime.of(9, 0)) })

            assertEquals(local("2026-10-05T09:00:00"), row(trip.id).endedAtMs)
            assertEquals("The recording is not touched", recording, row(recording.id))
        }

    @Test
    fun `a trip that was deleted while the form was open is not written to`() = runTest {
        val trip = recorded()
        trips.rows[0] = trip.copy(status = TripStatus.DELETED)

        val result = save(trip) { it.copy(kilometres = "13") }

        assertEquals(SaveResult.Failed, result)
        assertEquals(trip.copy(status = TripStatus.DELETED), row(trip.id))
        assertEquals(
            listOf("Trip 1: edit refused: it is deleted, not finished. Nothing changed"),
            logged(EventCategory.TRIP),
        )
    }

    @Test
    fun `a trip that is recording, discarded or deleted cannot be opened for an edit`() = runTest {
        val finished = recorded()
        val others =
            listOf(TripStatus.OPEN, TripStatus.DISCARDED, TripStatus.DELETED).map(::recorded)

        assertEquals(finished, editing.findEditable(finished.id))
        for (trip in others) assertNull("${trip.status}", editing.findEditable(trip.id))
        assertNull("No such trip", editing.findEditable(99))
    }

    @Test
    fun `a failure of storage is logged, and nothing is changed`() = runTest {
        val trip = recorded()
        trips.failNextByHandWrite = IOException("the disk is full")

        val result = save(trip) { it.copy(kilometres = "13") }

        assertEquals(SaveResult.Failed, result)
        assertEquals(trip, row(trip.id))
        assertEquals(listOf("Trip 1: edit failed in storage"), logged(EventCategory.ERROR))
        assertTrue(log.entries.single().detail.orEmpty().contains("the disk is full"))
    }

    @Test
    fun `an address the lookup found while the form was open is not overwritten`() = runTest {
        val lookingUp = recorded().copy(startAddress = null, endAddress = null)
        trips.rows[0] = lookingUp
        // The form is opened, and then the lookup's answer arrives.
        val form = formFor(lookingUp, edmonton)
        trips.rows[0] = lookingUp.copy(startAddress = "12 Shop Rd, Edmonton")

        // Saved with the from field as it was opened: empty, and untouched.
        editing.save(lookingUp, form.copy(kilometres = "13"), schedule, edmonton)

        assertEquals("12 Shop Rd, Edmonton", row(lookingUp.id).startAddress)
        assertFalse(row(lookingUp.id).startAddressByHand)
    }
}
