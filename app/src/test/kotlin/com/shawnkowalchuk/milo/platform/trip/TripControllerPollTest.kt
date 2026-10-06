package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE_MS = 60_000L

private const val PROVES_NOTHING = "No reading has shown the truck connected since its link"

/**
 * What a reading of "not connected" is worth while a trip is recording, where that is wired
 * into the controller (ADR-002, amendments 6 and 18): the once-a-minute reading, which is
 * believed only the second time in a row, and any reading taken before one has shown the truck
 * connected on its present link, which proves nothing.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerPollTest {
    private val world = FakeWorld()

    /**
     * A trip started by the truck's link connecting. What a reading of the truck says, then and
     * later, is up to the test (`world.truck`).
     */
    private fun TestScope.tripStartedByTheLink(): TripController {
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
        runCurrent()
        return controller
    }

    /** The service's minute timer fires. */
    private fun TestScope.minuteCheck(controller: TripController, truckConnected: Boolean) {
        world.truck.connected = truckConnected
        world.nowMs += MINUTE_MS
        controller.onTrigger(TripTrigger.POLL, "minute check")
        runCurrent()
    }

    private fun graceDeadlineMs(): Long? = world.openTrips.single().graceDeadlineMs

    // ---- Two in a row -------------------------------------------------------------------------

    @Test
    fun `one poll reading not connected changes nothing, the second starts the grace period`() =
        runTest {
            world.truck.connected = true
            val controller = tripStartedByTheLink()

            // The truck drives off without the disconnect ever being reported.
            minuteCheck(controller, truckConnected = false)

            assertNull(graceDeadlineMs())
            assertEquals(emptyList<String>(), world.logged(EventCategory.GRACE))

            minuteCheck(controller, truckConnected = false)

            // Default grace period: two minutes from the second reading.
            assertEquals(world.nowMs + 2 * MINUTE_MS, graceDeadlineMs())
        }

    @Test
    fun `a routine poll that confirms the truck is connected is not logged`() = runTest {
        world.truck.connected = true
        val controller = tripStartedByTheLink()
        val linesBefore = world.log.entries.size

        repeat(5) { minuteCheck(controller, truckConnected = true) }

        assertEquals(linesBefore, world.log.entries.size)
    }

    @Test
    fun `not connected, connected, not connected is not two in a row`() = runTest {
        // The profile state lagged for one reading, caught up, and lagged again.
        world.truck.connected = true
        val controller = tripStartedByTheLink()

        minuteCheck(controller, truckConnected = false)
        minuteCheck(controller, truckConnected = true)
        minuteCheck(controller, truckConnected = false)

        assertNull(graceDeadlineMs())
    }

    @Test
    fun `a reading of connected for any other trigger starts the count again`() = runTest {
        world.truck.connected = true
        val controller = tripStartedByTheLink()

        minuteCheck(controller, truckConnected = false)
        // Shawn opens MilO, and this time the truck is read as connected.
        world.truck.connected = true
        controller.onTrigger(TripTrigger.RECONCILE, "app opened")
        runCurrent()
        minuteCheck(controller, truckConnected = false)

        assertNull(graceDeadlineMs())
    }

    @Test
    fun `a new link starts the count again, and has to be seen before it can be doubted`() =
        runTest {
            world.truck.connected = true
            val controller = tripStartedByTheLink()

            minuteCheck(controller, truckConnected = false)
            // The link dropped and came back, and only the connect was reported.
            controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
            runCurrent()
            minuteCheck(controller, truckConnected = false)
            minuteCheck(controller, truckConnected = false)

            // No reading has seen the new link yet: its profiles may not be up.
            assertNull(graceDeadlineMs())

            // Once a reading has shown it, two readings in a row count again.
            minuteCheck(controller, truckConnected = true)
            minuteCheck(controller, truckConnected = false)
            assertNull(graceDeadlineMs())
            minuteCheck(controller, truckConnected = false)
            assertNotNull(graceDeadlineMs())
        }

    // ---- A link that no reading has seen ------------------------------------------------------

    @Test
    fun `a truck no reading can see is not ended by the minute check`() = runTest {
        // The link is up, but the truck is on neither the hands-free nor the audio profile,
        // which is all a reading looks at on Android 14. Counted, the first two polls would
        // end every trip four minutes after it started, and nothing would start the next one:
        // the link never dropped.
        world.truck.connected = false
        val controller = tripStartedByTheLink()

        repeat(5) { minuteCheck(controller, truckConnected = false) }

        assertNull(graceDeadlineMs())
        assertEquals(emptyList<String>(), world.logged(EventCategory.GRACE))
        // Each reading is in the log: it is how the phone shows that this truck is affected.
        val doubted = world.logged(EventCategory.TRIGGER).filter { it.contains(PROVES_NOTHING) }
        assertEquals(5, doubted.size)
        assertTrue(doubted.first().startsWith("minute check: the truck reads as not connected"))
    }

    @Test
    fun `a reading taken seconds after the link was made does not start the grace period`() =
        runTest {
            // The profiles connect a few seconds after the link. In between, Android binds the
            // companion service, or Shawn opens MilO, and the truck is read.
            world.truck.connected = false
            val controller = tripStartedByTheLink()

            world.nowMs += 1_000
            controller.onTrigger(TripTrigger.RECONCILE, "companion service created")
            runCurrent()

            assertNull(graceDeadlineMs())
            assertEquals(false, controller.activity.value.trip?.waitingForTruck)
            assertTrue(world.logged(EventCategory.TRIGGER).last().contains(PROVES_NOTHING))

            // The hands-free profile connects, and its broadcast prompts a reading.
            world.nowMs += 3_000
            world.truck.connected = true
            controller.onTrigger(TripTrigger.RECONCILE, "hands-free profile connected")
            runCurrent()

            // From here on a reading that finds the truck gone is believed at once.
            world.nowMs += 10 * MINUTE_MS
            world.truck.connected = false
            controller.onTrigger(TripTrigger.RECONCILE, "app opened")
            runCurrent()
            assertEquals(world.nowMs + 2 * MINUTE_MS, graceDeadlineMs())
        }

    @Test
    fun `a disconnect event still ends a trip no reading could see`() = runTest {
        world.truck.connected = false
        val controller = tripStartedByTheLink()
        minuteCheck(controller, truckConnected = false)

        // The link going down is an event, trusted as it stands.
        controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "ACL disconnect")
        runCurrent()

        assertEquals(world.nowMs + 2 * MINUTE_MS, graceDeadlineMs())
    }

    @Test
    fun `a truck that joins a manual trip over its link is not doubted before it is seen`() =
        runTest {
            val (controller, _) = recordingManualTrip(world)
            controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
            runCurrent()
            assertTrue(world.openTrips.single().truckSeen)

            repeat(2) { minuteCheck(controller, truckConnected = false) }

            assertNull(graceDeadlineMs())
        }
}
