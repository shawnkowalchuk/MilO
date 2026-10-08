package com.shawnkowalchuk.milo.platform.car

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.platform.system.PreflightProblem
import com.shawnkowalchuk.milo.platform.trip.StartFailure
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the Android Auto screen redraws. Two rules keep it calm: a content that prints the same
 * as the last one is not a change at all, and the running figures of a trip wait for the gap
 * between refreshes while everything else is drawn at once.
 */
class CarScreenRefreshTest {
    private val recording = TripActivity(trip = carTrip(12_400.0), truckConnected = true)

    @Test
    fun `figures that print the same make the same content, so nothing is redrawn`() {
        val aFixLater = TripActivity(trip = carTrip(12_449.0), truckConnected = true)

        // 12.40 km and 12.449 km both print as 12.4, and 23 min 0 s and 23 min 59 s as 23 min.
        assertEquals(carContent(recording), carContent(aFixLater, nowMs = NOW_MS + 59_000))
    }

    @Test
    fun `the running figures of an open trip wait for the gap between refreshes`() {
        val before = carContent(recording)
        val further = carContent(TripActivity(trip = carTrip(12_900.0), truckConnected = true))
        val aMinuteOn = carContent(recording, nowMs = NOW_MS + MINUTE_MS)

        assertTrue(before.differsOnlyInTripFigures(further))
        assertTrue(before.differsOnlyInTripFigures(aMinuteOn))
    }

    @Test
    fun `a change of the unit in Settings is drawn at once, with or without a trip`() {
        val inKilometres = carContent(recording)
        val inMiles = carContent(recording, unit = DistanceUnit.MILES)

        // 12.4 km is 7.7 mi: other figures, and another unit to write after them.
        assertEquals("12.4", inKilometres.trip?.kilometres)
        assertEquals("7.7", inMiles.trip?.kilometres)
        assertEquals(DistanceUnit.MILES, inMiles.unit)
        assertFalse(inKilometres.differsOnlyInTripFigures(inMiles))
        // Without a trip and with no trips today nothing printed changes, but the content does,
        // so that the unit a later figure is written with is the new one.
        val idle = TripActivity(truckConnected = true)
        assertFalse(carContent(idle) == carContent(idle, unit = DistanceUnit.MILES))
        // A figure that moves on in miles still waits for the gap, as it does in kilometres.
        val further = TripActivity(trip = carTrip(12_900.0), truckConnected = true)
        assertTrue(inMiles.differsOnlyInTripFigures(carContent(further, unit = DistanceUnit.MILES)))
    }

    @Test
    fun `every other change is drawn at once`() {
        val before = carContent(recording)
        val idle = carContent(TripActivity(truckConnected = true))
        val furtherOn = carTrip(12_900.0)
        val refused = StartFailure(listOf(PreflightProblem.LOCATION_SWITCHED_OFF))

        val changes =
            mapOf(
                "the first content of a visit" to (null to before),
                "the trip ended" to (before to idle),
                "a trip started" to (idle to before),
                "the truck dropped out" to
                    (before to carContent(TripActivity(furtherOn, truckConnected = false))),
                "the grace period began" to
                    (before to carContent(TripActivity(carTrip(12_900.0, waitingForTruck = true)))),
                "today's trips arrived" to
                    (carContent(recording, today = null) to before),
                "a trip was added to today" to
                    (before to carContent(recording, today = todayOf(5_000.0))),
                "a start was refused" to (idle to carContent(TripActivity(startFailure = refused))),
            )

        for ((what, change) in changes) {
            val (from, to) = change
            assertFalse(what, from.differsOnlyInTripFigures(to))
        }
    }

    @Test
    fun `a content is never held back against itself`() {
        val shown = carContent(recording)

        // The screen skips an unchanged content before it asks; the rule must not say "wait".
        assertFalse(shown.differsOnlyInTripFigures(shown))
    }
}
