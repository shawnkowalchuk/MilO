package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.START_CONFIRMATION_MS
import com.shawnkowalchuk.milo.core.trip.TripStatus
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

/**
 * The trip controller and its readings of the truck: what the triggers of the Bluetooth
 * receiver and the companion service lead to, and above all what happens when the phone cannot
 * say whether the truck is connected (no Bluetooth permission, or Bluetooth did not answer).
 * That reading is "unknown". It must never start a trip, and it must never end one that is
 * being recorded.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerReadingTest {
    private val world = FakeWorld()

    /** A trip the truck started, being recorded, with the truck connected. */
    private fun TestScope.recordingTruckTrip(): Pair<TripController, FakeService> {
        world.truck.connected = true
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "Bluetooth receiver: ACL connected")
        runCurrent()
        return controller to service
    }

    // ---- The triggers ---------------------------------------------------------------------------

    @Test
    fun `a reconcile that finds the truck connected starts a trip, through the service`() =
        runTest {
            // The phone booted, or MilO was updated, with the truck already connected.
            world.truck.connected = true
            val (controller, service) = process(world)
            service.comesUpAtOnce = true

            controller.onTrigger(TripTrigger.RECONCILE, "phone booted")
            runCurrent()

            assertEquals("phone booted", service.startRequests.single().source)
            assertEquals(1, world.openTrips.size)
            assertTrue(world.openTrips.single().truckSeen)
        }

    @Test
    fun `a reconcile that finds no truck starts nothing and says what it read`() = runTest {
        val (controller, service) = process(world)

        controller.onTrigger(TripTrigger.RECONCILE, "Bluetooth receiver: audio profile connected")
        runCurrent()

        assertEquals(emptyList<StartRequest>(), service.startRequests)
        // The first trigger of a process picks the stored state up; its line has the reading.
        assertEquals(
            listOf(
                "Bluetooth receiver: audio profile connected: picked up the stored state; " +
                    "the truck is not connected (the test says so)",
            ),
            world.logged(EventCategory.TRIGGER),
        )
    }

    @Test
    fun `a companion start is confirmed by the link connect broadcast`() = runTest {
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.TRUCK_APPEARED, "companion service")
        runCurrent()
        assertNotNull(service.checkAtMs)

        // Both come from the same Bluetooth event, so the broadcast follows within moments.
        world.nowMs += 300
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "Bluetooth receiver: ACL connected")
        runCurrent()

        assertTrue(world.openTrips.single().truckSeen)
        assertNull(service.checkAtMs)
        assertEquals(1, service.tripStartsAnnounced)
    }

    @Test
    fun `the same disconnect heard by two receivers starts one grace period`() = runTest {
        // During a trip the manifest receiver and the trip service's own both hear it.
        val (controller, _) = recordingTruckTrip()
        world.truck.connected = false

        controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "Bluetooth receiver")
        world.nowMs += 50
        controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "Bluetooth receiver (trip service)")
        runCurrent()

        assertEquals(1, world.logged(EventCategory.GRACE).size)
        // Both are in the log: which path delivered is what the phone has to show.
        assertEquals(2, world.logged(EventCategory.TRIGGER).count { it.contains("not connected") })
    }

    // ---- A reading of "unknown" -----------------------------------------------------------------

    @Test
    fun `a reconcile that cannot read the truck starts nothing and says why`() = runTest {
        // The truck is connected, but MilO may not ask. Unknown is never taken for connected.
        world.truck.connected = true
        world.truck.unreadable = true
        val (controller, service) = process(world)
        controller.onTrigger(TripTrigger.RECONCILE, "process start")
        runCurrent()

        controller.onTrigger(TripTrigger.RECONCILE, "app opened")
        runCurrent()

        assertEquals(emptyList<StartRequest>(), service.startRequests)
        assertEquals(emptyList<Any>(), world.trips.rows)
        assertEquals(
            "app opened: the truck's connection could not be read " +
                "(the test took Bluetooth away). Nothing changes",
            world.logged(EventCategory.TRIGGER).last(),
        )
    }

    @Test
    fun `a reconcile that cannot read the truck leaves a trip that is recording alone`() = runTest {
        // Opening MilO during a drive must not put the trip into its grace period.
        val (controller, _) = recordingTruckTrip()
        world.truck.unreadable = true

        controller.onTrigger(TripTrigger.RECONCILE, "app opened")
        runCurrent()

        assertNull(world.openTrips.single().graceDeadlineMs)
        assertEquals(emptyList<String>(), world.logged(EventCategory.GRACE))
    }

    @Test
    fun `polls that cannot read the truck are not counted as not connected`() = runTest {
        val (controller, _) = recordingTruckTrip()
        world.truck.unreadable = true

        repeat(5) {
            world.nowMs += MINUTE_MS
            controller.onTrigger(TripTrigger.POLL, "minute check")
        }
        runCurrent()

        // Five minutes of no answer: the trip is still recording, and each is in the log.
        assertNull(world.openTrips.single().graceDeadlineMs)
        val unread = world.logged(EventCategory.TRIGGER).count { it.contains("could not be read") }
        assertEquals(5, unread)

        // Bluetooth answers again, and the truck has gone: two readings in a row, as always.
        world.truck.unreadable = false
        world.truck.connected = false
        world.nowMs += MINUTE_MS
        controller.onTrigger(TripTrigger.POLL, "minute check")
        runCurrent()
        assertNull(world.openTrips.single().graceDeadlineMs)

        world.nowMs += MINUTE_MS
        controller.onTrigger(TripTrigger.POLL, "minute check")
        runCurrent()
        assertNotNull(world.openTrips.single().graceDeadlineMs)
    }

    @Test
    fun `a grace period still runs out when the truck cannot be read at its end`() = runTest {
        // The disconnect was reported and nothing has shown the truck since. Left open, the
        // trip would record for as long as Bluetooth gave no answer.
        val (controller, service) = recordingTruckTrip()
        world.truck.connected = false
        controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "Bluetooth receiver")
        runCurrent()
        val deadlineMs = checkNotNull(service.checkAtMs)

        world.truck.unreadable = true
        world.nowMs = deadlineMs
        controller.onCheckDue(deadlineMs)
        runCurrent()

        assertEquals(emptyList<Any>(), world.openTrips)
        assertEquals(1, service.stops)
        assertTrue(world.logged(EventCategory.TRIGGER).last().contains("could not be read"))
    }

    @Test
    fun `a companion start is a false start if the truck cannot be read at the deadline`() =
        runTest {
            // Nothing confirmed it, and "unknown" confirms nothing.
            val (controller, service) = process(world)
            service.comesUpAtOnce = true
            controller.onTrigger(TripTrigger.TRUCK_APPEARED, "companion service")
            runCurrent()

            world.truck.unreadable = true
            world.nowMs += START_CONFIRMATION_MS
            controller.onCheckDue(world.nowMs)
            runCurrent()

            assertEquals(TripStatus.DISCARDED, world.trips.rows.single().status)
            assertEquals(1, service.stops)
        }

    @Test
    fun `End with the truck unreadable goes by what was believed, and holds off`() = runTest {
        // Believed connected, so the next reading must not start a new trip at once.
        val (controller, _) = recordingTruckTrip()
        world.truck.unreadable = true
        world.nowMs += 10 * MINUTE_MS

        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()

        assertEquals(emptyList<Any>(), world.openTrips)
        assertEquals(world.nowMs, world.settings.current().autoStartHeldOffSinceMs)
    }

    @Test
    fun `a new process that cannot read the truck sends a stored trip into its grace period`() =
        runTest {
            // The Bluetooth permission was taken away during a drive: Android kills the process
            // and restarts the service. The trip must not stay open for ever on "unknown".
            val (_, _) = recordingTruckTrip()
            world.nowMs += MINUTE_MS
            world.truck.unreadable = true

            val (controller, service) = world.newProcess(backgroundScope)
            service.comesUpAtOnce = true
            controller.onTrigger(TripTrigger.RECONCILE, "process start")
            runCurrent()

            assertEquals(world.nowMs + 2 * MINUTE_MS, world.openTrips.single().graceDeadlineMs)
            assertTrue(
                world.logged(EventCategory.TRIGGER).any {
                    it.contains("picked up the stored state") && it.contains("could not be read")
                },
            )
        }

    // ---- Keeping a broadcast open ---------------------------------------------------------------

    @Test
    fun `a receiver is told once everything before it has been dealt with`() = runTest {
        val (controller, _) = process(world)
        var linesWhenCaughtUp: Int? = null

        controller.onTrigger(TripTrigger.RECONCILE, "phone booted")
        controller.whenCaughtUp { linesWhenCaughtUp = world.log.entries.size }
        // Not yet: the worker has not run.
        assertNull(linesWhenCaughtUp)
        runCurrent()

        // By then the reconcile had been handled and logged.
        assertEquals(1, linesWhenCaughtUp)
    }
}
