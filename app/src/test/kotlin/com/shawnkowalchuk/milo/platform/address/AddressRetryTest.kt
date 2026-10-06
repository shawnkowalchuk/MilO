package com.shawnkowalchuk.milo.platform.address

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.trip.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS

/** Which trips are due for an address lookup, when MilO gives up, and what a row says meanwhile. */
class AddressRetryTest {
    private val now = 1_791_028_800_000L

    /** A finished trip with both positions and no address, as a trip is when it closes. */
    private fun trip(
        status: TripStatus = TripStatus.FINISHED,
        startAddress: String? = null,
        endAddress: String? = null,
        attempts: Int = 0,
        lastAttemptAtMs: Long? = null,
        hasPositions: Boolean = true,
    ) = Trip(
        id = 7,
        startedAtMs = now - HOUR_MS,
        endedAtMs = now - 30 * MINUTE_MS,
        status = status,
        startedBy = TripStartCause.TRUCK,
        truckSeen = true,
        distanceMetres = 12_300.0,
        startLatitude = 53.5461.takeIf { hasPositions },
        startLongitude = (-113.4938).takeIf { hasPositions },
        endLatitude = 53.2594.takeIf { hasPositions },
        endLongitude = (-113.5492).takeIf { hasPositions },
        startAddress = startAddress,
        endAddress = endAddress,
        addressAttempts = attempts,
        addressLastAttemptAtMs = lastAttemptAtMs,
    )

    // ---- Which trips are due ----------------------------------------------------------------------

    @Test
    fun `a finished trip that has never been looked up is due`() {
        assertTrue(isDueForLookup(trip(), now))
    }

    @Test
    fun `a trip recorded before addresses existed is due like any other`() {
        // The migration leaves the four new columns empty on old rows.
        val old = trip().copy(startedAtMs = now - 300 * HOUR_MS, endedAtMs = now - 299 * HOUR_MS)

        assertTrue(isDueForLookup(old, now))
    }

    @Test
    fun `a discarded trip, a deleted trip and a trip in progress are never due`() {
        assertFalse(isDueForLookup(trip(status = TripStatus.DISCARDED), now))
        assertFalse(isDueForLookup(trip(status = TripStatus.DELETED), now))
        assertFalse(isDueForLookup(trip(status = TripStatus.OPEN), now))
    }

    @Test
    fun `a trip that is restored, or counted after all, is due like any finished trip`() {
        val deleted = trip(status = TripStatus.DELETED, startAddress = "12 Shop Rd, Edmonton")
        val discarded = trip(status = TripStatus.DISCARDED)

        // What Restore and "Count this trip" do to a row: the status, and nothing else.
        assertTrue(isDueForLookup(deleted.copy(status = TripStatus.FINISHED), now))
        assertTrue(isDueForLookup(discarded.copy(status = TripStatus.FINISHED), now))
    }

    @Test
    fun `a trip with both addresses is not due`() {
        val complete = trip(startAddress = "12 Shop Rd, Edmonton", endAddress = "Leduc")

        assertFalse(isDueForLookup(complete, now))
    }

    @Test
    fun `a trip that lacks one of its addresses is due`() {
        assertTrue(isDueForLookup(trip(startAddress = "12 Shop Rd, Edmonton"), now))
        assertTrue(isDueForLookup(trip(endAddress = "Leduc"), now))
    }

    @Test
    fun `a trip with no stored position has nothing to look up`() {
        assertFalse(isDueForLookup(trip(hasPositions = false), now))
    }

    // ---- Spacing the attempts ---------------------------------------------------------------------

    @Test
    fun `after a failed attempt the next one waits, longer each time`() {
        assertEquals(0L, waitAfterAttemptMs(0))
        assertEquals(2 * MINUTE_MS, waitAfterAttemptMs(1))
        assertEquals(HOUR_MS, waitAfterAttemptMs(2))
        assertEquals(24 * HOUR_MS, waitAfterAttemptMs(3))
    }

    @Test
    fun `a trip is not due until its wait is over`() {
        val failedOnce = trip(attempts = 1, lastAttemptAtMs = now)

        assertFalse(isDueForLookup(failedOnce, now + 2 * MINUTE_MS - 1))
        assertTrue(isDueForLookup(failedOnce, now + 2 * MINUTE_MS))

        val failedThreeTimes = trip(attempts = 3, lastAttemptAtMs = now)

        assertFalse(isDueForLookup(failedThreeTimes, now + 23 * HOUR_MS))
        assertTrue(isDueForLookup(failedThreeTimes, now + 24 * HOUR_MS))
    }

