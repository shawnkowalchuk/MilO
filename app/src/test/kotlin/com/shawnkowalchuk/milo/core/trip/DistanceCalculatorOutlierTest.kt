package com.shawnkowalchuk.milo.core.trip

import org.junit.Assert.assertEquals
import org.junit.Test

private const val CRUISE_METRES_PER_FIX = 100.0
private const val ONE_METRE = 1.0
private const val TEN_MINUTES_MS = 10 * 60_000L

/**
 * Bad fixes that claim to be good. First the guards around the jump filter and its re-anchor
 * rule, then rule 4: a single bad fix inside the speed limit is not counted out and back.
 */
class DistanceCalculatorOutlierTest {
    private fun measure(points: List<TrackPoint>) = DistanceCalculator.measure(points)

    /** 10 km due north: fix number n is n x 100 m up the road, n x 5 s into the drive. */
    private val drive = driveNorth(fixCount = 101, metresPerFix = CRUISE_METRES_PER_FIX)

    /** A fix that claims the truck is far off the road at the moment of fix number [index]. */
    private fun wildFix(index: Int, northMetres: Double, eastMetres: Double) =
        fixAt(northMetres, eastMetres, second = index * FIX_INTERVAL_SECONDS)

    private fun driveWith(vararg replaced: Pair<Int, TrackPoint>): List<TrackPoint> =
        drive.toMutableList().also { for ((index, fix) in replaced) it[index] = fix }

    // ---- The speed between two fixes is timed with elapsed realtime ---------------------------

    @Test
    fun `a wall clock corrected in mid-drive changes nothing`() {
        // The phone's time of day jumps ten minutes at fix 50. Time since boot does not.
        val setBack =
            drive.mapIndexed { index, fix ->
                if (index < 50) fix else fix.copy(wallClockMs = fix.wallClockMs - TEN_MINUTES_MS)
            }
        val setForward =
            drive.mapIndexed { index, fix ->
                if (index < 50) fix else fix.copy(wallClockMs = fix.wallClockMs + TEN_MINUTES_MS)
            }

        for (corrected in listOf(setBack, setForward)) {
            val result = measure(corrected)

            assertEquals(10_000.0, result.metres, ONE_METRE)
            assertEquals(0, result.rejectedAsJump)
        }
    }

    // ---- The re-anchor rule needs three rejected fixes in a row that agree --------------------

    @Test
    fun `two wild fixes that agree with each other are still both dropped`() {
        // Both claim a spot 5 km east, 100 m apart: they agree, but two are not enough.
        val result =
            measure(
                driveWith(
                    50 to wildFix(50, northMetres = 5_000.0, eastMetres = 5_000.0),
                    51 to wildFix(51, northMetres = 5_100.0, eastMetres = 5_000.0),
                ),
            )

        assertEquals(10_000.0, result.metres, ONE_METRE)
        assertEquals(2, result.rejectedAsJump)
        assertEquals(drive.last(), result.lastAccepted)
    }

    @Test
    fun `three wild fixes that disagree with each other are all dropped`() {
        // Three in a row, but each one is as far from the others as from the road.
        val result =
            measure(
                driveWith(
                    50 to wildFix(50, northMetres = 5_000.0, eastMetres = 5_000.0),
                    51 to wildFix(51, northMetres = 5_100.0, eastMetres = -8_000.0),
                    52 to wildFix(52, northMetres = 15_000.0, eastMetres = 0.0),
                ),
            )

        assertEquals(10_000.0, result.metres, ONE_METRE)
        assertEquals(3, result.rejectedAsJump)
        assertEquals(drive.last(), result.lastAccepted)
    }

    @Test
    fun `wild fixes with good ones between them never add up to a re-anchor`() {
        // The same wrong spot three times, but never twice in a row: a good fix in between
        // means the fix the jumps are measured from was right all along.
        val result =
            measure(
                driveWith(
                    50 to wildFix(50, northMetres = 5_000.0, eastMetres = 5_000.0),
                    52 to wildFix(52, northMetres = 5_000.0, eastMetres = 5_000.0),
                    54 to wildFix(54, northMetres = 5_000.0, eastMetres = 5_000.0),
                ),
            )

        assertEquals(10_000.0, result.metres, ONE_METRE)
        assertEquals(3, result.rejectedAsJump)
        assertEquals(drive.last(), result.lastAccepted)
    }

