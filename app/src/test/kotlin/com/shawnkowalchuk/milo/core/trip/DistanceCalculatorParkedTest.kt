package com.shawnkowalchuk.milo.core.trip

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 100 m between fixes 5 s apart is 72 km/h. */
private const val CRUISE_METRES_PER_FIX = 100.0

/**
 * How far a parked truck's fixes scatter in each direction. 3.3 m is what a stated accuracy of
 * 5 m (a 68 % radius) means.
 */
private const val JITTER_METRES = 3.3

/**
 * The third rule of the distance calculation: only real movement counts, so a parked truck with
 * GPS jitter adds nothing (APP_ENCYCLOPEDIA, "GPS recording and distance").
 */
class DistanceCalculatorParkedTest {
    private fun measure(points: List<TrackPoint>) = DistanceCalculator.measure(points)

    @Test
    fun `a move of under 10 m is not counted however good the fixes are`() {
        val points =
            listOf(
                fixAt(0.0, second = 0, accuracyMetres = 1f),
                fixAt(9.0, second = 5, accuracyMetres = 1f),
            )

        assertEquals(0.0, measure(points).metres, 0.0)
    }

    @Test
    fun `a move of over 10 m between two sharp fixes is counted`() {
        val points =
            listOf(
                fixAt(0.0, second = 0, accuracyMetres = 1f),
                fixAt(11.0, second = 5, accuracyMetres = 1f),
            )

        assertEquals(11.0, measure(points).metres, 0.1)
    }

    @Test
    fun `a move that the fixes' own error could explain waits until it is clear`() {
        // Two 5 m fixes can be 20 m apart by error alone, so 15 m proves nothing yet.
        val unclear = listOf(fixAt(0.0, second = 0), fixAt(15.0, second = 5))
        val clear = unclear + fixAt(30.0, second = 10)

        assertEquals(0.0, measure(unclear).metres, 0.0)
        // Nothing was lost by waiting: the whole 30 m is counted from where counting stopped.
        assertEquals(30.0, measure(clear).metres, 0.1)
    }

    @Test
    fun `an hour parked with GPS jitter adds nothing`() {
        val parked = jitterCloud(fixCount = 720, fromSecond = 0, seed = 7)

        // What a naive sum of fix-to-fix distances would have claimed: kilometres of nothing.
        val naive = parked.zipWithNext { a, b -> a.metresTo(b) }.sum()
        assertTrue("the cloud is too tame to prove anything: $naive", naive > 2_000.0)

        assertEquals(0.0, measure(parked).metres, 0.0)
    }

    @Test
    fun `jitter adds nothing for any of a hundred different parked hours`() {
        val total = (1..100).sumOf { seed -> measure(jitterCloud(720, 0, seed.toLong())).metres }

        // 72 000 fixes. The safety factor allows about one false move in ten thousand, and
        // rule 4 takes such a move back when the next fix returns: nothing is left.
        assertEquals("over 100 parked hours", 0.0, total, 0.0)
    }

    @Test
    fun `the 10 m rule alone would not be enough for a parked truck`() {
        // This is why movement must also exceed the fixes' own stated error. With that part
        // switched off, the same parked hour that measures zero above adds phantom distance,
        // even though rule 4 takes back every step that the next fix walks back: more than the
        // minimum trip distance, so an hour parked would be kept as a trip.
        val tenMetresOnly = DistanceLimits(noiseSafetyFactor = 0.0)
        val parked = jitterCloud(fixCount = 720, fromSecond = 0, seed = 7)

        val phantom = DistanceCalculator.measure(parked, tenMetresOnly).metres

        assertTrue("was $phantom", phantom > 300.0)
    }

    @Test
    fun `a position that drifts slowly while parked adds nothing`() {
        // The whole cloud wanders 8 m over the hour, as it does when satellites move.
        val drifting =
            List(720) { index ->
                fixAt(northMetres = 8.0 * index / 720, second = index * FIX_INTERVAL_SECONDS)
            }

        assertEquals(0.0, measure(drifting).metres, 0.0)
    }

    @Test
    fun `a long stop in the middle of a drive adds nothing to it`() {
        val out = driveNorth(fixCount = 11, metresPerFix = CRUISE_METRES_PER_FIX)
        val stop = jitterCloud(fixCount = 360, fromSecond = 55, seed = 3, northMetres = 1_000.0)
        val back =
            driveNorth(
                fixCount = 11,
                metresPerFix = CRUISE_METRES_PER_FIX,
                fromNorthMetres = 1_000.0,
                fromSecond = 55 + 360 * FIX_INTERVAL_SECONDS,
            )

        val metres = measure(out + stop + back).metres

        // 1 000 m, half an hour parked, 1 000 m. The jitter may shift where counting resumes
        // by a few metres; it must not add tens.
        assertEquals(2_000.0, metres, 15.0)
    }

    /**
     * A parked truck as the phone sees it: every fix lands a random few metres from the true
     * spot. [Random] with a seed, so the test is the same on every run.
     */
    private fun jitterCloud(
        fixCount: Int,
        fromSecond: Int,
        seed: Long,
        northMetres: Double = 0.0,
    ): List<TrackPoint> {
        val random = Random(seed)
        return List(fixCount) { index ->
            fixAt(
                northMetres = northMetres + random.nextGaussian() * JITTER_METRES,
                eastMetres = random.nextGaussian() * JITTER_METRES,
                second = fromSecond + index * FIX_INTERVAL_SECONDS,
            )
        }
    }
}
