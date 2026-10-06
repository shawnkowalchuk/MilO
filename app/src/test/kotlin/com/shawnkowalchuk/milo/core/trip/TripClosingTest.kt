package com.shawnkowalchuk.milo.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val MINIMUM_METRES = 300.0

class TripClosingTest {
    /** 21 fixes, 100 m apart, 5 s apart: 2 km in 100 s. */
    private val drive = driveNorth(fixCount = 21, metresPerFix = 100.0)
    private val driveEndMs = drive.last().wallClockMs

    @Test
    fun `a trip closes at its last recorded point`() {
        val closed = TripClosing.close(drive, lastPointNotAfterMs = driveEndMs, MINIMUM_METRES)

        assertEquals(TripStatus.FINISHED, closed.status)
        assertEquals(driveEndMs, closed.endedAtMs)
        assertEquals(2_000.0, closed.distance.metres, 1.0)
        assertEquals(drive.first(), closed.start)
        assertEquals(drive.last(), closed.end)
    }

    @Test
    fun `what was recorded during the grace period is not part of the trip`() {
        // The truck disconnected with the last fix. For the two minutes of grace the phone
        // kept recording while Shawn walked 400 m away with it.
        val walkingAway =
            List(24) { index ->
                fixAt(
                    northMetres = 2_000.0,
                    eastMetres = (index + 1) * 17.0,
                    second = 100 + (index + 1) * FIX_INTERVAL_SECONDS,
                )
            }

        val closed =
            TripClosing.close(drive + walkingAway, lastPointNotAfterMs = driveEndMs, MINIMUM_METRES)

        // Closed at the truck: not two minutes later, not 400 m further, not at Shawn's desk.
        assertEquals(driveEndMs, closed.endedAtMs)
        assertEquals(2_000.0, closed.distance.metres, 1.0)
        assertEquals(drive.last(), closed.end)
    }

    @Test
    fun `a false start is discarded whatever distance the phone covered`() {
        // The companion callback fired as Shawn drove past the yard in another vehicle. In the
        // seconds before the start was found to be false the phone covered 2 km of fixes.
        val closed =
            TripClosing.close(
                drive,
                lastPointNotAfterMs = driveEndMs,
                MINIMUM_METRES,
                falseStart = true,
            )

        assertEquals(TripStatus.DISCARDED, closed.status)
        // The figures are still worked out and stored, so the row shows what was thrown away.
        assertEquals(2_000.0, closed.distance.metres, 1.0)
    }

    @Test
    fun `a clock that was fast at the start and corrected later costs the trip nothing`() {
        // The phone's clock runs ten minutes fast for the first 5 km, then it is corrected.
        // Every early fix carries a time later than the trip's end.
        val fast =
            driveNorth(fixCount = 51, metresPerFix = 100.0)
                .map { it.copy(wallClockMs = it.wallClockMs + 10 * MINUTE) }
        val corrected =
            driveNorth(
                fixCount = 10,
                metresPerFix = 100.0,
                fromNorthMetres = 5_100.0,
                fromSecond = 255,
            )
        val truckGoneAt = corrected.last().wallClockMs

        val closed = TripClosing.close(fast + corrected, truckGoneAt, MINIMUM_METRES)

        // Filtered by time, only the last 900 m would be left.
        assertEquals(6_000.0, closed.distance.metres, 1.0)
        assertEquals(fast.first(), closed.start)
        assertEquals(truckGoneAt, closed.endedAtMs)
    }

    @Test
    fun `the end time is the last fix before the cut-off, not the cut-off itself`() {
        // GPS was lost 40 s before the truck disconnected.
        val closed =
            TripClosing.close(drive, lastPointNotAfterMs = driveEndMs + 40_000, MINIMUM_METRES)

        assertEquals(driveEndMs, closed.endedAtMs)
    }

    @Test
    fun `a poor last fix still gives the end time but not the end position`() {
        val poor = fixAt(northMetres = 2_500.0, second = 105, accuracyMetres = 80f)

        val closed =
            TripClosing.close(drive + poor, lastPointNotAfterMs = poor.wallClockMs, MINIMUM_METRES)

        assertEquals(poor.wallClockMs, closed.endedAtMs)
        assertEquals(drive.last(), closed.end)
        assertEquals(2_000.0, closed.distance.metres, 1.0)
    }

    @Test
    fun `a trip under the minimum distance is discarded`() {
        // Moving the truck across the yard: 200 m.
        val yard = driveNorth(fixCount = 11, metresPerFix = 20.0)

        val closed = TripClosing.close(yard, yard.last().wallClockMs, MINIMUM_METRES)

        assertEquals(TripStatus.DISCARDED, closed.status)
        // The figures are still worked out, so the event log can say what was discarded.
        assertEquals(200.0, closed.distance.metres, 1.0)
    }

    @Test
    fun `a trip of exactly the minimum distance is kept`() {
        val closed = TripClosing.close(drive, driveEndMs, minimumDistanceMetres = 0.0)
        val exactly = closed.distance.metres

        val atTheLimit = TripClosing.close(drive, driveEndMs, minimumDistanceMetres = exactly)

        assertEquals(TripStatus.FINISHED, atTheLimit.status)
    }

    @Test
    fun `a trip with no fixes at all is discarded and ends at the cut-off`() {
        val closed = TripClosing.close(emptyList(), lastPointNotAfterMs = T0, MINIMUM_METRES)

        assertEquals(TripStatus.DISCARDED, closed.status)
        assertEquals(T0, closed.endedAtMs)
        assertEquals(0.0, closed.distance.metres, 0.0)
        assertNull(closed.start)
        assertNull(closed.end)
    }

    @Test
    fun `with the minimum distance set to zero nothing is discarded`() {
        val closed = TripClosing.close(emptyList(), lastPointNotAfterMs = T0, 0.0)

        assertEquals(TripStatus.FINISHED, closed.status)
    }
}
