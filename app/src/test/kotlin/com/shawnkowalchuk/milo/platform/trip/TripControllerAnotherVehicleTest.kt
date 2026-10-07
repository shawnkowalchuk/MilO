package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A drive in another vehicle with the phone still connected to the parked truck (Shawn's
 * decision of 2026-10-07): the trip that the watch starts is removed for good, row and points,
 * when the truck is lost within its first kilometre. The rule itself is `leftInAnotherVehicle`
 * in `core/trip`; this is the controller and storage carrying it out.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerAnotherVehicleTest {
    private val world = FakeWorld()

    /** The parked truck "drives off" 450 m, then 150 m more, then its connection is lost. */
    private fun TestScope.leaveInAnotherVehicle(scene: ParkedScene) {
        scene.driveAndPark()
        scene.standThenDriveOff()
        scene.fix(northMetres = DRIVEN_METRES + 600, second = DRIVES_OFF_AT_SECOND + 45)
        world.truck.connected = false
        scene.controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "ACL disconnect")
        runCurrent()
        scene.timerRunsOut()
    }

    @Test
    fun `losing the truck 600 m after the watch started a trip removes that trip for good`() =
        runTest {
            val scene = ParkedScene(this, world)

            leaveInAnotherVehicle(scene)

            // The truck's own trip is all that is left, and the other's points are gone too.
            val kept = world.trips.rows.single()
            assertEquals(1L, kept.id)
            assertEquals(TripStatus.FINISHED, kept.status)
            assertTrue(world.points.rows.all { it.tripId == kept.id })
            assertNull(world.settings.current().drivenOffTripId)
            // Nothing is recorded or watched any more: the truck is gone.
            assertEquals(1, scene.service.stops)
            assertNull(scene.controller.activity.value.trip)
        }

    @Test
    fun `the log keeps what the trips table no longer holds`() = runTest {
        leaveInAnotherVehicle(ParkedScene(this, world))

        val line = world.logged(EventCategory.TRIP).single {
            it.startsWith("Trip 2:") &&
                "removed" in it
        }
        assertTrue(line, "removed for good as a drive in another vehicle" in line)
        assertTrue(line, "600 m on, under 1000 m" in line)
    }

    @Test
    fun `the trip that the watch starts is remembered in the settings file until it closes`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.driveAndPark()

            scene.standThenDriveOff()

            val second = world.trips.rows.last()
            assertEquals(second.id, world.settings.current().drivenOffTripId)
        }

    @Test
    fun `a drive of a kilometre and more before the truck is lost is the truck's, and is kept`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.driveAndPark()
            scene.standThenDriveOff()

            for (index in 1..15) {
                val second = DRIVES_OFF_AT_SECOND + 30 + index * 5
                scene.fix(northMetres = DRIVEN_METRES + 450 + index * 100, second = second)
            }
            world.truck.connected = false
            scene.controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "ACL disconnect")
            runCurrent()
            scene.timerRunsOut()

            assertEquals(2, world.trips.rows.size)
            assertEquals(TripStatus.FINISHED, world.trips.rows.last().status)
            assertNull(world.settings.current().drivenOffTripId)
        }

    @Test
    fun `a short trip that a new link started is never taken for another vehicle`() = runTest {
        val scene = ParkedScene(this, world)
        scene.connect()
        for (index in 0..6) scene.fix(northMetres = index * 100.0, second = index * 5)

        world.truck.connected = false
        scene.controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "ACL disconnect")
        runCurrent()
        scene.timerRunsOut()

        // 600 m, over the minimum: a trip like any other.
        val trip = world.trips.rows.single()
        assertEquals(TripStatus.FINISHED, trip.status)
        assertEquals(600.0, trip.distanceMetres, 1.0)
    }
}