    // ---- Rule 4: out and back is not a drive --------------------------------------------------

    /**
     * 1 km, a stop at a light with one bad fix [offsetMetres] to the side, then 1 km more. The
     * bad fix arrives [gapSeconds] after the fix before it: a fix further away than 250 m only
     * gets past the speed limit after a gap. The truck stands for 20 s more after the bad fix.
     */
    private fun stopWithOneBadFix(offsetMetres: Double, gapSeconds: Int): List<TrackPoint> {
        val out = driveNorth(fixCount = 11, metresPerFix = CRUISE_METRES_PER_FIX)
        val standing = List(6) { fixAt(1_000.0, second = 55 + it * FIX_INTERVAL_SECONDS) }
        val badAt = 80 + gapSeconds
        val bad = fixAt(1_000.0, eastMetres = offsetMetres, second = badAt, accuracyMetres = 10f)
        val stillStanding = List(4) { fixAt(1_000.0, second = badAt + (it + 1) * 5) }
        val back =
            driveNorth(
                fixCount = 11,
                metresPerFix = CRUISE_METRES_PER_FIX,
                fromNorthMetres = 1_000.0,
                fromSecond = badAt + 25,
            )
        return out + standing + bad + stillStanding + back
    }

    @Test
    fun `one bad fix while the truck stands still adds nothing, from 100 m to 700 m away`() {
        val cases = listOf(100.0 to 5, 200.0 to 5, 400.0 to 10, 700.0 to 15)
        for ((offsetMetres, gapSeconds) in cases) {
            val result = measure(stopWithOneBadFix(offsetMetres, gapSeconds))

            // Counted out and back it would be 2 000 m plus twice the offset.
            assertEquals("bad fix $offsetMetres m away", 2_000.0, result.metres, ONE_METRE)
        }
    }

    @Test
    fun `a parked truck with one bad fix 200 m away does not become a trip`() {
        // Half an hour connected in the yard. Out and back, the one bad fix would be 400 m:
        // more than the minimum trip distance, so a trip that never happened would be kept.
        val parked = List(360) { fixAt(0.0, second = it * FIX_INTERVAL_SECONDS) }.toMutableList()
        parked[180] = fixAt(0.0, eastMetres = 200.0, second = 900, accuracyMetres = 10f)

        val closed = TripClosing.close(parked, parked.last().wallClockMs, 300.0)

        assertEquals(0.0, closed.distance.metres, 0.0)
        assertEquals(TripStatus.DISCARDED, closed.status)
    }

    @Test
    fun `a bad fix far to the side of a moving truck is not counted out and back`() {
        // 200 m off the road at 72 km/h: inside the speed limit both ways.
        val result = measure(driveWith(50 to wildFix(50, 5_000.0, eastMetres = 200.0)))

        assertEquals(10_000.0, result.metres, ONE_METRE)
        assertEquals(0, result.rejectedAsJump)
    }

    @Test
    fun `a step the next fix does not walk back stands`() {
        // A real right-angle turn: 100 m north, then 100 m east. Nothing is taken back.
        val turn =
            listOf(
                fixAt(0.0, second = 0),
                fixAt(100.0, second = 5),
                fixAt(100.0, eastMetres = 100.0, second = 10),
            )

        assertEquals(200.0, measure(turn).metres, ONE_METRE)
    }

    @Test
    fun `the price of rule 4 - forward and back within one fix is not counted`() {
        // 15 m forward and 13 m back between three sharp fixes, as when shunting in the yard.
        val shunt =
            listOf(
                fixAt(0.0, second = 0, accuracyMetres = 1f),
                fixAt(15.0, second = 5, accuracyMetres = 1f),
                fixAt(2.0, second = 10, accuracyMetres = 1f),
            )

        assertEquals(0.0, measure(shunt).metres, 0.0)
    }
}
