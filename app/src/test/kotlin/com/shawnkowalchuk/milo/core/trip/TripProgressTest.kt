package com.shawnkowalchuk.milo.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The running picture of the open trip that the screens and the no-movement guard read. */
class TripProgressTest {
    /** The wall-clock time of the fix taken [second] seconds into the track. */
    private fun timeOf(second: Int) = TRACK_START_WALL_CLOCK_MS + second * 1000L

    @Test
    fun `before the first fix nothing is known`() {
        val progress = TripProgress()

        assertEquals(0.0, progress.distance.metres, 0.0)
        assertNull(progress.lastMovementAtMs)
        assertNull(progress.lastFixAtMs)
    }

    @Test
    fun `a fix that adds distance is movement, and its time is remembered`() {
        val progress =
            TripProgress()
                .plus(fixAt(northMetres = 0.0, second = 0))
                .plus(fixAt(northMetres = 100.0, second = 5))

        assertEquals(100.0, progress.distance.metres, 0.5)
        assertEquals(timeOf(5), progress.lastMovementAtMs)
        assertEquals(2, progress.fixCount)
    }

    @Test
    fun `fixes from a parked truck are not movement`() {
        val driven = TripProgress.of(driveNorth(fixCount = 2, metresPerFix = 100.0))

        // Three more fixes within a few metres of the last one.
        val parked =
            driven
                .plus(fixAt(northMetres = 102.0, second = 10))
                .plus(fixAt(northMetres = 99.0, second = 15))
                .plus(fixAt(northMetres = 101.0, second = 20))

        assertEquals(driven.distance.metres, parked.distance.metres, 0.0)
        assertEquals(timeOf(5), parked.lastMovementAtMs)
        // The newest fix is still the trip's last sign of life.
        assertEquals(timeOf(20), parked.lastFixAtMs)
    }

    @Test
    fun `a fix that is not used still counts as a sign of life`() {
        val progress = TripProgress().plus(
            fixAt(northMetres = 0.0, second = 0, accuracyMetres = 80f),
        )

        assertEquals(0, progress.distance.acceptedCount)
        assertEquals(timeOf(0), progress.lastFixAtMs)
        assertEquals(1, progress.fixCount)
    }

    @Test
    fun `rebuilt from the stored points it is the same as built one fix at a time`() {
        // This is what makes a restart invisible: the picture is whatever the points say.
        val points =
            driveNorth(fixCount = 4, metresPerFix = 100.0) +
                fixAt(northMetres = 301.0, second = 20) +
                fixAt(northMetres = 420.0, second = 25)

        val live = points.fold(TripProgress()) { progress, point -> progress.plus(point) }

        assertEquals(live, TripProgress.of(points))
        assertEquals(timeOf(25), live.lastMovementAtMs)
    }
}
