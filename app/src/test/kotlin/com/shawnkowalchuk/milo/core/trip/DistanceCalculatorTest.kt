package com.shawnkowalchuk.milo.core.trip

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 100 m between fixes 5 s apart is 72 km/h. */
private const val CRUISE_METRES_PER_FIX = 100.0

/** Haversine against the flat-earth arithmetic in [fixAt] agrees to well under this. */
private const val ONE_METRE = 1.0

/**
 * A plain drive, and the first two rules of the point filter: accuracy and impossible jumps.
 * The third rule, that only real movement counts, is in [DistanceCalculatorParkedTest].
 */
class DistanceCalculatorTest {
    private fun measure(points: List<TrackPoint>) = DistanceCalculator.measure(points)

    // ---- A plain drive ------------------------------------------------------------------------

    @Test
    fun `no points give zero distance and no positions`() {
        val result = measure(emptyList())

        assertEquals(0.0, result.metres, 0.0)
        assertNull(result.firstAccepted)
        assertNull(result.lastAccepted)
    }

    @Test
    fun `a straight drive of 10 km measures 10 km`() {
        // 101 fixes are 100 steps of 100 m.
        val drive = driveNorth(fixCount = 101, metresPerFix = CRUISE_METRES_PER_FIX)

        val result = measure(drive)

        assertEquals(10_000.0, result.metres, ONE_METRE)
        assertEquals(101, result.acceptedCount)
        assertEquals(0, result.rejectedForAccuracy)
        assertEquals(0, result.rejectedAsJump)
        assertEquals(drive.first(), result.firstAccepted)
        assertEquals(drive.last(), result.lastAccepted)
    }

    @Test
    fun `adding fixes one at a time gives the same result as measuring them all`() {
        val drive = driveNorth(fixCount = 50, metresPerFix = CRUISE_METRES_PER_FIX)

        var running = DistanceState()
        for (fix in drive) running = DistanceCalculator.add(running, fix)

        assertEquals(measure(drive), running)
    }

    @Test
    fun `a drive at walking pace is still counted, a few fixes at a time`() {
        // 2 m per fix is 1.4 km/h: each fix is inside the noise, but the truck does get there.
        val crawl = driveNorth(fixCount = 101, metresPerFix = 2.0)

        val metres = measure(crawl).metres

        // 200 m driven. Only the last stretch, short of the next threshold, may be missing.
        assertTrue("was $metres", metres > 180.0 && metres < 200.0 + ONE_METRE)
    }

    // ---- Rule 1: accuracy ---------------------------------------------------------------------

    @Test
    fun `a fix at exactly 25 m accuracy is used and one just over is not`() {
        val used = listOf(fixAt(0.0, second = 0), fixAt(200.0, second = 5, accuracyMetres = 25f))
        val dropped =
            listOf(fixAt(0.0, second = 0), fixAt(200.0, second = 5, accuracyMetres = 25.1f))

        assertEquals(200.0, measure(used).metres, ONE_METRE)
        assertEquals(0.0, measure(dropped).metres, 0.0)
        assertEquals(1, measure(dropped).rejectedForAccuracy)
    }

    @Test
    fun `a fix with no accuracy, a negative one or impossible coordinates is not used`() {
        val origin = fixAt(0.0, second = 0)
        val unusable =
            listOf(
                fixAt(200.0, second = 5, accuracyMetres = Float.POSITIVE_INFINITY),
                fixAt(200.0, second = 10, accuracyMetres = Float.NaN),
                fixAt(200.0, second = 15, accuracyMetres = -1f),
                fixAt(200.0, second = 20).copy(latitude = Double.NaN),
                fixAt(200.0, second = 25).copy(latitude = 91.0),
                fixAt(200.0, second = 30).copy(longitude = -181.0),
            )

        val result = measure(listOf(origin) + unusable)

        assertEquals(0.0, result.metres, 0.0)
        assertEquals(unusable.size, result.rejectedForAccuracy)
        assertEquals(origin, result.lastAccepted)
    }

    @Test
    fun `a burst of poor-accuracy fixes does not change the distance`() {
        val random = Random(1)
        val before = driveNorth(fixCount = 21, metresPerFix = CRUISE_METRES_PER_FIX)
        // Ten fixes over 50 s in which the phone fell back to cell towers: 60 m accuracy,
        // scattered up to 300 m either side of the road.
        val burst =
            List(10) { index ->
                fixAt(
                    northMetres = 2_000.0 + index * CRUISE_METRES_PER_FIX,
                    eastMetres = random.nextDouble() * 600 - 300,
                    second = 105 + index * FIX_INTERVAL_SECONDS,
                    accuracyMetres = 60f,
                )
            }
        val after =
            driveNorth(
                fixCount = 20,
                metresPerFix = CRUISE_METRES_PER_FIX,
                fromNorthMetres = 3_100.0,
                fromSecond = 155,
            )

        val result = measure(before + burst + after)

        // The road runs straight from 0 to 5 000 m; the burst is bridged by a straight line.
        assertEquals(5_000.0, result.metres, ONE_METRE)
        assertEquals(10, result.rejectedForAccuracy)
        assertEquals(0, result.rejectedAsJump)
    }

    // ---- Rule 2: impossible jumps -------------------------------------------------------------

