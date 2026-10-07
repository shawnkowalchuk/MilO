package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.WAITING_LIMIT_MS
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
 * [TripControllerWaitingTest]: the limit after which MilO stops watching, and a restart of the
 * process in the middle of a wait.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerWaitingLimitTest {
    private val world = FakeWorld()

    // ---- The limit on waiting ---------------------------------------------------------------------

    @Test
    fun `at the waiting limit the service stops, the log says so, and no trip starts`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()
        val sinceMs = world.nowMs

        scene.timerRunsOut()

        assertEquals(sinceMs + WAITING_LIMIT_MS, world.nowMs)
        assertEquals(1, scene.service.stops)
        assertEquals(1, world.trips.rows.size)
        assertNull(world.settings.current().parkedTruck)
        assertTrue(world.logged(EventCategory.TRIP).last().contains("stopped watching it"))
        assertEquals(
            ParkedTruckWatch.NO_LONGER_WATCHED,
            scene.controller.activity.value.parked,
        )

        // The minute check that was still under way must not count as a reconcile.
        scene.minuteCheck(seconds = 1)
        assertEquals(1, world.trips.rows.size)
    }

    @Test
    fun `once MilO has stopped watching, opening it starts a trip`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()
        scene.timerRunsOut()

        world.nowMs += 60 * 60_000L
        scene.controller.onTrigger(TripTrigger.RECONCILE, "app opened")
        runCurrent()

        assertEquals(TripStatus.OPEN, world.trips.rows.last().status)
        assertEquals(2, world.trips.rows.size)
        assertNull(scene.controller.activity.value.parked)
    }

    // ---- The wait survives the process ---------------------------------------------------------

    @Test
    fun `a restart while waiting asks for the service and watches again, with no new trip`() =
        runTest {
            ParkedScene(this, world).driveAndPark()
            val stored = world.settings.current().parkedTruck

            // MilO's process is killed. An hour later Android restarts the trip service.
            world.nowMs += 60 * 60_000L
            val (controller, service) = world.newProcess(backgroundScope)
            controller.onServiceStarted(service, request = null)
            runCurrent()

            assertEquals(1, world.trips.rows.size)
            assertTrue(service.watchingParked)
            assertEquals(stored, world.settings.current().parkedTruck)
            assertEquals(ParkedTruckWatch.WAITING_TO_MOVE, controller.activity.value.parked)
            // Still the limit counted from when the wait began, not from the restart.
            assertEquals(checkNotNull(stored).sinceMs + WAITING_LIMIT_MS, service.checkAtMs)
        }

    @Test
    fun `a process started by something else brings the service up before it watches`() = runTest {
        ParkedScene(this, world).driveAndPark()

        world.nowMs += 60 * 60_000L
        val (controller, service) = world.newProcess(backgroundScope)
        controller.onTrigger(TripTrigger.RECONCILE, "process start")
        runCurrent()

        // Nothing is watched until the service is in the foreground, and the wait stays stored.
        assertEquals(1, service.startRequests.size)
        assertFalse(service.watchingParked)
        assertNotNull(world.settings.current().parkedTruck)
        service.comeUp()
        runCurrent()
        assertTrue(service.watchingParked)
        assertEquals(1, world.trips.rows.size)
    }

    @Test
    fun `after a restart the truck driving off still starts the trip at the stored place`() =
        runTest {
            val before = ParkedScene(this, world)
            before.driveAndPark()
            val first = world.trips.rows.single()
            val pointsBefore = world.points.rows.size

            val scene = ParkedScene(this, world)
            val process = world.newProcess(backgroundScope)
            scene.controller = process.first
            scene.service = process.second
            scene.controller.onServiceStarted(scene.service, request = null)
            runCurrent()
            scene.standThenDriveOff()

            val second = world.trips.rows.last()
            assertEquals(TripStatus.OPEN, second.status)
            val carried = world.points.rows.drop(pointsBefore)
            assertEquals(3, carried.size)
            assertEquals(first.endLatitude, carried.first().latitude)
            assertEquals(
                450.0,
                checkNotNull(scene.controller.activity.value.trip).distanceMetres,
                1.0,
            )
        }

    @Test
    fun `a restart that finds a trip parked too long closes it and watches from where it ended`() =
        runTest {
            // The process dies while the truck stands, before the limit, and comes back after.
            val before = ParkedScene(this, world)
            before.connect()
            drive(world, before.controller, fixCount = 21, metresPerFix = 100.0)
            before.fix(northMetres = DRIVEN_METRES + 1, second = STOPPED_AT_SECOND + 300)

            val scene = ParkedScene(this, world)
            world.nowMs = scene.timeOf(STOPPED_AT_SECOND + 900)
            val process = world.newProcess(backgroundScope)
            scene.controller = process.first
            scene.service = process.second
            scene.controller.onServiceStarted(scene.service, request = null)
            runCurrent()

            val first = world.trips.rows.single()
            assertEquals(TripStatus.FINISHED, first.status)
            assertEquals(scene.timeOf(STOPPED_AT_SECOND), first.endedAtMs)
            assertTrue(scene.service.watchingParked)
            assertEquals(first.endLatitude, world.settings.current().parkedTruck?.place?.latitude)

            // And the trip that movement starts begins at that place, not at the first new fix.
            val pointsBefore = world.points.rows.size
            scene.standThenDriveOff()
            val carried = world.points.rows.drop(pointsBefore)
            assertEquals(first.endLatitude, carried.first().latitude)
            assertEquals(3, carried.size)
        }

    @Test
    fun `a restart while waiting with the truck gone ends the wait and starts nothing`() = runTest {
        ParkedScene(this, world).driveAndPark()

        world.truck.connected = false
        world.nowMs += 60 * 60_000L
        val (controller, service) = world.newProcess(backgroundScope)
        controller.onTrigger(TripTrigger.RECONCILE, "process start")
        runCurrent()

        assertEquals(emptyList<StartRequest>(), service.startRequests)
        assertNull(world.settings.current().parkedTruck)
        assertEquals(1, world.trips.rows.size)
    }
}
