package com.shawnkowalchuk.milo.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A turn every 300 m, as in a town. */
private const val LEG_METRES = 300.0

/** 35 m between fixes 5 s apart is about 25 km/h. Chosen so no fix lands on a corner. */
private const val TOWN_METRES_PER_FIX = 35.0

private const val TOWN_FIX_COUNT = 343

/** The rate of a trip since 2026-10-09 (`FixRate.RECORDING`). */
private const val TRIP_SECONDS_PER_FIX = 2

/** What Shawn's phone states for most of its fixes on the road. */
private const val USUAL_ACCURACY_METRES = 4f

/** About 25 km/h and about 50 km/h. */
private const val SLOW_METRES_PER_SECOND = 7.0
private const val BRISK_METRES_PER_SECOND = 14.0

/**
 * What rule 3 costs. Distance is added as a straight line from where it was last counted, once
 * the truck is further from there than the fixes' own stated error could explain. That is exact
 * on a straight road. Through a turn it cuts the corner, and the worse the stated accuracy, the
 * longer the line and the bigger the cut. These tests put numbers on it, so a change to the
 * thresholds shows up here. The numbers come from this simulation, not from the phone.
 */
class DistanceCalculatorTurnsTest {
    /**
     * A route of right-angle turns, north then east then north again, every [LEG_METRES], driven
     * at a steady speed with a fix every 5 s and no GPS noise.
     */
    private fun townRoute(accuracyMetres: Float): List<TrackPoint> = List(TOWN_FIX_COUNT) { index ->
        val driven = index * TOWN_METRES_PER_FIX
        val legsDone = (driven / LEG_METRES).toInt()
        val intoLeg = driven - legsDone * LEG_METRES
        // Legs 0, 2, 4... run north and legs 1, 3, 5... run east.
        val north = (legsDone + 1) / 2 * LEG_METRES + if (legsDone % 2 == 0) intoLeg else 0.0
        val east = legsDone / 2 * LEG_METRES + if (legsDone % 2 == 1) intoLeg else 0.0
        fixAt(north, east, second = index * FIX_INTERVAL_SECONDS, accuracyMetres = accuracyMetres)
    }

    private fun shortfallPercent(points: List<TrackPoint>): Double {
        val driven = (TOWN_FIX_COUNT - 1) * TOWN_METRES_PER_FIX
        return 100.0 * (driven - DistanceCalculator.measure(points).metres) / driven
    }

    @Test
    fun `on a route of turns the distance comes up short, more so with poorer accuracy`() {
        // What a fix every 5 s can see at all: the straight lines between neighbouring fixes.
        val sampled = townRoute(accuracyMetres = 5f).zipWithNext { a, b -> a.metresTo(b) }.sum()
        val driven = (TOWN_FIX_COUNT - 1) * TOWN_METRES_PER_FIX
        val lostToSampling = 100.0 * (driven - sampled) / driven

        val at5 = shortfallPercent(townRoute(accuracyMetres = 5f))
        val at10 = shortfallPercent(townRoute(accuracyMetres = 10f))
        val at25 = shortfallPercent(townRoute(accuracyMetres = 25f))

        // With 5 m fixes the threshold (20 m) is under one step, so nothing is lost beyond
        // what the sampling loses anyway: about 2 %.
        assertEquals(lostToSampling, at5, 0.1)
        assertTrue("sampling alone: $lostToSampling %", lostToSampling in 1.5..3.0)
        // With 10 m fixes the threshold is 40 m: about 4 %. With 25 m fixes, 100 m: about 11 %.
        assertTrue("at 10 m accuracy: $at10 %", at10 in 3.5..5.0)
        assertTrue("at 25 m accuracy: $at25 %", at25 in 9.0..12.0)
    }

    /**
     * How much of the town route goes uncounted, in per cent, driven at [metresPerSecond] with
     * a fix every [secondsPerFix] of [USUAL_ACCURACY_METRES] and no GPS noise.
     */
    private fun townShortfallPercent(secondsPerFix: Int, metresPerSecond: Double): Double {
        val metresPerFix = metresPerSecond * secondsPerFix
        val fixCount = ((TOWN_FIX_COUNT - 1) * TOWN_METRES_PER_FIX / metresPerFix).toInt() + 1
        val points =
            List(fixCount) { index ->
                val driven = index * metresPerFix
                val legsDone = (driven / LEG_METRES).toInt()
                val intoLeg = driven - legsDone * LEG_METRES
                val north =
                    (legsDone + 1) / 2 * LEG_METRES + if (legsDone % 2 == 0) intoLeg else 0.0
                val east = legsDone / 2 * LEG_METRES + if (legsDone % 2 == 1) intoLeg else 0.0
                fixAt(north, east, index * secondsPerFix, USUAL_ACCURACY_METRES)
            }
        val driven = (fixCount - 1) * metresPerFix
        return 100.0 * (driven - DistanceCalculator.measure(points).metres) / driven
    }

    @Test
    fun `a fix every 2 s gains on turns taken at speed, and next to nothing on slow ones`() {
        // What Shawn's change of 2026-10-09 buys on this route, with fixes as good as his phone
        // usually gives. Rule 3 counts a step only 16 m from the last one, so at 25 km/h a
        // step is counted every 28 m where it was every 35 m: the corner is cut much as before.
        val slowAt2 = townShortfallPercent(TRIP_SECONDS_PER_FIX, SLOW_METRES_PER_SECOND)
        val slowAt5 = townShortfallPercent(FIX_INTERVAL_SECONDS, SLOW_METRES_PER_SECOND)
        assertTrue("25 km/h, every 2 s: $slowAt2 %", slowAt2 in 1.6..2.0)
        assertTrue("25 km/h, every 5 s: $slowAt5 %", slowAt5 in 2.0..2.3)

        // At 50 km/h a step was every 70 m and is every 28 m now: the loss is well under half.
        val briskAt2 = townShortfallPercent(TRIP_SECONDS_PER_FIX, BRISK_METRES_PER_SECOND)
        val briskAt5 = townShortfallPercent(FIX_INTERVAL_SECONDS, BRISK_METRES_PER_SECOND)
        assertTrue("50 km/h, every 2 s: $briskAt2 %", briskAt2 in 1.5..1.9)
        assertTrue("50 km/h, every 5 s: $briskAt5 %", briskAt5 in 4.0..4.5)
    }

    @Test
    fun `the last stretch of a trip, short of the threshold, is not counted`() {
        for (accuracy in listOf(5f, 10f, 25f)) {
            // 1 100 m at speed, then 90 m at walking pace into a parking spot.
            val drive =
                driveNorth(fixCount = 11, metresPerFix = 110.0)
                    .map { it.copy(accuracyMetres = accuracy) }
            val creep =
                List(9) { index ->
                    fixAt(
                        northMetres = 1_100.0 + (index + 1) * 10.0,
                        second = 55 + index * FIX_INTERVAL_SECONDS,
                        accuracyMetres = accuracy,
                    )
                }

            val uncounted = 1_190.0 - DistanceCalculator.measure(drive + creep).metres

            // The threshold is twice the two fixes' stated error: 20 m, 40 m and 100 m.
            val threshold = NOISE_SAFETY_FACTOR * 2 * accuracy
            assertTrue("at $accuracy m: $uncounted m uncounted", uncounted in 0.0..threshold)
            if (accuracy == 25f) assertTrue("was $uncounted", uncounted > 80.0)
        }
    }
}