    @Test
    fun `180 km per hour is possible and a little more is not`() {
        // 180 km/h is 50 m/s: 250 m in the 5 s between two fixes.
        val fast = listOf(fixAt(0.0, second = 0), fixAt(249.0, second = 5))
        val tooFast = listOf(fixAt(0.0, second = 0), fixAt(251.0, second = 5))

        assertEquals(249.0, measure(fast).metres, ONE_METRE)
        assertEquals(0.0, measure(tooFast).metres, 0.0)
        assertEquals(1, measure(tooFast).rejectedAsJump)
    }

    @Test
    fun `a single wild outlier is dropped and the rest of the trip is unharmed`() {
        val drive = driveNorth(fixCount = 101, metresPerFix = CRUISE_METRES_PER_FIX)
        // Fix 50 lands 5 km to the east while claiming to be accurate.
        val withOutlier =
            drive.toMutableList().also {
                it[50] = fixAt(northMetres = 5_000.0, eastMetres = 5_000.0, second = 250)
            }

        val result = measure(withOutlier)

        assertEquals(10_000.0, result.metres, ONE_METRE)
        assertEquals(1, result.rejectedAsJump)
        assertEquals(100, result.acceptedCount)
    }

    @Test
    fun `two wild fixes that disagree with each other are both dropped`() {
        val drive = driveNorth(fixCount = 101, metresPerFix = CRUISE_METRES_PER_FIX)
        val withOutliers =
            drive.toMutableList().also {
                it[50] = fixAt(northMetres = 5_000.0, eastMetres = 5_000.0, second = 250)
                it[51] = fixAt(northMetres = 5_100.0, eastMetres = -8_000.0, second = 255)
            }

        val result = measure(withOutliers)

        assertEquals(10_000.0, result.metres, ONE_METRE)
        assertEquals(2, result.rejectedAsJump)
    }

    @Test
    fun `a GPS gap is bridged, because a long way in a long time is not a jump`() {
        val drive = driveNorth(fixCount = 101, metresPerFix = CRUISE_METRES_PER_FIX)
        // Two minutes in a tunnel: fixes 40 to 63 never arrive.
        val withGap = drive.filterIndexed { index, _ -> index !in 40..63 }

        val result = measure(withGap)

        assertEquals(10_000.0, result.metres, ONE_METRE)
        assertEquals(0, result.rejectedAsJump)
    }

    @Test
    fun `a wrong first fix is replaced once three later fixes agree with each other`() {
        // The phone's first answer is a stale position 3 km away. Without the re-anchor rule
        // every later fix would be "a jump" from it and the whole trip would measure zero.
        val stale = fixAt(northMetres = 0.0, eastMetres = 3_000.0, second = 0)
        val drive =
            driveNorth(fixCount = 100, metresPerFix = CRUISE_METRES_PER_FIX, fromSecond = 5)

        val result = measure(listOf(stale) + drive)

        // 99 steps of 100 m. The 3 km leap from the stale fix is not counted.
        assertEquals(9_900.0, result.metres, ONE_METRE)
        assertEquals(drive.first(), result.firstAccepted)
        assertEquals(0, result.rejectedAsJump)
        assertEquals(101, result.acceptedCount)
    }

    @Test
    fun `a wrong cluster in the middle is left again without counting either leap`() {
        val before = driveNorth(fixCount = 20, metresPerFix = CRUISE_METRES_PER_FIX)
        // Three fixes in a row agree on a spot 4 km to the east, then the truth returns.
        val wrong =
            List(3) { index ->
                fixAt(1_900.0, eastMetres = 4_000.0, second = 100 + index * FIX_INTERVAL_SECONDS)
            }
        val after =
            driveNorth(
                fixCount = 20,
                metresPerFix = CRUISE_METRES_PER_FIX,
                fromNorthMetres = 2_300.0,
                fromSecond = 115,
            )

        val result = measure(before + wrong + after)

        // 1 900 m before and 1 900 m after. The 400 m driven during the wrong cluster is lost,
        // which is the honest outcome: nothing trustworthy was recorded for it.
        assertEquals(3_800.0, result.metres, ONE_METRE)
        assertEquals(before.first(), result.firstAccepted)
        assertEquals(after.last(), result.lastAccepted)
    }

    @Test
    fun `a fix delivered twice counts once`() {
        val drive = driveNorth(fixCount = 11, metresPerFix = CRUISE_METRES_PER_FIX)
        val withRepeat = drive.take(6) + drive[5] + drive.drop(6)

        val result = measure(withRepeat)

        assertEquals(1_000.0, result.metres, ONE_METRE)
    }

    @Test
    fun `after a reboot in mid-trip counting resumes and the unknown gap is not counted`() {
        val beforeReboot = driveNorth(fixCount = 11, metresPerFix = CRUISE_METRES_PER_FIX)
        // The elapsed-realtime clock restarts near zero, so every new fix looks "earlier".
        val afterReboot =
            driveNorth(
                fixCount = 11,
                metresPerFix = CRUISE_METRES_PER_FIX,
                fromNorthMetres = 3_000.0,
                fromSecond = 1,
            )

        val result = measure(beforeReboot + afterReboot)

        // 1 000 m before and 1 000 m after. The 2 km in between cannot be checked for speed.
        assertEquals(2_000.0, result.metres, ONE_METRE)
        assertEquals(afterReboot.last(), result.lastAccepted)
    }
}
