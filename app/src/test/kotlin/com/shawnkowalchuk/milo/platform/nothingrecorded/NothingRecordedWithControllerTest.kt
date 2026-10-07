package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.core.trip.TRACK_START_WALL_CLOCK_MS
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.DRIVEN_METRES
import com.shawnkowalchuk.milo.platform.trip.FakeWorld
import com.shawnkowalchuk.milo.platform.trip.ParkedScene
import com.shawnkowalchuk.milo.platform.trip.TripController
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import com.shawnkowalchuk.milo.platform.trip.process
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** How long the stand-in for Android takes to bring the trip service to the foreground. */
private const val SERVICE_START_MS = 1_000L

/**
 * The "nothing recorded" check beside a real trip controller, on the stand-ins of the
 * controller's own tests. `NothingRecordedCheckTest` sets by hand what the controller
 * publishes; here the controller decides, and the trip service comes up when the test says.
 *
 * It is here for one moment in a trip start. The controller opens a trip only once the trip
 * service is in the foreground: until then it has asked Android for the service, stored
 * nothing, and says it has caught up. A look that falls into that moment must not say "no trip
 * recorded today" a second before the trip begins.
 *
 * It is Tuesday 6 October 2026, 12:30, a work day out of the box and past noon, with no trip
 * stored and the truck connected: a notification is due, and a trip is about to start.
 *
 * The last test is here for the other place where the check meets the controller: a wait beside
 * the parked truck (ADR-002, amendment 28), which is not a trip.
 */
