package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.fixAt
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
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
 * The wait beside a parked truck through the trip controller, continued from
 * [TripControllerWaitingTest]: the drives the review of 2026-10-06 found would have gone
 * unrecorded or been cut short. A stop after End and Start, a stop on Android Auto's cable with
 * Bluetooth down, a truck that stops again at once, one that never moved in its trip, and one
 * stray fix long before the truck drives off.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerWaitingEdgesTest {
    private val world = FakeWorld()

    @Test
    fun `End, then Start, then a stop with the truck connected - the drive after it is recorded`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.connect()
            drive(world, scene.controller, fixCount = 21, metresPerFix = 100.0)
            scene.controller.onTrigger(TripTrigger.MANUAL_END, "End button")
            runCurrent()
            assertNotNull(world.settings.current().autoStartHeldOffSinceMs)

            // Later, the truck connected throughout, Shawn presses Start and drives a kilometre.
            world.nowMs = scene.timeOf(200)
            scene.controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
            runCurrent()
            for (index in 1..10) {
                scene.fix(northMetres = DRIVEN_METRES + index * 100, second = 200 + index * 5)
            }
            scene.timerRunsOut()

            // The stop closed the trip. The hold-off is given up for the wait.
            assertEquals(TripStatus.FINISHED, world.trips.rows.last().status)
            assertNull(world.settings.current().autoStartHeldOffSinceMs)
            assertNotNull(world.settings.current().parkedTruck)
            assertTrue(scene.service.watchingParked)
            val released = "Automatic start no longer held off: TRIP_PARKED"
            assertTrue(world.logged(EventCategory.TRIP).contains(released))

            scene.fix(northMetres = DRIVEN_METRES + 1_150, second = 2_000)
            scene.fix(northMetres = DRIVEN_METRES + 1_450, second = 2_030)

            assertEquals(3, world.trips.rows.size)
            assertEquals(TripStatus.OPEN, world.trips.rows.last().status)
            assertTrue(scene.service.recording)
        }

    @Test
    fun `on Android Auto's cable with Bluetooth down, the drive after a stop is recorded`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.connect()
            scene.controller.onAndroidAuto(connected = true, "CarConnection reports type 1")
            drive(world, scene.controller, fixCount = 21, metresPerFix = 100.0)
            world.truck.connected = false
            scene.controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "ACL disconnect")
            runCurrent()
            // Android Auto holds the trip: no grace period.
            assertEquals(emptyList<String>(), world.logged(EventCategory.GRACE))

            scene.timerRunsOut()

            assertEquals(TripStatus.FINISHED, world.trips.rows.single().status)
            assertEquals(0, scene.service.stops)
            assertTrue(scene.service.watchingParked)
            // The minute check goes on reading the truck as gone, and that ends nothing.
            repeat(3) { scene.minuteCheck() }
            assertTrue(scene.service.watchingParked)

            scene.standThenDriveOff()

            assertEquals(2, world.trips.rows.size)
            assertEquals(TripStatus.OPEN, world.trips.rows.last().status)
            assertEquals(emptyList<String>(), world.logged(EventCategory.GRACE))
        }

    @Test
    fun `Android Auto disconnecting ends a wait it alone was holding, and the service stops`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.connect()
            scene.controller.onAndroidAuto(connected = true, "CarConnection reports type 1")
            drive(world, scene.controller, fixCount = 21, metresPerFix = 100.0)
            world.truck.connected = false
            scene.controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "ACL disconnect")
            runCurrent()
            scene.timerRunsOut()
            assertTrue(scene.service.watchingParked)

            scene.controller.onAndroidAuto(connected = false, "CarConnection reports type 0")
            runCurrent()

            assertEquals(1, scene.service.stops)
            assertFalse(scene.service.watchingParked)
            assertNull(world.settings.current().parkedTruck)
            assertEquals(1, world.trips.rows.size)
        }

    @Test
    fun `a truck that stops again right after driving off keeps the whole of that short drive`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.driveAndPark()

            // Two fixes show it leave: 150 m away, then 450 m. There it stops for good.
            scene.standThenDriveOff()

            // It last moved at the second of them. Told only of the first, the rules would
            // close the trip half a minute early and cut it at 150 m, under the minimum.
            val lastMoved = scene.timeOf(DRIVES_OFF_AT_SECOND + 30)
            assertEquals(lastMoved + PARKED_LIMIT_MS, scene.service.checkAtMs)
            scene.timerRunsOut()

            val second = world.trips.rows.last()
            assertEquals(TripStatus.FINISHED, second.status)
            assertEquals(450.0, second.distanceMetres, 1.0)
            assertEquals(lastMoved, second.endedAtMs)
        }

    @Test
    fun `a truck that never moved in its trip is waited for where its last fix was`() = runTest {
        val scene = ParkedScene(this, world)
        scene.connect()
        // Idling for ten minutes, 4.6 km up the road, the fixes wandering a metre or two.
        for (second in 5..595 step 5) {
            scene.fix(northMetres = 4_600.0 + second % 3, second = second)
        }
        scene.timerRunsOut()

        // The trip is cut at its start and has no end position. The wait still has a place.
        assertEquals(TripStatus.DISCARDED, world.trips.rows.single().status)
        assertNull(world.trips.rows.single().endLatitude)
        val place = checkNotNull(world.settings.current().parkedTruck?.place)
        assertEquals(fixAt(northMetres = 4_601.0, second = 595).latitude, place.latitude, 0.0)

        // It drives off within half a minute: the first fix of the wait is already 350 m on.
        val pointsBefore = world.points.rows.size
        scene.fix(northMetres = 4_950.0, second = 620)
        scene.fix(northMetres = 5_400.0, second = 650)

        val carried = world.points.rows.drop(pointsBefore)
        assertEquals(place.latitude, carried.first().latitude, 0.0)
        // So that first stretch is counted: 799 m from the parked place, not 450 m.
        val current = checkNotNull(scene.controller.activity.value.trip)
        assertEquals(799.0, current.distanceMetres, 1.0)
    }

    @Test
    fun `one stray fix long before the truck drives off does not date the trip back`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()
        val pointsBefore = world.points.rows.size

        // One usable fix 60 m off, then a quarter of an hour of fixes too poor to use.
        scene.fix(northMetres = DRIVEN_METRES + 60, second = 730)
        for (index in 1..30) {
            scene.fix(DRIVEN_METRES + 500, second = 730 + index * 30, accuracyMetres = 80f)
        }
        assertEquals(1, world.trips.rows.size)
        scene.fix(northMetres = DRIVEN_METRES + 300, second = 1_660)
        scene.fix(northMetres = DRIVEN_METRES + 650, second = 1_690)

        // Dated at the first fix of the drive, and still running, with its parked limit
        // counted from the last: not a trip of fifteen minutes ago, closed at once.
        val second = world.trips.rows.last()
        assertEquals(TripStatus.OPEN, second.status)
        assertEquals(scene.timeOf(1_660), second.startedAtMs)
        assertEquals(scene.timeOf(1_690) + PARKED_LIMIT_MS, scene.service.checkAtMs)
        // What is stored is what the watch counts as the drive: the place and its two fixes.
        val carried = world.points.rows.drop(pointsBefore)
        assertEquals(3, carried.size)
        assertEquals(world.trips.rows.first().endLatitude, carried.first().latitude)
        val current = checkNotNull(scene.controller.activity.value.trip)
        assertEquals(650.0, current.distanceMetres, 1.0)
    }
}
