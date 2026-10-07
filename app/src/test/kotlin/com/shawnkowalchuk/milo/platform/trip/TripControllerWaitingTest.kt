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
 * The wait beside a truck that is connected and parked, through the trip controller (ADR-002,
 * amendment 28): the trip that movement starts, what ends the wait without one, the limit on
 * it, and a restart of the process in the middle of it.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerWaitingTest {
    private val world = FakeWorld()

    // ---- The truck moves again ----------------------------------------------------------------

    @Test
    fun `nothing is stored and nothing starts while the truck stands`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()
        val pointsBefore = world.points.rows.size
        val linesBefore = world.log.entries.size

        for (second in STOPPED_AT_SECOND + 630..STOPPED_AT_SECOND + 3_600 step 30) {
            scene.fix(northMetres = DRIVEN_METRES + second % 3, second = second)
        }

        assertEquals(1, world.trips.rows.size)
        assertEquals(pointsBefore, world.points.rows.size)
        // An hour of standing writes no line either.
        assertEquals(linesBefore, world.log.entries.size)
        assertTrue(scene.service.watchingParked)
    }

    @Test
    fun `one fix somewhere else starts no trip`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()

        scene.fix(northMetres = DRIVEN_METRES + 200, second = DRIVES_OFF_AT_SECOND)
        assertEquals(1, world.trips.rows.size)
        // The next fix is back where the truck stands: it was one bad fix.
        scene.fix(northMetres = DRIVEN_METRES + 1, second = DRIVES_OFF_AT_SECOND + 30)
        scene.fix(northMetres = DRIVEN_METRES + 2, second = DRIVES_OFF_AT_SECOND + 60)

        assertEquals(1, world.trips.rows.size)
        assertTrue(scene.service.watchingParked)
    }

    @Test
    fun `when the truck drives off a new trip starts where it was parked`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()
        val pointsBefore = world.points.rows.size

        scene.standThenDriveOff()

        val (first, second) = world.trips.rows
        assertEquals(TripStatus.OPEN, second.status)
        assertEquals(TripStartCause.TRUCK, second.startedBy)
        assertTrue(second.truckSeen)
        // It starts when the truck was first seen somewhere else.
        assertEquals(scene.timeOf(DRIVES_OFF_AT_SECOND), second.startedAtMs)
        // Its first points: the parked place, then the two fixes that showed it leave.
        val carried = world.points.rows.drop(pointsBefore)
        assertEquals(3, carried.size)
        assertTrue(carried.all { it.tripId == second.id })
        assertEquals(first.endLatitude, carried.first().latitude)
        assertEquals(first.endLongitude, carried.first().longitude)
        // So nothing of the drive is lost: 450 m from the parked place already.
        val current = checkNotNull(scene.controller.activity.value.trip)
        assertEquals(450.0, current.distanceMetres, 1.0)
        assertEquals(first.endLatitude, current.startLatitude)
        // The service records again, at the full rate, and the wait is no longer stored.
        assertTrue(scene.service.recording)
        assertFalse(scene.service.watchingParked)
        assertNull(world.settings.current().parkedTruck)
        assertNull(scene.controller.activity.value.parked)
    }

    @Test
    fun `no trip-start sound for a trip that movement started`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()
        assertEquals(1, scene.service.tripStartsAnnounced)

        scene.standThenDriveOff()

        assertEquals(2, world.trips.rows.size)
        assertEquals(1, scene.service.tripStartsAnnounced)
    }

    @Test
    fun `the second trip is a trip like any other, and ends where its own drive ends`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()
        scene.standThenDriveOff()

        // On up the road for another 1.5 km, then the truck disconnects for good.
        for (index in 1..15) {
            val second = DRIVES_OFF_AT_SECOND + 30 + index * 5
            scene.fix(northMetres = DRIVEN_METRES + 450 + index * 100, second = second)
        }
        world.truck.connected = false
        scene.controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "ACL disconnect")
        runCurrent()
        scene.timerRunsOut()

        val (first, second) = world.trips.rows
        assertEquals(TripStatus.FINISHED, second.status)
        assertEquals(1_950.0, second.distanceMetres, 1.0)
        // Where the first trip ended, the second begins: no gap between the two.
        assertEquals(first.endLatitude, second.startLatitude)
        assertEquals(first.endLongitude, second.startLongitude)
        assertEquals(1, scene.service.stops)
    }

    @Test
    fun `the log has a line for the end of the wait and one for the new trip`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()

        scene.standThenDriveOff()

        val lines = world.logged(EventCategory.TRIP).takeLast(2)
        assertEquals("No longer waiting for the truck to move: it moved", lines[0])
        val started =
            "Trip 2 started by TRUCK: it was connected and parked, and it moved. The trip " +
                "starts where it was parked (3 points carried over), with no trip-start sound"
        assertEquals(started, lines[1])
    }

    // ---- The wait ends without a trip -----------------------------------------------------------

    @Test
    fun `the truck disconnecting while MilO waits stops the service, and nothing is recorded`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.driveAndPark()

            world.truck.connected = false
            world.nowMs += 20 * 60_000L
            scene.controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "ACL disconnect")
            runCurrent()

            assertEquals(1, scene.service.stops)
            assertFalse(scene.service.watchingParked)
            assertEquals(1, world.trips.rows.size)
            assertNull(world.settings.current().parkedTruck)
            assertEquals(emptyList<String>(), world.logged(EventCategory.GRACE))
            val last = world.logged(EventCategory.TRIP).last()
            val expected =
                "No longer waiting for the truck to move: it is no longer connected. " +
                    "Nothing was recorded"
            assertEquals(expected, last)
        }

    @Test
    fun `a disconnect nobody reported ends the wait at the second reading in a row`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()
        scene.minuteCheck()

        world.truck.connected = false
        scene.minuteCheck()
        assertTrue(scene.service.watchingParked)
        scene.minuteCheck()

        assertEquals(1, scene.service.stops)
        assertNull(world.settings.current().parkedTruck)
    }

    @Test
    fun `a truck no reading can see is not taken for gone while MilO waits`() = runTest {
        // Its link is up, but it is on neither profile a reading looks at (amendment 18). A
        // wait ended by such readings would leave nothing to start the next trip.
        world.truck.connected = false
        val scene = ParkedScene(this, world)
        val process = process(world)
        scene.controller = process.first
        scene.service = process.second
        scene.service.comesUpAtOnce = true
        scene.controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
        runCurrent()
        drive(world, scene.controller, fixCount = 21, metresPerFix = 100.0)
        scene.timerRunsOut()
        assertTrue(scene.service.watchingParked)

        repeat(3) { scene.minuteCheck() }

        assertTrue(scene.service.watchingParked)
        assertEquals(0, scene.service.stops)
    }

    @Test
    fun `End pressed while waiting holds automatic start off, and movement then starts nothing`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.driveAndPark()

            world.nowMs += 60_000
            scene.controller.onTrigger(TripTrigger.MANUAL_END, "End button")
            runCurrent()

            assertEquals(world.nowMs, world.settings.current().autoStartHeldOffSinceMs)
            assertNull(world.settings.current().parkedTruck)
            assertEquals(1, scene.service.stops)
            // The service is down, so no fix arrives; one that did would start nothing.
            scene.standThenDriveOff()
            assertEquals(1, world.trips.rows.size)
        }

    @Test
    fun `Start pressed while waiting starts a trip at once, with the sound`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()

        world.nowMs += 60_000
        scene.controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
        runCurrent()

        val second = world.trips.rows.last()
        assertEquals(TripStatus.OPEN, second.status)
        assertEquals(TripStartCause.MANUAL, second.startedBy)
        assertEquals(world.nowMs, second.startedAtMs)
        assertEquals(2, scene.service.tripStartsAnnounced)
        assertNull(world.settings.current().parkedTruck)
    }
}