// runCurrent() and advanceTimeBy() are how a test lets the coroutines of the check and of the
// controller run. The API is marked experimental by the coroutines library; there is no stable
// equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class NothingRecordedWithControllerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val world =
        FakeWorld().apply {
            nowMs = LocalDateTime.parse("2026-10-06T12:30").toInstant(ZoneOffset.UTC).toEpochMilli()
            truck.connected = true
        }
    private var shown = 0
    private var withdrawn = 0

    /** The check as `CheckObjects` builds it, with a notification and an alarm that count. */
    private fun TestScope.checkBeside(controller: TripController): NothingRecordedCheck {
        val trips = TripRepository(world.trips)
        return NothingRecordedCheck(
            alarm =
                object : NothingRecordedAlarm {
                    override fun setFor(atMs: Long) = Unit

                    override fun cancel() = Unit
                },
            show = {
                shown++
                true
            },
            withdraw = { withdrawn++ },
            settings = world.settings,
            tripsStartedBetween = { fromMs, untilMs ->
                trips.observeTripsStartedBetween(fromMs, untilMs).first()
            },
            openTrip = trips::findOpenTrip,
            tripActivity = controller.activity,
            whenTripsCaughtUp = controller::whenCaughtUp,
            eventLog = EventLogRepository(world.log),
            crashFileStore = CrashFileStore(folder.root),
            clock = { world.nowMs },
            zone = { world.zone },
            scope = backgroundScope,
        ).also { runCurrent() }
    }

    /** The check's own lines for an asking: the alarm's line is not among them. */
    private fun askings(): List<String> =
        world.logged(EventCategory.TRIP).filter { it.startsWith("Nothing-recorded check (") }

    @Test
    fun `MilO opened beside the connected truck does not say no trip while one is starting`() =
        runTest {
            val (controller, service) = process(world)
            val check = checkBeside(controller)

            // What MainActivity.onStart does.
            controller.onTrigger(TripTrigger.RECONCILE, "app opened")
            check.look("app opened")
            runCurrent()

            // The controller has asked Android for the trip service, and has caught up with
            // everything: no trip is stored, and none is published.
            assertEquals(1, service.startRequests.size)
            assertTrue(world.trips.rows.isEmpty())
            assertNull(controller.activity.value.trip)
            assertEquals(0, shown)
            assertEquals(emptyList<String>(), askings())

            advanceTimeBy(SERVICE_START_MS)
            world.nowMs += SERVICE_START_MS
            service.comeUp()
            advanceTimeBy(TRIP_START_WAIT_MS)
            runCurrent()

            assertEquals(1, world.openTrips.size)
            assertEquals(0, shown)
            assertNull(world.settings.current().nothingRecorded.shownOn)
            assertEquals(
                listOf(
                    "Nothing-recorded check (app opened): no notification: 1 trip was started " +
                        "or is being recorded today, Tue 2026-10-06. A first look, a moment " +
                        "earlier, found no trip and waited before notifying: one was just " +
                        "being started.",
                ),
                askings(),
            )
        }

    @Test
    fun `a process the truck started does not say no trip before its trip is stored`() = runTest {
        val (controller, service) = world.newProcess(backgroundScope)
        val check = checkBeside(controller)

        // What MiloApplication does at process start, and then the Bluetooth receiver.
        controller.onTrigger(TripTrigger.RECONCILE, "process start")
        check.arm("process start")
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "Bluetooth")
        runCurrent()
        assertTrue(world.trips.rows.isEmpty())
        assertEquals(0, shown)

        advanceTimeBy(SERVICE_START_MS)
        service.comeUp()
        advanceTimeBy(TRIP_START_WAIT_MS)
        runCurrent()

        assertEquals(1, world.openTrips.size)
        assertEquals(0, shown)
        assertEquals(1, askings().size)
        assertTrue(askings().single().contains("no notification: 1 trip was started"))
    }

    @Test
    fun `a trip service that reports in as the wait ends has its trip stored before the look`() =
        runTest {
            val (controller, service) = process(world)
            val check = checkBeside(controller)
            controller.onTrigger(TripTrigger.RECONCILE, "app opened")
            check.look("app opened")
            runCurrent()

            // The wait is over and the second look is about to run. The service reports in at
            // this very moment: its trip is in the controller's inbox, and not stored yet.
            advanceTimeBy(TRIP_START_WAIT_MS)
            service.comeUp()
            assertTrue(world.trips.rows.isEmpty())
            runCurrent()

            // The second look asked the controller to catch up first, and so found the trip.
            assertEquals(1, world.openTrips.size)
            assertEquals(0, shown)
            assertTrue(askings().single().contains("no notification: 1 trip was started"))
        }

    @Test
    fun `a trip service that takes longer than the wait still gets its notification taken away`() =
        runTest {
            val (controller, service) = process(world)
            val check = checkBeside(controller)

            controller.onTrigger(TripTrigger.RECONCILE, "app opened")
            check.look("app opened")
            advanceTimeBy(TRIP_START_WAIT_MS)
            runCurrent()
            // The limit of a wait: no trip had come, so the check said so.
            assertEquals(1, shown)
            assertEquals(0, withdrawn)

            service.comeUp()
            runCurrent()

            assertEquals(1, world.openTrips.size)
            assertEquals(1, withdrawn)
        }

    @Test
    fun `with no truck and no trip the notification comes, after the wait and not before`() =
        runTest {
            world.truck.connected = false
            val (controller, service) = process(world)
            val check = checkBeside(controller)

            controller.onTrigger(TripTrigger.RECONCILE, "app opened")
            check.look("app opened")
            runCurrent()
            assertEquals(0, shown)

            advanceTimeBy(TRIP_START_WAIT_MS)
            assertEquals(0, shown)
            runCurrent()

            assertEquals(1, shown)
            assertEquals(0, service.startRequests.size)
            assertTrue(askings().single().contains("notify: no trip has been started today"))
        }

    @Test
    fun `a wait beside the parked truck is not a trip, and the trip that movement starts is`() =
        runTest {
            // Saturday noon: a drive, and the truck stays connected where it is parked.
            world.nowMs = TRACK_START_WALL_CLOCK_MS
            val scene = ParkedScene(this, world)
            scene.driveAndPark()
            val check = checkBeside(scene.controller)
            assertNotNull(scene.controller.activity.value.parked)

            // Monday, 12:30, a work day. The truck has not been driven since, and MilO still
            // waits beside it: the wait's three days are not over.
            val mondaySecond = 48 * 3_600 + 30 * 60
            world.nowMs = scene.timeOf(mondaySecond)
            check.look("daily alarm")
            advanceTimeBy(TRIP_START_WAIT_MS)
            runCurrent()

            // No trip was started today, so the notification comes, beside "Truck connected
            // and parked".
            assertEquals(1, shown)
            assertTrue(askings().single().contains("notify: no trip has been started today"))
            assertNotNull(scene.controller.activity.value.parked)

            // The truck drives off. The trip from the parked place takes the notification away.
            scene.fix(northMetres = DRIVEN_METRES + 150, second = mondaySecond + 60)
            scene.fix(northMetres = DRIVEN_METRES + 450, second = mondaySecond + 90)
            runCurrent()

            assertEquals(2, world.trips.rows.size)
            assertEquals(1, withdrawn)
        }
}
