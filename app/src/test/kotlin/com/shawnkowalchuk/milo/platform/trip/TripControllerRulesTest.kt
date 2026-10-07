package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.FIX_INTERVAL_SECONDS
import com.shawnkowalchuk.milo.core.trip.START_CONFIRMATION_MS
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.driveNorth
import com.shawnkowalchuk.milo.core.trip.fixAt
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE_MS = 60_000L

/**
 * The trip controller and the rules that need the service's help: the companion start and the
 * hold-off (added to ADR-002), Android Auto, and the timers. The once-a-minute reading has a
 * file of its own, [TripControllerPollTest]. The rules themselves are tested in `core/trip`;
 * here they run through the controller, against the stand-ins of [FakeWorld].
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerRulesTest {
    private val world = FakeWorld()

    // ---- Two of the rules added to ADR-002 --------------------------------------------------------

    @Test
    fun `a companion start that nothing confirms is discarded as a false start`() = runTest {
        val (controller, service) = process(world)
        service.comesUpAtOnce = true

        controller.onTrigger(TripTrigger.TRUCK_APPEARED, "companion service")
        runCurrent()

        // Recording at once, with a timer for the confirmation. No trip-start sound yet: it
        // says "connected to the truck", and nothing has shown that.
        val startedAtMs = world.nowMs
        assertEquals(TripStartCause.TRUCK, world.openTrips.single().startedBy)
        assertEquals(startedAtMs + START_CONFIRMATION_MS, service.checkAtMs)
        assertTrue(service.recording)
        assertEquals(0, service.tripStartsAnnounced)

        // The phone is in another vehicle passing the yard: 400 m in the ten seconds before
        // the timer runs out. The truck's profile state still says "not connected".
        driveNorth(fixCount = 3, metresPerFix = 200.0).forEach { controller.onFix(it.asFix()) }
        world.nowMs += START_CONFIRMATION_MS
        controller.onCheckDue(startedAtMs + START_CONFIRMATION_MS)
        runCurrent()

        // Discarded although it is over the minimum distance: it was never a trip.
        val trip = world.trips.rows.single()
        assertEquals(TripStatus.DISCARDED, trip.status)
        assertEquals(400.0, trip.distanceMetres, 1.0)
        assertTrue(world.logged(EventCategory.TRIP).last().contains("discarded as a false start"))
        // The service stops; the trip never waited out a grace period, and never made a sound.
        assertEquals(1, service.stops)
        assertEquals(emptyList<String>(), world.logged(EventCategory.GRACE))
        assertEquals(0, service.tripStartsAnnounced)
    }

    @Test
    fun `a companion start confirmed by the profile state carries on as a truck trip`() = runTest {
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        val startedAtMs = world.nowMs
        controller.onTrigger(TripTrigger.TRUCK_APPEARED, "companion service")
        runCurrent()

        world.truck.connected = true
        world.nowMs += START_CONFIRMATION_MS
        controller.onCheckDue(world.nowMs)
        runCurrent()

        val trip = world.openTrips.single()
        assertTrue(trip.truckSeen)
        // The confirmation timer is gone. What is left is the parked limit of every trip.
        assertEquals(startedAtMs + PARKED_LIMIT_MS, service.checkAtMs)
        // Now the truck is known to be connected: the sound, once.
        assertEquals(1, service.tripStartsAnnounced)
    }

    @Test
    fun `End with the truck connected stores the hold-off with its time`() = runTest {
        world.truck.connected = true
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
        runCurrent()

        world.nowMs += 10 * MINUTE_MS
        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()

        assertEquals(world.nowMs, world.settings.current().autoStartHeldOffSinceMs)

        // A late duplicate of the connect event, ten seconds on, restarts nothing.
        world.nowMs += 10_000
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "companion service")
        runCurrent()
        assertEquals(emptyList<Any>(), world.openTrips)

        // The next morning's connect is a new link: the hold-off ends and a trip starts.
        world.nowMs += 15 * 60 * MINUTE_MS
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
        runCurrent()
        assertEquals(1, world.openTrips.size)
        assertNull(world.settings.current().autoStartHeldOffSinceMs)
    }

    // ---- Android Auto and the timers ----------------------------------------------------------------

    @Test
    fun `Android Auto holds the trip open when the truck disconnects`() = runTest {
        world.truck.connected = true
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
        controller.onAndroidAuto(connected = true, "CarConnection reports type 2")
        runCurrent()

        world.truck.connected = false
        controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "ACL disconnect")
        runCurrent()

        assertNull(world.openTrips.single().graceDeadlineMs)
        assertEquals(1, world.logged(EventCategory.ANDROID_AUTO).size)

        // Unplugged: now the grace period starts.
        controller.onAndroidAuto(connected = false, "CarConnection reports type 0")
        runCurrent()
        assertNotNull(world.openTrips.single().graceDeadlineMs)
    }

    @Test
    fun `a GPS fix well after a missed deadline stands in for the timer`() = runTest {
        world.truck.connected = true
        val (controller, _) = process(world).also { it.second.comesUpAtOnce = true }
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
        world.truck.connected = false
        controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "ACL disconnect")
        runCurrent()
        val deadlineMs = checkNotNull(world.openTrips.single().graceDeadlineMs)

        // The timer never fires (the phone slept). A fix arrives 20 seconds past the deadline.
        val late = (deadlineMs - world.nowMs) / 1000 + 4 * FIX_INTERVAL_SECONDS
        world.nowMs = deadlineMs + 20_000
        controller.onFix(driveNorth(1, 0.0, fromSecond = late.toInt()).single().asFix())
        runCurrent()

        assertEquals(emptyList<Any>(), world.openTrips)
    }

    @Test
    fun `a forgotten manual trip is closed by the first fix after its limit, not kept open`() =
        runTest {
            val (controller, service) = recordingManualTrip(world)
            drive(world, controller, fixCount = 11, metresPerFix = 100.0)
            val lastMovedAtMs = world.nowMs

            // The phone goes indoors: no fixes, and it sleeps through the 30-minute timer.
            // 45 minutes later it leaves, and the first fix is 500 m from the last one. Counted
            // as movement before the overdue limit was looked at, that fix would move the
            // limit on, and the forgotten trip would swallow the next drive.
            val later = 45 * 60
            world.nowMs = lastMovedAtMs + later * 1000L
            val firstFixOutside = fixAt(northMetres = 1_500.0, second = 50 + later)
            controller.onFix(firstFixOutside.asFix())
            runCurrent()

            val trip = world.trips.rows.single()
            assertEquals(TripStatus.FINISHED, trip.status)
            // Closed where it last moved, with nothing of the gap in it.
            assertEquals(lastMovedAtMs, trip.endedAtMs)
            assertEquals(1_000.0, trip.distanceMetres, 1.0)
            assertTrue(world.logged(EventCategory.TRIP).last().contains("ended by NO_MOVEMENT"))
            assertEquals(1, service.stops)
            // The fix that closed it belongs to no trip.
            assertEquals(11, world.points.rows.size)
        }
}
