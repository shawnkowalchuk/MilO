package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.categoriesOffered
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
 * "Mark as Business" and "Mark as Personal" against the stand-in trips table, which matches a
 * trip the way the real update does: what is stored afterwards, what is refused, and the one
 * line each attempt leaves in the event log. Delete, Restore and Count are in
 * `TripCorrectionsTest`.
 */
class TripMarkingTest {
    private val trips = FakeTripDao()
    private val log = FakeEventLogDao()
    private val nowMs = 1_791_028_800_000L
    private val corrections =
        TripCorrections(TripRepository(trips), EventLogRepository(log)) { nowMs }

    /** Stores a trip as it is after the schedule has sorted it. */
    private fun stored(
        category: TripCategory?,
        status: TripStatus = TripStatus.FINISHED,
        ranPastSchedule: Boolean = false,
        ignored: Boolean = false,
    ): Trip {
        val open = status == TripStatus.OPEN
        val trip =
            Trip(
                id = trips.rows.size + 1L,
                startedAtMs = nowMs - 60 * MINUTE_MS,
                endedAtMs = (nowMs - 30 * MINUTE_MS).takeUnless { open },
                status = status,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                distanceMetres = if (open) 0.0 else 12_340.0,
                startAddress = "12 Shop Rd, Edmonton".takeUnless { open },
                endAddress = "48 Main St, Leduc".takeUnless { open },
                category = category,
                ranPastSchedule = ranPastSchedule,
                ignoredOutsideSchedule = ignored,
            )
        trips.rows += trip
        return trip
    }

    private fun row(id: Long): Trip = trips.rows.single { it.id == id }

    private fun logged(category: EventCategory): List<String> =
        log.entries.filter { it.category == category }.map { it.message }

    // ---- What a marking stores --------------------------------------------------------------------

    @Test
    fun `marking writes the category and that it was set by hand, and nothing else`() = runTest {
        val trip = stored(TripCategory.BUSINESS, ranPastSchedule = true)

        assertTrue(corrections.mark(trip.id, TripCategory.PERSONAL))

        assertEquals(
            trip.copy(category = TripCategory.PERSONAL, categorySetByHand = true),
            row(trip.id),
        )
    }

    @Test
    fun `a trip can be marked back, and stays set by hand`() = runTest {
        val trip = stored(TripCategory.BUSINESS, ranPastSchedule = true)

        assertTrue(corrections.mark(trip.id, TripCategory.PERSONAL))
        assertTrue(corrections.mark(trip.id, TripCategory.BUSINESS))

        // Everything it had is still there, the ran-past-schedule flag included.
        assertEquals(trip.copy(categorySetByHand = true), row(trip.id))
    }

    @Test
    fun `a trip that is not sorted yet can be marked as either`() = runTest {
        val one = stored(category = null)
        val other = stored(category = null)

        assertTrue(corrections.mark(one.id, TripCategory.BUSINESS))
        assertTrue(corrections.mark(other.id, TripCategory.PERSONAL))

        assertEquals(TripCategory.BUSINESS, row(one.id).category)
        assertEquals(TripCategory.PERSONAL, row(other.id).category)
        assertTrue(row(one.id).categorySetByHand && row(other.id).categorySetByHand)
    }

    @Test
    fun `marking a kept trip Personal does not discard it`() = runTest {
        // Whatever the setting for trips outside the schedule says: that setting is applied
        // when a trip is finalised, and this class cannot even read it.
        val trip = stored(TripCategory.BUSINESS)

        assertTrue(corrections.mark(trip.id, TripCategory.PERSONAL))

        assertEquals(TripStatus.FINISHED, row(trip.id).status)
        assertFalse(row(trip.id).ignoredOutsideSchedule)
    }

    @Test
    fun `an ignored trip that was counted can be marked Business like any other`() = runTest {
        val ignored = stored(TripCategory.PERSONAL, TripStatus.DISCARDED, ignored = true)
        assertTrue(corrections.apply(ignored.id, TripCorrection.COUNT))

        assertTrue(corrections.mark(ignored.id, TripCategory.BUSINESS))

        assertEquals(TripStatus.FINISHED, row(ignored.id).status)
        assertEquals(TripCategory.BUSINESS, row(ignored.id).category)
    }

