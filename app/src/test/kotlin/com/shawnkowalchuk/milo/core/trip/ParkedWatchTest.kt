package com.shawnkowalchuk.milo.core.trip

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The watch on a parked, connected truck: when has it really driven off? */
class ParkedWatchTest {
    /** How often MilO reads the position while it waits: every 30 seconds. */
    private val watchSeconds = 30

    /** Where the truck is parked: the last counted fix of the trip before, an hour ago. */
    private val place = fixAt(northMetres = 0.0, second = 0)

    /** The wall-clock time of the fix taken [second] seconds into the track. */
    private fun timeOf(second: Int) = TRACK_START_WALL_CLOCK_MS + second * 1000L

    private fun ParkedWatch.after(vararg fixes: TrackPoint): ParkedWatch =
        fixes.fold(this) { watch, fix -> watch.plus(fix) }

    @Test
    fun `a parked truck has not moved, however long it stands`() {
        // A night of fixes scattered around the place as an accuracy of 5 m implies.
        val random = Random(20261006)
        var watch = ParkedWatch.at(place)
        for (index in 1..1_440) {
            val north = random.nextGaussian() * GOOD_ACCURACY_METRES
            val east = random.nextGaussian() * GOOD_ACCURACY_METRES
            watch = watch.plus(fixAt(north, east, second = 3_600 + index * watchSeconds))
            assertNull("fix $index", watch.movedAtMs)
        }
        assertEquals(listOf(place), watch.tripStart)
    }

    @Test
    fun `one fix somewhere else is not yet movement`() {
        val watch = ParkedWatch.at(place).after(fixAt(northMetres = 150.0, second = 3_630))

        // It may be one bad fix. The next fix says which.
        assertNull(watch.movedAtMs)
    }

    @Test
    fun `a second fix that bears the first out is movement, dated at the first`() {
        val first = fixAt(northMetres = 150.0, second = 3_630)
        val second = fixAt(northMetres = 420.0, second = 3_660)

        val watch = ParkedWatch.at(place).after(fixAt(2.0, second = 3_600), first, second)

        assertEquals(timeOf(3_630), watch.movedAtMs)
        // The next trip starts where the truck was parked, and the stretch to the first fix
        // is counted: nothing of the drive is lost.
        assertEquals(listOf(place, first, second), watch.tripStart)
        assertEquals(420.0, DistanceCalculator.measure(watch.tripStart).metres, 0.5)
    }

    @Test
    fun `one bad fix that the next fix takes back is not movement`() {
        val bad = fixAt(northMetres = 200.0, second = 3_630)
        val back = fixAt(northMetres = 3.0, second = 3_660)

        val watch = ParkedWatch.at(place).after(bad, back)

        assertNull(watch.movedAtMs)
        assertEquals(listOf(place), watch.tripStart)
        // And the watch carries on from the parked place, not from the bad fix.
        val later = watch.after(fixAt(4.0, second = 3_690), fixAt(1.0, second = 3_720))
        assertNull(later.movedAtMs)
    }

    @Test
    fun `a crawl out of the yard is movement once it is further than jitter can be`() {
        // Two metres every half minute. With 5 m fixes the truck has moved at 20 m.
        var watch = ParkedWatch.at(place)
        var movedAtFix: Int? = null
        for (index in 1..20) {
            watch = watch.plus(fixAt(northMetres = index * 2.0, second = 3_600 + index * 30))
            if (watch.movedAtMs != null && movedAtFix == null) movedAtFix = index
        }

        // The tenth fix is 20 m away; the eleventh bears it out.
        assertEquals(11, movedAtFix)
        assertEquals(timeOf(3_600 + 10 * 30), watch.movedAtMs)
    }

    @Test
    fun `poor fixes need the truck to be further away before it has moved`() {
        // 20 m accuracy on both ends: 80 m is the least that cannot be jitter.
        val poorPlace = fixAt(northMetres = 0.0, second = 0, accuracyMetres = 20f)
        val near =
            ParkedWatch.at(poorPlace).after(
                fixAt(northMetres = 60.0, second = 3_630, accuracyMetres = 20f),
                fixAt(northMetres = 70.0, second = 3_660, accuracyMetres = 20f),
            )
        val far =
            ParkedWatch.at(poorPlace).after(
                fixAt(northMetres = 90.0, second = 3_630, accuracyMetres = 20f),
                fixAt(northMetres = 180.0, second = 3_660, accuracyMetres = 20f),
            )

        assertNull(near.movedAtMs)
        assertEquals(timeOf(3_630), far.movedAtMs)
    }

    @Test
    fun `a fix too inaccurate to use says nothing either way`() {
        val watch =
            ParkedWatch.at(place).after(
                fixAt(northMetres = 900.0, second = 3_630, accuracyMetres = 80f),
                fixAt(northMetres = 900.0, second = 3_660, accuracyMetres = 80f),
            )

        assertNull(watch.movedAtMs)
    }

