package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE_MS = 60_000L

/**
 * Delete, Restore and "Count this trip" against the stand-in trips table, which matches on a
 * trip's status the way the real update does: what is stored afterwards, what is refused, and
 * the one line each attempt leaves in the event log.
 */
class TripCorrectionsTest {
    private val trips = FakeTripDao()
    private val log = FakeEventLogDao()
    private val nowMs = 1_791_028_800_000L
    private val corrections =
        TripCorrections(TripRepository(trips), EventLogRepository(log)) { nowMs }

    /** Stores a trip as it is after it has closed, with addresses if it is a finished one. */
    private fun stored(status: TripStatus, metres: Double = 12_340.0): Trip {
        val open = status == TripStatus.OPEN
        val trip =
            Trip(
                id = trips.rows.size + 1L,
                startedAtMs = nowMs - 60 * MINUTE_MS,
                endedAtMs = (nowMs - 30 * MINUTE_MS).takeUnless { open },
                status = status,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                distanceMetres = if (open) 0.0 else metres,
                startLatitude = 53.5.takeUnless { open },
                startLongitude = (-113.5).takeUnless { open },
                endLatitude = 53.3.takeUnless { open },
                endLongitude = (-113.6).takeUnless { open },
                startAddress = "12 Shop Rd, Edmonton".takeIf { status == TripStatus.FINISHED },
                endAddress = "48 Main St, Leduc".takeIf { status == TripStatus.FINISHED },
                addressLastAttemptAtMs = (nowMs - 29 * MINUTE_MS).takeIf {
                    status == TripStatus.FINISHED
                },
            )
        trips.rows += trip
        return trip
    }

    private fun row(id: Long): Trip = trips.rows.single { it.id == id }

    private fun logged(category: EventCategory): List<String> =
        log.entries.filter { it.category == category }.map { it.message }

    // ---- Delete and restore -----------------------------------------------------------------------

    @Test
    fun `a deleted trip keeps everything but its status`() = runTest {
        val trip = stored(TripStatus.FINISHED)

        assertTrue(corrections.apply(trip.id, TripCorrection.DELETE))

        assertEquals(trip.copy(status = TripStatus.DELETED), row(trip.id))
    }

    @Test
    fun `restore puts a deleted trip back exactly as it was`() = runTest {
        val trip = stored(TripStatus.FINISHED)

        corrections.apply(trip.id, TripCorrection.DELETE)
        assertTrue(corrections.apply(trip.id, TripCorrection.RESTORE))

        // Times, distance, positions, addresses and the count of lookups: all as before.
        assertEquals(trip, row(trip.id))
    }

    @Test
    fun `a change to one trip leaves every other trip as it was`() = runTest {
        val before = stored(TripStatus.FINISHED, metres = 5_000.0)
        val trip = stored(TripStatus.FINISHED)
        val after = stored(TripStatus.DISCARDED, metres = 120.0)

        corrections.apply(trip.id, TripCorrection.DELETE)

        assertEquals(before, row(before.id))
        assertEquals(after, row(after.id))
    }

    @Test
    fun `a trip that is still being recorded cannot be deleted`() = runTest {
        val recording = stored(TripStatus.OPEN)

        assertFalse(corrections.apply(recording.id, TripCorrection.DELETE))

        assertEquals(recording, row(recording.id))
        assertEquals(
            listOf(
                "Trip 1: delete refused on the Trips screen: it is still being recorded. " +
                    "Nothing changed",
            ),
            logged(EventCategory.TRIP),
        )
    }

    @Test
    fun `a trip that is still being recorded can be neither restored nor counted`() = runTest {
        val recording = stored(TripStatus.OPEN)

        assertFalse(corrections.apply(recording.id, TripCorrection.RESTORE))
        assertFalse(corrections.apply(recording.id, TripCorrection.COUNT))

        // Still the one open trip, for the trip rules to end.
        assertEquals(recording, row(recording.id))
    }