    // ---- What is refused --------------------------------------------------------------------------

    @Test
    fun `marking a trip as what it already is changes nothing and is not set by hand`() = runTest {
        val trip = stored(TripCategory.BUSINESS)

        assertFalse(corrections.mark(trip.id, TripCategory.BUSINESS))

        assertEquals(trip, row(trip.id))
        assertEquals(
            listOf(
                "Trip 1: mark as Business refused on the Trips screen: it is Business already. " +
                    "Nothing changed",
            ),
            logged(EventCategory.TRIP),
        )
    }

    @Test
    fun `only a finished trip can be marked`() = runTest {
        val open = stored(category = null, status = TripStatus.OPEN)
        val deleted = stored(TripCategory.BUSINESS, TripStatus.DELETED)
        val discarded = stored(TripCategory.BUSINESS, TripStatus.DISCARDED)

        for (trip in listOf(open, deleted, discarded)) {
            assertFalse(corrections.mark(trip.id, TripCategory.PERSONAL))
            assertEquals(trip, row(trip.id))
        }
        assertFalse(corrections.mark(99, TripCategory.PERSONAL))

        assertEquals(
            listOf(
                "Trip 1: mark as Personal refused on the Trips screen: it is still being " +
                    "recorded. Nothing changed",
                "Trip 2: mark as Personal refused on the Trips screen: it is deleted. " +
                    "Nothing changed",
                "Trip 3: mark as Personal refused on the Trips screen: it is discarded. " +
                    "Nothing changed",
                "Trip 99: mark as Personal refused on the Trips screen: there is no such trip. " +
                    "Nothing changed",
            ),
            logged(EventCategory.TRIP),
        )
    }

    @Test
    fun `what the screen offers is exactly what storage accepts`() = runTest {
        // Every status with every category, and every category it could be marked as.
        for (status in TripStatus.entries) {
            for (has in TripCategory.entries + null) {
                for (wanted in TripCategory.entries) {
                    val trip = stored(has, status)
                    val offered = wanted in categoriesOffered(status, has)

                    val marked = corrections.mark(trip.id, wanted)

                    assertEquals("$status, $has to $wanted", offered, marked)
                }
            }
        }
    }

    // ---- What the log says ------------------------------------------------------------------------

    @Test
    fun `every marking leaves one line that says what the trip was`() = runTest {
        val sorted = stored(TripCategory.BUSINESS)
        val unsorted = stored(category = null)

        corrections.mark(sorted.id, TripCategory.PERSONAL)
        corrections.mark(sorted.id, TripCategory.BUSINESS)
        corrections.mark(unsorted.id, TripCategory.PERSONAL)

        assertEquals(
            listOf(
                "Trip 1: marked Personal by hand on the Trips screen (12340 m, was Business). " +
                    "Nothing else about it changed, and the work schedule no longer decides " +
                    "what it is",
                "Trip 1: marked Business by hand on the Trips screen (12340 m, was Personal). " +
                    "Nothing else about it changed, and the work schedule no longer decides " +
                    "what it is",
                "Trip 2: marked Personal by hand on the Trips screen (12340 m, was not sorted). " +
                    "Nothing else about it changed, and the work schedule no longer decides " +
                    "what it is",
            ),
            logged(EventCategory.TRIP),
        )
        assertTrue(log.entries.all { it.atMs == nowMs })
    }

    @Test
    fun `a marking that storage fails on changes nothing and is logged as an error`() = runTest {
        val trip = stored(TripCategory.BUSINESS)
        trips.failNextCategoryWrite = IOException("the disk is full")

        assertFalse(corrections.mark(trip.id, TripCategory.PERSONAL))

        assertEquals(trip, row(trip.id))
        val line = log.entries.single()
        assertEquals(EventCategory.ERROR, line.category)
        assertEquals("Trip 1: mark as Personal failed in storage", line.message)
        assertTrue(line.detail.orEmpty().contains("the disk is full"))
    }
}
