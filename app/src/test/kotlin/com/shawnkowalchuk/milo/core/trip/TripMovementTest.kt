package com.shawnkowalchuk.milo.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the trip rules are told about movement, fix by fix: the parked rule counts from the last
 * real movement, so one bad fix must neither be movement for long nor leave a trace behind.
 */
class TripMovementTest {
    private val tripStartedAtMs = TRACK_START_WALL_CLOCK_MS - 60_000

    /** The wall-clock time of the fix taken [second] seconds into the track. */
    private fun timeOf(second: Int) = TRACK_START_WALL_CLOCK_MS + second * 1000L

    /** Feeds [fixes] one at a time and collects what the rules would have been told. */
    private fun told(fixes: List<TrackPoint>): List<TripEvent> {
        var progress = TripProgress()
        val events = mutableListOf<TripEvent>()
        for (fix in fixes) {
            val next = progress.plus(fix)
            next.movementSince(progress, tripStartedAtMs, fix.wallClockMs)?.let { events += it }
            progress = next
        }
        return events
    }

    @Test
    fun `every fix of a drive that adds distance is reported as movement`() {
        val events = told(driveNorth(fixCount = 4, metresPerFix = 100.0))

        assertEquals(listOf(5, 10, 15).map { TripEvent.Moved(timeOf(it)) }, events)
    }

    @Test
    fun `a parked truck reports nothing`() {
        val parked =
            driveNorth(fixCount = 2, metresPerFix = 100.0) +
                listOf(102.0, 98.0, 101.0, 99.0).mapIndexed { index, north ->
                    fixAt(northMetres = north, second = 10 + index * 5)
                }

        assertEquals(listOf(TripEvent.Moved(timeOf(5))), told(parked))
    }

    @Test
    fun `one bad fix while parked is reported and then taken back to the last real movement`() {
        val fixes =
            driveNorth(fixCount = 3, metresPerFix = 100.0) +
                fixAt(northMetres = 201.0, second = 15) +
                // 200 m to the side for one fix, and back.
                fixAt(northMetres = 200.0, eastMetres = 200.0, second = 20) +
                fixAt(northMetres = 199.0, second = 25)

        val events = told(fixes)

        assertEquals(
            listOf(
                TripEvent.Moved(timeOf(5)),
                TripEvent.Moved(timeOf(10)),
                TripEvent.Moved(timeOf(20)),
                TripEvent.MoveTakenBack(lastMovedAtMs = timeOf(10), atMs = timeOf(25)),
            ),
            events,
        )
        assertEquals(timeOf(10), TripProgress.of(fixes).lastMovementAtMs)
    }

    @Test
    fun `a bad fix before the truck ever moved is taken back to the trip's start`() {
        val fixes =
            listOf(
                fixAt(northMetres = 0.0, second = 0),
                fixAt(northMetres = 150.0, second = 5),
                fixAt(northMetres = 2.0, second = 10),
            )

        assertEquals(
            listOf(
                TripEvent.Moved(timeOf(5)),
                TripEvent.MoveTakenBack(lastMovedAtMs = tripStartedAtMs, atMs = timeOf(10)),
            ),
            told(fixes),
        )
        assertNull(TripProgress.of(fixes).lastMovementAtMs)
    }

    @Test
    fun `a slow crawl is movement each time it gets further than jitter can be`() {
        // Half a metre a second: 2.5 m a fix. Counted every eighth fix, at 20 m.
        val crawl = driveNorth(fixCount = 33, metresPerFix = 2.5)

        val events = told(crawl)

        assertEquals(listOf(40, 80, 120, 160).map { TripEvent.Moved(timeOf(it)) }, events)
    }

    @Test
    fun `a fix that is not used changes nothing about movement`() {
        val fixes =
            driveNorth(fixCount = 2, metresPerFix = 100.0) +
                fixAt(northMetres = 900.0, second = 10, accuracyMetres = 80f)

        assertEquals(listOf(TripEvent.Moved(timeOf(5))), told(fixes))
    }
}
