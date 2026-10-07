package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.WAITING_LIMIT_MS
import com.shawnkowalchuk.milo.core.trip.fixAt
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.setParkedLimitSeconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parked rule through the trip controller (ADR-002, amendment 28): a trip is closed where
 * the truck stopped although Bluetooth stays connected, with real storage of the trip, its
 * points and the settings behind it. What follows the close is in [TripControllerWaitingTest];
 * the rule itself is tested in `core/trip`.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerParkedTest {
    private val world = FakeWorld()

    @Test
    fun `a trip is closed at the time and place the truck stopped, still connected`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()

        val trip = world.trips.rows.single()
        assertEquals(TripStatus.FINISHED, trip.status)
        // Where and when it last moved, not ten minutes later.
        assertEquals(scene.timeOf(STOPPED_AT_SECOND), trip.endedAtMs)
        assertEquals(DRIVEN_METRES, trip.distanceMetres, 1.0)
        val stoppedAt = fixAt(northMetres = DRIVEN_METRES, second = STOPPED_AT_SECOND)
        assertEquals(stoppedAt.latitude, trip.endLatitude)
        assertEquals(stoppedAt.longitude, trip.endLongitude)
        // The ten minutes of standing are stored, and count for nothing.
        assertEquals(21 + 119, world.points.rows.size)
    }

    @Test
    fun `the log says why the trip ended and when the truck last moved, in one line`() = runTest {
        ParkedScene(this, world).driveAndPark()

        val closed = world.logged(EventCategory.TRIP).filter { it.startsWith("Trip 1:") }
        assertEquals(1, closed.size)
        // The fake world's clock is UTC: the track starts at 12:00:00, and it stopped 100 s in.
        val expected =
            "Trip 1: finished, 2000 m; ended by NO_MOVEMENT (the truck was parked: it last " +
                "moved at 12:01:40 and then stood still for 10 min); "
        assertTrue(closed.single(), closed.single().startsWith(expected))
    }

    @Test
    fun `the service is not stopped, it is told to watch the parked truck`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()

        assertEquals(0, scene.service.stops)
        assertTrue(scene.service.watchingParked)
        assertFalse(scene.service.recording)
        // Its timer is now the limit on waiting, counted from the close.
        assertEquals(world.nowMs + WAITING_LIMIT_MS, scene.service.checkAtMs)
        val activity = scene.controller.activity.value
        assertNull(activity.trip)
        assertEquals(ParkedTruckWatch.WAITING_TO_MOVE, activity.parked)
        assertEquals(true, activity.truckConnected)
    }

    @Test
    fun `a truck that connects and is never driven leaves one line and no trip`() = runTest {
        val scene = ParkedScene(this, world)
        scene.connect()
        val startedAtMs = world.nowMs

        scene.timerRunsOut()

        // Discarded like any other trip under the minimum distance, with the one line every
        // closed trip gets; the wait that follows has a line of its own.
        val trip = world.trips.rows.single()
        assertEquals(TripStatus.DISCARDED, trip.status)
        assertEquals(startedAtMs, trip.endedAtMs)
        val aboutTheTrip = world.logged(EventCategory.TRIP).filter { it.startsWith("Trip 1") }
        assertEquals(2, aboutTheTrip.size)
        assertEquals("Trip 1 started by TRUCK", aboutTheTrip.first())
        assertTrue(aboutTheTrip.last().startsWith("Trip 1: discarded: 0 m is under the minimum"))
        assertTrue(scene.service.watchingParked)
    }

    @Test
    fun `a slow crawl through a jam does not end the trip`() = runTest {
        val scene = ParkedScene(this, world)
        scene.connect()
        // Half a metre a second for twenty minutes, the truck read every minute.
        for (index in 0..240) {
            scene.fix(northMetres = index * 2.5, second = index * 5)
            if (index % 12 == 11) scene.minuteCheck(seconds = 0)
        }

        assertEquals(TripStatus.OPEN, world.trips.rows.single().status)
        assertTrue(scene.service.recording)
    }

    @Test
    fun `one bad fix while the truck stands does not keep the trip open`() = runTest {
        val scene = ParkedScene(this, world)
        scene.connect()
        drive(world, scene.controller, fixCount = 21, metresPerFix = 100.0)
        // Standing. Eight minutes in, one fix lands 200 m up the road; the next is back.
        for (second in STOPPED_AT_SECOND + 5..STOPPED_AT_SECOND + 595 step 5) {
            val stray = second == STOPPED_AT_SECOND + 480
            scene.fix(northMetres = if (stray) DRIVEN_METRES + 200 else DRIVEN_METRES, second)
        }

        // The timer is still the one for the real stop, and it closes the trip there.
        assertEquals(scene.timeOf(STOPPED_AT_SECOND) + PARKED_LIMIT_MS, scene.service.checkAtMs)
        scene.timerRunsOut()

        val trip = world.trips.rows.single()
        assertEquals(scene.timeOf(STOPPED_AT_SECOND), trip.endedAtMs)
        assertEquals(DRIVEN_METRES, trip.distanceMetres, 1.0)
    }

    @Test
    fun `a fix that arrives after a timer the phone slept through closes the trip all the same`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.connect()
            drive(world, scene.controller, fixCount = 21, metresPerFix = 100.0)

            // No timer fires. The first fix more than ten seconds past the limit stands in.
            val late = STOPPED_AT_SECOND + 615
            scene.fix(northMetres = DRIVEN_METRES + 1, second = late)

            val trip = world.trips.rows.single()
            assertEquals(TripStatus.FINISHED, trip.status)
            assertEquals(scene.timeOf(STOPPED_AT_SECOND), trip.endedAtMs)
            assertTrue(scene.service.watchingParked)
        }

    @Test
    fun `the limit is the one on the Settings screen, from the next event on`() = runTest {
        val scene = ParkedScene(this, world)
        scene.connect()
        drive(world, scene.controller, fixCount = 21, metresPerFix = 100.0)
        world.settings.setParkedLimitSeconds(5 * 60)

        // Five minutes of standing. The first reading after the change closes the trip.
        scene.fix(northMetres = DRIVEN_METRES + 1, second = STOPPED_AT_SECOND + 290)
        assertEquals(TripStatus.OPEN, world.trips.rows.single().status)
        scene.minuteCheck(seconds = 10)

        assertEquals(TripStatus.FINISHED, world.trips.rows.single().status)
        assertTrue(world.logged(EventCategory.TRIP).any { it.contains("stood still for 5 min") })
    }

    @Test
    fun `a manual trip with no truck is closed the same way, and then the service stops`() =
        runTest {
            val (controller, service) = recordingManualTrip(world)
            drive(world, controller, fixCount = 11, metresPerFix = 100.0)
            val stoppedAtMs = world.nowMs

            world.nowMs = stoppedAtMs + PARKED_LIMIT_MS
            controller.onCheckDue(checkNotNull(service.checkAtMs))
            runCurrent()

            val trip = world.trips.rows.single()
            assertEquals(TripStatus.FINISHED, trip.status)
            assertEquals(stoppedAtMs, trip.endedAtMs)
            // No truck, so nothing to wait beside.
            assertEquals(1, service.stops)
            assertFalse(service.watchingParked)
            assertNull(world.settings.current().parkedTruck)
            assertNull(controller.activity.value.parked)
        }

    @Test
    fun `the place the truck is parked at is stored with the wait`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()

        val stored = checkNotNull(world.settings.current().parkedTruck)
        val trip = world.trips.rows.single()
        assertEquals(world.nowMs, stored.sinceMs)
        val place = checkNotNull(stored.place)
        assertEquals(trip.endLatitude, place.latitude)
        assertEquals(trip.endLongitude, place.longitude)
        assertEquals(trip.endedAtMs, place.atMs)
        assertNotNull(world.logged(EventCategory.TRIP).singleOrNull { it == WAITING_BEGAN })
    }
}