    @Test
    fun `a lookup that found one address without failing does not make the trip wait`() {
        // Cannot be stored today (a missing address is always counted), but the rule must not
        // depend on that: with no failed attempt there is no wait.
        val half = trip(startAddress = "12 Shop Rd, Edmonton", attempts = 0, lastAttemptAtMs = now)

        assertTrue(isDueForLookup(half, now))
    }

    @Test
    fun `a clock that was set back does not make a trip wait for ever`() {
        val stampedInTheFuture = trip(attempts = 1, lastAttemptAtMs = now + 300 * HOUR_MS)

        assertTrue(isDueForLookup(stampedInTheFuture, now))
    }

    // ---- Giving up --------------------------------------------------------------------------------

    @Test
    fun `after the last attempt a trip is never due again`() {
        val givenUp = trip(attempts = MAX_ADDRESS_ATTEMPTS, lastAttemptAtMs = now)

        assertNull(waitAfterAttemptMs(MAX_ADDRESS_ATTEMPTS))
        assertFalse(isDueForLookup(givenUp, now + 1_000 * HOUR_MS))
        // More than the limit must not be read as "still some left".
        assertFalse(isDueForLookup(givenUp.copy(addressAttempts = 99), now + 1_000 * HOUR_MS))
    }

    @Test
    fun `the last attempt is still made`() {
        val oneLeft = trip(attempts = MAX_ADDRESS_ATTEMPTS - 1, lastAttemptAtMs = now)

        assertTrue(isDueForLookup(oneLeft, now + 24 * HOUR_MS))
    }

    // ---- What a row says about each end -----------------------------------------------------------

    @Test
    fun `a stored address is known`() {
        val complete = trip(startAddress = "12 Shop Rd, Edmonton", endAddress = "Leduc")

        assertEquals(TripPlace.Known("12 Shop Rd, Edmonton"), complete.startPlace())
        assertEquals(TripPlace.Known("Leduc"), complete.endPlace())
    }

    @Test
    fun `a missing address is being looked up while attempts are left`() {
        val waiting = trip(startAddress = "12 Shop Rd, Edmonton", attempts = 2)

        assertEquals(TripPlace.Known("12 Shop Rd, Edmonton"), waiting.startPlace())
        assertEquals(TripPlace.LookingUp, waiting.endPlace())
        assertEquals(TripPlace.LookingUp, trip().startPlace())
    }

    @Test
    fun `a missing address is not found once MilO has given up, but a found one stays`() {
        val givenUp = trip(startAddress = "12 Shop Rd, Edmonton", attempts = MAX_ADDRESS_ATTEMPTS)

        assertEquals(TripPlace.Known("12 Shop Rd, Edmonton"), givenUp.startPlace())
        assertEquals(TripPlace.NotFound, givenUp.endPlace())
    }

    @Test
    fun `an end with no position is not found, never looked up`() {
        val nowhere = trip(hasPositions = false)

        assertEquals(TripPlace.NotFound, nowhere.startPlace())
        assertEquals(TripPlace.NotFound, nowhere.endPlace())
    }

    @Test
    fun `an address left empty by hand is said to be that, with or without a position`() {
        val emptied = trip(endAddress = "48 Main St, Leduc").copy(startAddressByHand = true)
        val typedIn =
            trip(hasPositions = false).copy(startAddressByHand = true, endAddressByHand = true)

        // Never "looking up": MilO keeps away from an address that is Shawn's own.
        assertEquals(TripPlace.LeftBlank, emptied.startPlace())
        assertEquals(TripPlace.LeftBlank, typedIn.startPlace())
        assertEquals(TripPlace.LeftBlank, typedIn.endPlace())
        assertFalse(isDueForLookup(emptied, now))
        assertFalse(isDueForLookup(typedIn, now))
    }

    @Test
    fun `an address typed by hand is shown like any other`() {
        val typed = trip(startAddress = "Home").copy(startAddressByHand = true)

        assertEquals(TripPlace.Known("Home"), typed.startPlace())
        // The other end is still the lookup's to find.
        assertEquals(TripPlace.LookingUp, typed.endPlace())
        assertTrue(isDueForLookup(typed, now))
    }
}
