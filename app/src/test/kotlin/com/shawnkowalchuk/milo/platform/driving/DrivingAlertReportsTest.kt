package com.shawnkowalchuk.milo.platform.driving

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.platform.trip.FakeWorld
import com.shawnkowalchuk.milo.platform.trip.TripController
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import com.shawnkowalchuk.milo.platform.trip.process
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The driving alert as a whole, beside a real trip controller on stand-ins ([workdayWorld]):
 * what a report leads to, and above all that it leads to a notification and never to a trip.
 * What is asked of the phone is in `DrivingAlertWatchTest`, the limits on the alert in
 * `DrivingAlertQuietTest`, and what a failure does in `DrivingAlertFailureTest`.
 */
// runCurrent() is how a test lets the alert's and the controller's coroutines run. The API is
// marked experimental by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class DrivingAlertReportsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val world = workdayWorld()
    private val detection = FakeDetection()
    private val notification = FakeNotification()
    private var finished = 0

    private fun TestScope.alertBeside(
        controller: TripController,
        on: FakeWorld = world,
    ): DrivingAlert =
        drivingAlert(controller, on, detection, notification, CrashFileStore(folder.root))

    private fun TestScope.report(alert: DrivingAlert, vararg reports: VehicleReport) {
        alert.onReports(reports.toList()) { finished++ }
        runCurrent()
    }

    private fun logged(category: EventCategory = EventCategory.DRIVING): List<String> =
        world.logged(category)

    // ---- A report never starts a trip -------------------------------------------------------------

    @Test
    fun `a report of driving shows the alert and starts nothing`() = runTest {
        val (controller, service) = process(world)
        val alert = alertBeside(controller)

        report(alert, ENTERED)

        assertTrue(notification.showing)
        // The whole point: the trip service was not asked for, and no trip was opened.
        assertEquals(emptyList<Any>(), service.startRequests)
        assertEquals(emptyList<Any>(), world.trips.rows)
        assertNull(controller.activity.value.trip)
        assertEquals(1, finished)
        assertEquals(
            listOf(
                "Driving detection: entered a vehicle 4 s ago. Alert shown: no trip is being " +
                    "recorded and the truck is not connected",
            ),
            logged(),
        )
    }

    @Test
    fun `no report, however often it comes, ever asks for the trip service`() = runTest {
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        val alert = alertBeside(controller)

        repeat(3) {
            report(alert, ENTERED)
            report(alert, LEFT)
        }
        report(alert, LEFT, ENTERED)
        alert.arm("the test")
        runCurrent()

        assertEquals(emptyList<Any>(), service.startRequests)
        assertEquals(emptyList<Any>(), world.trips.rows)
        assertFalse(service.recording)
    }

    // ---- What a report leads to -------------------------------------------------------------------

    @Test
    fun `leaving the vehicle takes the alert away again`() = runTest {
        val (controller, _) = process(world)
        val alert = alertBeside(controller)
        report(alert, ENTERED)

        report(alert, LEFT)

        assertFalse(notification.showing)
        assertEquals(
            "Driving detection: left a vehicle 4 s ago. The alert is withdrawn if it was " +
                "showing: the drive is over",
            logged().last(),
        )
    }

    @Test
    fun `with the truck connected there is no alert, and the log says how that was read`() =
        runTest {
            val (controller, _) = process(world)
            val alert = alertBeside(controller)
            world.truck.connected = true

            report(alert, ENTERED)

            assertFalse(notification.showing)
            val line = world.log.entries.last { it.category == EventCategory.DRIVING }
            assertTrue(line.message.endsWith("No alert: the truck is connected"))
            assertTrue(line.detail.orEmpty().contains("The truck: connected (the test says so)."))
        }

    @Test
    fun `while a trip is being recorded there is no alert`() = runTest {
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        val alert = alertBeside(controller)
        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
        runCurrent()

        report(alert, ENTERED)

        assertFalse(notification.showing)
        assertEquals(0, notification.posted)
        assertTrue(logged().last().endsWith("No alert: a trip is being recorded"))
    }

    @Test
    fun `a trip that starts takes a showing alert away, however the trip started`() = runTest {
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        val alert = alertBeside(controller)
        report(alert, ENTERED)
        assertTrue(notification.showing)

        // The truck connects after all, a minute into the drive.
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "the test")
        runCurrent()

        assertFalse(notification.showing)
    }

    @Test
    fun `outside the work hours there is no alert`() = runTest {
        val (controller, _) = process(world)
        val alert = alertBeside(controller)
        // Back to the Saturday the world's clock starts on.
        world.nowMs -= 3 * DAY_MS

        report(alert, ENTERED)

        assertFalse(notification.showing)
        val line = world.log.entries.last { it.category == EventCategory.DRIVING }
        assertTrue(line.message.endsWith("No alert: it is outside the work hours"))
        assertTrue(line.detail.orEmpty().contains("Sat is not a tracked day (UTC)"))
    }

    @Test
    fun `an alert nobody can see is said to have gone unseen`() = runTest {
        val (controller, _) = process(world)
        val alert = alertBeside(controller)
        notification.visible = false

        report(alert, ENTERED)

        assertTrue(logged().last().endsWith("so nobody saw it"))
    }

    @Test
    fun `a trip the last process left open is not alerted about while it is picked up`() = runTest {
        // The last process died in the middle of a trip started by hand. A report of
        // driving starts the next one, whose trip service is asked for and not up yet.
        val (first, firstService) = process(world)
        firstService.comesUpAtOnce = true
        first.onTrigger(TripTrigger.MANUAL_START, "Start button")
        runCurrent()

        val (second, secondService) = world.newProcess(backgroundScope)
        val alert = alertBeside(second)
        second.onTrigger(TripTrigger.RECONCILE, "process start")
        report(alert, ENTERED)

        // Nothing is published yet: the trip carries on only once the service is up.
        assertNull(second.activity.value.trip)
        assertEquals(1, secondService.startRequests.size)
        assertEquals(0, notification.posted)
        assertEquals(1, finished)
        assertTrue(logged().last().endsWith("No alert: a trip is being recorded"))
    }

    @Test
    fun `a trip left open long ago is closed first, and then no longer silences the alert`() =
        runTest {
            val (first, firstService) = process(world)
            firstService.comesUpAtOnce = true
            first.onTrigger(TripTrigger.MANUAL_START, "Start button")
            runCurrent()
            // Nobody watched for longer than the restart rules allow, still inside the hours.
            world.nowMs += 45 * MINUTE_MS
            // The write that closes the stale trip takes its time, as a database's does.
            val closing = CompletableDeferred<Unit>()
            world.trips.holdCloseUntil = closing

            val (second, _) = world.newProcess(backgroundScope)
            val alert = alertBeside(second)
            second.onTrigger(TripTrigger.RECONCILE, "process start")
            report(alert, ENTERED)

            // The report waits its turn behind the controller's first look at storage. Judged
            // now, it would find the stale trip open and say "a trip is being recorded".
            assertEquals(1, world.openTrips.size)
            assertEquals(0, finished)
            assertEquals(emptyList<String>(), logged())

            closing.complete(Unit)
            runCurrent()

            assertEquals(emptyList<Any>(), world.openTrips)
            assertEquals(1, finished)
            assertTrue(notification.showing)
        }

    @Test
    fun `a trip that begins while the alert is being decided takes it straight back`() = runTest {
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        // The alert has asked whether a trip is open and heard "no". While it reads on, Shawn
        // presses Start: the trip is published, and the withdrawal that follows a trip start
        // runs before the alert has been posted at all.
        val startsMeanwhile: suspend () -> Long? = {
            controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
            controller.activity.first { it.trip != null }
            yield()
            null
        }
        val alert =
            drivingAlert(
                controller,
                world,
                detection,
                notification,
                CrashFileStore(folder.root),
                lastTripEndedAtMs = startsMeanwhile,
            )

        report(alert, ENTERED)

        // Posted, because the decision was made before the trip, and taken away at once.
        assertEquals(1, notification.posted)
        assertFalse(notification.showing)
        assertTrue(service.recording)
    }

    @Test
    fun `settings that cannot be read do not silence the alert`() = runTest {
        val broken = workdayWorld(UnreadableSettingsFile, truckPaired = false)
        val (controller, _) = process(broken)
        val alert = alertBeside(controller, on = broken)

        report(alert, ENTERED)

        assertTrue(notification.showing)
        val line = broken.log.entries.last { it.category == EventCategory.DRIVING }
        assertTrue(line.detail.orEmpty().startsWith("The settings cannot be read"))
        // Nor can the time of the alert be stored, and the line says what follows from that.
        assertTrue(line.detail.orEmpty().contains("The time of this alert could not be stored"))
    }
}
