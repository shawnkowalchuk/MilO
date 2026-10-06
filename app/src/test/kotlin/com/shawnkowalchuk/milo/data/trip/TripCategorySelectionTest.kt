package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which trips the catch-up may sort into Business or Personal: a closed trip with no category
 * that Shawn has not set by hand, and no other. The rule in Kotlin (`needsSorting`) and the
 * conditions of the stand-in query, written out like the real one's, are compared.
 */
class TripCategorySelectionTest {
    private val trips = FakeTripDao()

    private fun stored(
        status: TripStatus = TripStatus.FINISHED,
        category: TripCategory? = null,
        setByHand: Boolean = false,
    ): Trip {
        val trip =
            Trip(
                id = trips.rows.size + 1L,
                startedAtMs = 1_791_028_800_000L,
                endedAtMs = 1_791_030_000_000L.takeIf { status != TripStatus.OPEN },
                status = status,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                distanceMetres = 12_340.0,
                category = category,
                categorySetByHand = setByHand,
            )
        trips.rows += trip
        return trip
    }

    @Test
    fun `a closed trip with no category is to be sorted, whatever its status`() {
        for (status in TripStatus.entries.filter { it != TripStatus.OPEN }) {
            assertTrue(status.name, needsSorting(stored(status)))
        }
    }

    @Test
    fun `a trip that is still open is never sorted`() {
        assertFalse(needsSorting(stored(TripStatus.OPEN)))
    }

    @Test
    fun `a trip that has a category is never sorted again`() {
        for (category in TripCategory.entries) {
            assertFalse(category.name, needsSorting(stored(category = category)))
        }
    }

    @Test
    fun `a trip set by hand is never touched, with or without a category`() {
        for (category in TripCategory.entries + null) {
            val byHand = stored(category = category, setByHand = true)
            assertFalse("$category", needsSorting(byHand))
        }
    }

    @Test
    fun `the query finds exactly the trips the rule names`() = runTest {
        // Every combination of status, category and set-by-hand.
        for (status in TripStatus.entries) {
            for (category in TripCategory.entries + null) {
                for (byHand in listOf(false, true)) stored(status, category, byHand)
            }
        }

        val found = trips.findUnsorted(TripStatus.OPEN)

        assertEquals(trips.rows.filter(::needsSorting), found)
        // Three closed statuses, each once without a category and not set by hand.
        assertEquals(3, found.size)
    }

    @Test
    fun `the write takes only a trip the rule names`() = runTest {
        for (status in TripStatus.entries) {
            for (category in TripCategory.entries + null) {
                for (byHand in listOf(false, true)) stored(status, category, byHand)
            }
        }
        val before = trips.rows.toList()

        val written =
            before.filter { trip ->
                val rows = trips.sortUnsorted(trip.id, TripCategory.BUSINESS, true, TripStatus.OPEN)
                rows == 1
            }

        assertEquals(before.filter(::needsSorting), written)
        // Every other row is exactly as it was.
        for (trip in before.filterNot(::needsSorting)) {
            assertEquals(trip, trips.rows.single { it.id == trip.id })
        }
    }
}
