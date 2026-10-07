package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TRACK_START_WALL_CLOCK_MS
import com.shawnkowalchuk.milo.core.trip.TripRules
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.WAITING_LIMIT_MS
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading
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
 * The wait beside a parked truck when something under it gives way: the trip service is
 * destroyed or refused, the phone sleeps through the timer of the waiting limit, or a restart
 * of the process cannot read Bluetooth. In each of them a wait that is dropped quietly leaves a
 * truck that is still connected to drive off with nothing recording.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerWaitingServiceTest {
    private val world = FakeWorld()

    // ---- The trip service is lost or refused while MilO waits -------------------------------------

    @Test
    fun `a trip service destroyed in the middle of a wait is asked for again, and watches on`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.driveAndPark()
            val asked = scene.service.startRequests.size
            scene.service.watchingParked = false

            // HyperOS destroys the service; the process lives.
            scene.controller.onServiceStopped(scene.service)
            runCurrent()

            assertEquals(asked + 1, scene.service.startRequests.size)
            val lost =
                "The trip service stopped while MilO is watching the parked truck. " +
                    "Asking Android to start it again"
            assertTrue(world.logged(EventCategory.SERVICE).contains(lost))
            assertTrue(scene.service.watchingParked)
            assertNotNull(world.settings.current().parkedTruck)
            assertEquals(1, world.trips.rows.size)
        }

    @Test
    fun `if Android refuses it, the screens stop saying MilO waits, and the wait stays stored`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.driveAndPark()
            scene.service.refuseWith = StartFailure(emptyList(), "not allowed from the background")

            scene.controller.onServiceStopped(scene.service)
            runCurrent()

            // Nothing is watching, so nothing may go on saying "Truck connected and parked".
            assertNull(scene.controller.activity.value.parked)
            assertNotNull(scene.controller.activity.value.startFailure)
            val dropped =
                "The parked truck is not being watched. The next trigger picks the wait up"
            assertEquals(dropped, world.logged(EventCategory.SERVICE).last())
            assertNotNull(world.settings.current().parkedTruck)

            // MilO is opened: the stored wait is picked up, and now Android lets the service in.
            scene.service.refuseWith = null
            scene.controller.onTrigger(TripTrigger.RECONCILE, "app opened")
            runCurrent()

            assertTrue(scene.service.watchingParked)
            assertEquals(ParkedTruckWatch.WAITING_TO_MOVE, scene.controller.activity.value.parked)
            assertEquals(1, world.trips.rows.size)
        }

    // ---- The timer of the waiting limit, slept through ---------------------------------------------

    /** How many seconds into the track the service's timer is set for. */
    private fun ParkedScene.timerSecond(): Int =
        ((checkNotNull(service.checkAtMs) - TRACK_START_WALL_CLOCK_MS) / 1_000).toInt()

    @Test
    fun `a fix that arrives after the waiting limit stands in for the timer that was missed`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.driveAndPark()
            val limit = scene.timerSecond()

            // Within ten seconds of the limit the timer is given its chance to go first.
            scene.fix(northMetres = DRIVEN_METRES + 1, second = limit + 9)
            assertTrue(scene.service.watchingParked)
            scene.fix(northMetres = DRIVEN_METRES + 2, second = limit + 15)

            assertEquals(1, scene.service.stops)
            assertEquals(1, world.trips.rows.size)
            val unwatched = ParkedTruckWatch.NO_LONGER_WATCHED
            assertEquals(unwatched, scene.controller.activity.value.parked)
            assertTrue(world.logged(EventCategory.TRIP).last().contains("stopped watching it"))
        }

    @Test
    fun `a fix that shows the truck driving off is looked at before the limit it is late for`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.driveAndPark()
            val limit = scene.timerSecond()

            // The truck drives off as the limit passes, with the timer asleep. Judged the
            // other way round, the second fix would stop the watch and the drive be lost.
            scene.fix(northMetres = DRIVEN_METRES + 150, second = limit - 10)
            scene.fix(northMetres = DRIVEN_METRES + 450, second = limit + 20)

            assertEquals(2, world.trips.rows.size)
            assertEquals(TripStatus.OPEN, world.trips.rows.last().status)
            assertEquals(0, scene.service.stops)
            assertTrue(scene.service.recording)
        }

    // ---- A restart that cannot read Bluetooth ------------------------------------------------------

    @Test
    fun `a restart that cannot read the truck keeps the wait, and the next readings decide`() =
        runTest {
            val scene = ParkedScene(this, world)
            scene.driveAndPark()
            val stored = world.settings.current().parkedTruck

            // The process is killed. At its restart Bluetooth does not answer in time.
            world.truck.unreadable = true
            world.nowMs += 60 * 60_000L
            scene.newProcess()
            scene.controller.onServiceStarted(scene.service, request = null)
            runCurrent()

            assertTrue(scene.service.watchingParked)
            assertEquals(stored, world.settings.current().parkedTruck)
            assertEquals(ParkedTruckWatch.WAITING_TO_MOVE, scene.controller.activity.value.parked)
            val pickedUp = world.logged(EventCategory.TRIGGER).last { "picked up" in it }
            assertTrue(pickedUp, pickedUp.endsWith("and waits on until it can be read"))
            val ended = world.logged(EventCategory.TRIP).filter { "No longer waiting" in it }
            assertEquals(emptyList<String>(), ended)

            // Bluetooth answers again, and the truck has in fact gone: two readings end it.
            world.truck.unreadable = false
            world.truck.connected = false
            scene.minuteCheck()
            assertTrue(scene.service.watchingParked)
            scene.minuteCheck()

            assertEquals(1, scene.service.stops)
            assertNull(world.settings.current().parkedTruck)
            assertEquals(1, world.trips.rows.size)
        }

    @Test
    fun `after such a restart the truck driving off still starts the trip`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()

        world.truck.unreadable = true
        scene.newProcess()
        scene.controller.onServiceStarted(scene.service, request = null)
        runCurrent()
        world.truck.unreadable = false
        scene.standThenDriveOff()

        assertEquals(2, world.trips.rows.size)
        assertEquals(TripStatus.OPEN, world.trips.rows.last().status)
    }

    @Test
    fun `an unread truck keeps a stored wait only inside its limit and with no trip stored`() {
        val rules = TripRules(gracePeriodMs = 120_000, parkedLimitMs = PARKED_LIMIT_MS)
        val unread = TruckReading.unknown("Bluetooth did not answer")
        val since = 1_000L
        val inside = since + WAITING_LIMIT_MS - 1

        assertTrue(storedWaitStandsUnread(unread, tripStored = false, since, inside, rules))
        // Past the limit the rules start a trip for a connected truck, and an unread truck
        // must never start one.
        assertFalse(storedWaitStandsUnread(unread, tripStored = false, since, inside + 1, rules))
        // A stored trip is what counts, and it waits out a grace period as it always did.
        assertFalse(storedWaitStandsUnread(unread, tripStored = true, since, inside, rules))
        assertFalse(storedWaitStandsUnread(unread, tripStored = false, null, inside, rules))
        // A reading that was had is believed as it stands.
        val gone = TruckReading.notConnected("neither profile lists it")
        assertFalse(storedWaitStandsUnread(gone, tripStored = false, since, inside, rules))
    }

    @Test
    fun `a restart past the waiting limit that cannot read the truck starts nothing`() = runTest {
        ParkedScene(this, world).driveAndPark()

        world.truck.unreadable = true
        world.nowMs += WAITING_LIMIT_MS + 60_000L
        val (controller, service) = world.newProcess(backgroundScope)
        controller.onTrigger(TripTrigger.RECONCILE, "process start")
        runCurrent()

        assertEquals(emptyList<StartRequest>(), service.startRequests)
        assertNull(world.settings.current().parkedTruck)
        assertEquals(1, world.trips.rows.size)
    }
}
