package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.RESTART_GAP_LIMIT_MS
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.driveNorth
import com.shawnkowalchuk.milo.core.trip.fixAt
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val THREADS = 8
private const val TRIGGERS_PER_THREAD = 200
private const val FINISHED_MARK = "all triggers handed over"

/**
 * What the trip controller does when the process, or the service, goes away under it, and when
 * triggers arrive from several threads at once.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerRestartTest {
    private val world = FakeWorld()

    /** Records a manual trip of 1 km and then loses the process: the trip row stays open. */
    private fun TestScope.tripLeftOpenByAKilledProcess() {
        val (controller, service) = world.newProcess(backgroundScope)
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
        val fixes = driveNorth(fixCount = 11, metresPerFix = 100.0)
        fixes.forEach { controller.onFix(it.asFix()) }
        world.nowMs = fixes.last().wallClockMs
        runCurrent()
    }

    @Test
    fun `a trip left open by a killed process is picked up, through the service`() = runTest {
        tripLeftOpenByAKilledProcess()
        world.nowMs += 60_000

        val (controller, service) = world.newProcess(backgroundScope)
        controller.onTrigger(TripTrigger.RECONCILE, "process start")
        runCurrent()

        // The stored trip can carry on, so the service is asked for. Until it is up, nothing
        // is shown as recording.
        assertEquals(TripTrigger.RECONCILE, service.startRequests.single().trigger)
        assertNull(controller.activity.value.trip)

        service.comeUp()
        runCurrent()

        // The same trip, with the distance worked out again from its stored fixes. No second
        // row, and no second trip-start sound.
        assertEquals(1, world.trips.rows.size)
        assertEquals(1_000.0, controller.activity.value.trip?.distanceMetres ?: 0.0, 1.0)
        assertTrue(service.recording)
        assertEquals(0, service.tripStartsAnnounced)
    }

    @Test
    fun `Android restarting the service by itself picks the trip up too`() = runTest {
        tripLeftOpenByAKilledProcess()
        world.nowMs += 60_000

        val (controller, service) = world.newProcess(backgroundScope)
        // START_STICKY: the service is created again with no intent.
        controller.onServiceStarted(service, request = null)
        runCurrent()

        assertEquals(1, world.openTrips.size)
        assertTrue(service.recording)
        assertEquals(emptyList<StartRequest>(), service.startRequests)
    }

    @Test
    fun `a stored trip that is too old is closed at its last fix, and no service is started`() =
        runTest {
            tripLeftOpenByAKilledProcess()
            val lastFixAtMs = world.nowMs
            world.nowMs += RESTART_GAP_LIMIT_MS + 60_000

            val (controller, service) = world.newProcess(backgroundScope)
            controller.onTrigger(TripTrigger.RECONCILE, "process start")
            runCurrent()

            val trip = world.trips.rows.single()
            assertEquals(TripStatus.FINISHED, trip.status)
            assertEquals(lastFixAtMs, trip.endedAtMs)
            assertEquals(1_000.0, trip.distanceMetres, 1.0)
            assertEquals(emptyList<StartRequest>(), service.startRequests)
        }

    @Test
    fun `if the service cannot be started for a stored trip, the trip is left as it was`() =
        runTest {
            tripLeftOpenByAKilledProcess()
            world.nowMs += 60_000

            val (controller, service) = world.newProcess(backgroundScope)
            service.refuseWith = StartFailure(emptyList(), "not allowed from the background")
            controller.onTrigger(TripTrigger.RECONCILE, "process start")
            runCurrent()

            // Still open in storage, for the tap on the notification to pick up.
            assertEquals(1, world.openTrips.size)
            assertNull(controller.activity.value.trip)
            assertEquals(service.refuseWith, controller.activity.value.startFailure)

            service.refuseWith = null
            service.comesUpAtOnce = true
            controller.onTrigger(TripTrigger.MANUAL_START, "tap on the notification")
            runCurrent()

            // The stored trip carries on: no second row, and the failure is no longer shown.
            assertEquals(1, world.trips.rows.size)
            assertTrue(service.recording)
            assertNull(controller.activity.value.startFailure)
        }

    @Test
    fun `a service that stops while a trip is open is asked for again`() = runTest {
        val (controller, service) = world.newProcess(backgroundScope)
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
        runCurrent()

        // Android destroys the service without the controller having asked for it.
        service.comesUpAtOnce = false
        controller.onServiceStopped(service)
        runCurrent()

        assertEquals(TripTrigger.RECONCILE, service.startRequests.last().trigger)
        assertTrue(
            world.logged(EventCategory.SERVICE).any { it.startsWith("The trip service stopped") },
        )
    }

    @Test
    fun `an ordinary stop is not mistaken for a lost service`() = runTest {
        val (controller, service) = world.newProcess(backgroundScope)
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()
        val asked = service.startRequests.size

        controller.onServiceStopped(service)
        runCurrent()

        assertEquals(asked, service.startRequests.size)
    }

    @Test
    fun `a lost service that Android will not restart leaves no trip in memory`() = runTest {
        val (controller, service) = recordingManualTrip(world)
        drive(world, controller, fixCount = 11, metresPerFix = 100.0)
        val lastFixAtMs = world.nowMs

        // Android destroys the service in mid-trip and refuses to start it again (battery use
        // was set to Restricted). The process lives on.
        service.comesUpAtOnce = false
        service.refuseWith = StartFailure(emptyList(), "not allowed from the background")
        controller.onServiceStopped(service)
        runCurrent()

        // Nothing is recording, so the screens must not say a trip is in progress. The row
        // stays open in storage, for the next trigger to pick up or close.
        assertNull(controller.activity.value.trip)
        assertEquals(service.refuseWith, controller.activity.value.startFailure)
        assertEquals(1, world.openTrips.size)

        // The next morning the truck connects. Kept in memory, yesterday's trip would carry
        // on and join the two drives. Picked up from storage, it is closed at its last fix.
        world.nowMs += 14 * 60 * 60_000L
        service.refuseWith = null
        service.comesUpAtOnce = true
        world.truck.connected = true
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
        runCurrent()

        val (yesterday, today) = world.trips.rows
        assertEquals(TripStatus.FINISHED, yesterday.status)
        assertEquals(lastFixAtMs, yesterday.endedAtMs)
        assertEquals(TripStatus.OPEN, today.status)
        assertEquals(world.nowMs, today.startedAtMs)
    }

    // ---- Storage failing under the controller -----------------------------------------------------

    @Test
    fun `a trip that could not be stored leaves no service running and nothing on screen`() =
        runTest {
            val (controller, service) = process(world)
            service.comesUpAtOnce = true
            world.trips.failNextInsert = IOException("the disk is full")

            controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
            runCurrent()

            // Storage was read again at once: no trip is open, so the service that had come up
            // for the press is stopped, not left in the foreground with nothing to record.
            assertEquals(emptyList<Any>(), world.trips.rows)
            assertEquals(1, service.stops)
            assertNull(controller.activity.value.trip)
            assertEquals(
                listOf("The trip controller failed while handling Trigger"),
                world.logged(EventCategory.ERROR),
            )
        }

    @Test
    fun `a fix that could not be stored does not stop the trip being recorded`() = runTest {
        val (controller, service) = recordingManualTrip(world)
        drive(world, controller, fixCount = 5, metresPerFix = 100.0)
        world.points.failNextInsert = IOException("the disk is full")

        controller.onFix(fixAt(northMetres = 500.0, second = 25).asFix())
        runCurrent()

        // The trip was picked up from storage again straight away, so the fixes that follow
        // are stored, and the screen still shows it with the distance its stored fixes give.
        assertEquals(1, world.logged(EventCategory.ERROR).size)
        assertTrue(service.recording)
        assertEquals(400.0, controller.activity.value.trip?.distanceMetres ?: 0.0, 1.0)

        controller.onFix(fixAt(northMetres = 600.0, second = 30).asFix())
        runCurrent()
        assertEquals(6, world.points.rows.size)
        assertEquals(600.0, controller.activity.value.trip?.distanceMetres ?: 0.0, 1.0)
    }

    // ---- Many threads at once -----------------------------------------------------------------------

    @Test
    fun `triggers from many threads at once are handled one at a time`() {
        // Real threads and a real thread pool. The stand-in storage is not thread-safe, and its
        // "open a trip unless one is open" is not atomic: if the controller ever handled two
        // triggers at the same time, this would end with two open trips or a broken list.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val (controller, service) = world.newProcess(scope)
        service.comesUpAtOnce = true
        val triggers =
            listOf(
                TripTrigger.MANUAL_START,
                TripTrigger.MANUAL_END,
                TripTrigger.TRUCK_LINK_CONNECTED,
                TripTrigger.TRUCK_DISCONNECTED,
                TripTrigger.RECONCILE,
            )

        val threads =
            List(THREADS) { number ->
                thread {
                    repeat(TRIGGERS_PER_THREAD) { index ->
                        controller.onTrigger(triggers[(number + index) % triggers.size], "thread")
                    }
                }
            }
        threads.forEach { it.join() }

        // The inbox is worked through in order, so once this note is in the log, everything
        // handed over before it has been handled.
        val done = CountDownLatch(1)
        thread {
            controller.note(EventCategory.PROCESS, FINISHED_MARK)
            while (world.log.entries.none { it.message == FINISHED_MARK }) Thread.sleep(5)
            done.countDown()
        }
        assertTrue("the controller did not finish in time", done.await(30, TimeUnit.SECONDS))
        scope.cancel()

        assertTrue("more than one trip is open", world.openTrips.size <= 1)
        assertEquals(emptyList<String>(), world.logged(EventCategory.ERROR))
        // Every trip that was opened was also closed in order: ids are dense and each closed
        // row has an end time.
        val closed = world.trips.rows.filter { it.status != TripStatus.OPEN }
        assertTrue(closed.all { it.endedAtMs != null })
    }
}