    @Test
    fun `one displaced fix that nothing bears out in time is dropped, not kept for later`() {
        // One usable fix 60 m off, a quarter of an hour of fixes too poor to use (the phone
        // indoors), then the truck drives off. Kept, the old fix would be "borne out" by the
        // first good one, and the trip would be dated fifteen minutes before it began.
        val stray = fixAt(northMetres = 60.0, second = 3_630)
        val poor =
            (1..30).map { index ->
                fixAt(northMetres = 500.0, second = 3_630 + index * 30, accuracyMetres = 80f)
            }
        val first = fixAt(northMetres = 300.0, second = 4_560)
        val second = fixAt(northMetres = 650.0, second = 4_590)

        val waiting = ParkedWatch.at(place).after(stray, *poor.toTypedArray())
        assertNull(waiting.movedAtMs)
        val oneGoodFix = waiting.after(first)
        assertNull(oneGoodFix.movedAtMs)
        val moved = oneGoodFix.after(second)

        assertEquals(timeOf(4_560), moved.movedAtMs)
        // The trip starts at the parked place with the two fixes of the drive, and nothing of
        // the quarter of an hour before it.
        assertEquals(listOf(place, first, second), moved.tripStart)
    }

    @Test
    fun `a fix that is borne out within two minutes still dates the trip`() {
        // One fix of the watch was too poor to use; the one after it bears the first out.
        val first = fixAt(northMetres = 150.0, second = 3_630)
        val poor = fixAt(northMetres = 400.0, second = 3_660, accuracyMetres = 80f)
        val third = fixAt(northMetres = 700.0, second = 3_690)
        val edge = fixAt(northMetres = 700.0, second = 3_630 + 120)
        val tooLate = fixAt(northMetres = 700.0, second = 3_630 + 121)

        assertEquals(timeOf(3_630), ParkedWatch.at(place).after(first, poor, third).movedAtMs)
        assertEquals(timeOf(3_630), ParkedWatch.at(place).after(first, edge).movedAtMs)
        assertNull(ParkedWatch.at(place).after(first, tooLate).movedAtMs)
    }

    @Test
    fun `a truck that never moved in its trip is parked at that trip's newest usable fix`() {
        // Connected and left idling: the trip is cut at its start, before its first fix, so it
        // has no end position, and its fixes were all taken while it stood.
        val standing = listOf(fixAt(1.0, second = 5), fixAt(2.0, second = 10))
        val unusable = fixAt(northMetres = 90.0, second = 15, accuracyMetres = 80f)
        val neverMoved =
            TripClosing.close(standing + unusable, TRACK_START_WALL_CLOCK_MS, 300.0)
        assertNull(neverMoved.end)

        assertEquals(standing.last(), ParkedWatch.placeAfter(neverMoved, standing + unusable))

        // A trip that did move is parked where it ends, whatever was recorded while it stood.
        val drive = driveNorth(fixCount = 5, metresPerFix = 100.0)
        val after = drive + fixAt(northMetres = 403.0, second = 300)
        val moved = TripClosing.close(after, drive.last().wallClockMs, 300.0)
        assertEquals(drive.last(), ParkedWatch.placeAfter(moved, after))
        // And with no usable fix at all nobody knows where it stands.
        val blind = TripClosing.close(listOf(unusable), 0, 300.0)
        assertNull(ParkedWatch.placeAfter(blind, listOf(unusable)))
    }

    @Test
    fun `with no parked place the first usable fix becomes it`() {
        // The trip before had no usable fix, so nothing says where the truck stands.
        val first = fixAt(northMetres = 500.0, second = 3_600)
        val away = fixAt(northMetres = 700.0, second = 3_630)
        val further = fixAt(northMetres = 950.0, second = 3_660)

        val standing = ParkedWatch.at(null).after(first, fixAt(502.0, second = 3_630))
        val moved = ParkedWatch.at(null).after(first, away, further)

        assertEquals(first, standing.place)
        assertNull(standing.movedAtMs)
        assertEquals(timeOf(3_630), moved.movedAtMs)
        assertEquals(listOf(first, away, further), moved.tripStart)
    }

    @Test
    fun `a place stored without its time since boot is still a place to move away from`() {
        // After a restart the place comes from the settings file, with no elapsed time.
        val stored = place.copy(elapsedRealtimeMs = 0)

        val watch =
            ParkedWatch.at(stored).after(
                fixAt(northMetres = 150.0, second = 40_000),
                fixAt(northMetres = 420.0, second = 40_030),
            )

        assertEquals(timeOf(40_000), watch.movedAtMs)
    }
}