    @Test
    fun `a second delete changes nothing and says so`() = runTest {
        val trip = stored(TripStatus.FINISHED)

        assertTrue(corrections.apply(trip.id, TripCorrection.DELETE))
        assertFalse(corrections.apply(trip.id, TripCorrection.DELETE))

        assertEquals(TripStatus.DELETED, row(trip.id).status)
        assertEquals(
            "Trip 1: delete refused on the Trips screen: it is deleted, not finished. " +
                "Nothing changed",
            logged(EventCategory.TRIP).last(),
        )
    }

    @Test
    fun `a trip that does not exist is refused`() = runTest {
        assertFalse(corrections.apply(99, TripCorrection.DELETE))

        assertEquals(
            listOf(
                "Trip 99: delete refused on the Trips screen: there is no such trip. " +
                    "Nothing changed",
            ),
            logged(EventCategory.TRIP),
        )
    }

    @Test
    fun `every change is made from the one status it starts from, and from no other`() = runTest {
        for (correction in TripCorrection.entries) {
            for (status in TripStatus.entries) {
                val trip = stored(status)
                val case = "$correction from $status"
                val allowed = status == correction.from

                assertEquals(case, allowed, corrections.apply(trip.id, correction))

                val after = if (allowed) correction.to else status
                assertEquals(case, trip.copy(status = after), row(trip.id))
            }
        }
    }

    // ---- Count this trip --------------------------------------------------------------------------

    @Test
    fun `counting a discarded trip makes it an ordinary finished trip`() = runTest {
        val discarded = stored(TripStatus.DISCARDED, metres = 250.0)

        assertTrue(corrections.apply(discarded.id, TripCorrection.COUNT))

        // Its positions are kept, so the address lookup can find its addresses now.
        assertEquals(discarded.copy(status = TripStatus.FINISHED), row(discarded.id))
    }

    @Test
    fun `a trip counted after all can be deleted like any other`() = runTest {
        val discarded = stored(TripStatus.DISCARDED, metres = 250.0)

        corrections.apply(discarded.id, TripCorrection.COUNT)
        assertTrue(corrections.apply(discarded.id, TripCorrection.DELETE))

        assertEquals(TripStatus.DELETED, row(discarded.id).status)
    }

    @Test
    fun `a discarded trip cannot be deleted or restored, only counted`() = runTest {
        val discarded = stored(TripStatus.DISCARDED, metres = 250.0)

        assertFalse(corrections.apply(discarded.id, TripCorrection.DELETE))
        assertFalse(corrections.apply(discarded.id, TripCorrection.RESTORE))

        assertEquals(discarded, row(discarded.id))
    }

    // ---- The event log ----------------------------------------------------------------------------

    @Test
    fun `every delete, restore and count writes one line`() = runTest {
        val trip = stored(TripStatus.FINISHED)
        val discarded = stored(TripStatus.DISCARDED, metres = 250.0)

        corrections.apply(trip.id, TripCorrection.DELETE)
        corrections.apply(trip.id, TripCorrection.RESTORE)
        corrections.apply(discarded.id, TripCorrection.COUNT)

        assertEquals(
            listOf(
                "Trip 1: deleted on the Trips screen (12340 m, was finished). It is no longer " +
                    "counted. Its row and its GPS points are kept, and Restore puts it back",
                "Trip 1: restored on the Trips screen (12340 m, was deleted). It is counted again",
                "Trip 2: counted on the Trips screen (250 m, was discarded). It is now a " +
                    "finished trip",
            ),
            logged(EventCategory.TRIP),
        )
        assertEquals(3, log.entries.size)
        assertTrue(log.entries.all { it.atMs == nowMs })
    }

    @Test
    fun `a change that storage refuses is logged as a failure, and nothing is changed`() = runTest {
        val trip = stored(TripStatus.FINISHED)
        trips.failNextStatusChange = IOException("disk full")

        assertFalse(corrections.apply(trip.id, TripCorrection.DELETE))

        assertEquals(trip, row(trip.id))
        val failure = log.entries.single()
        assertEquals(EventCategory.ERROR, failure.category)
        assertEquals("Trip 1: delete failed in storage", failure.message)
        assertTrue(failure.detail.orEmpty().contains("disk full"))
    }
}
