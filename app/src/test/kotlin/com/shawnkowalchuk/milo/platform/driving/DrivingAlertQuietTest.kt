package com.shawnkowalchuk.milo.platform.driving

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.platform.trip.FakeWorld
import com.shawnkowalchuk.milo.platform.trip.TripController
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import com.shawnkowalchuk.milo.platform.trip.process
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The three limits on the driving alert, beside a real trip controller on stand-ins: no truck
 * paired, a trip that has just ended, and an alert that was posted a short while ago. The last
 * two are kept in storage, so each is also tried across a restart of MilO's process. The edges
 * of the two spans, to the second, are in `DrivingAlertLimitsTest`.
 */
// runCurrent() is how a test lets the alert's and the controller's coroutines run. The API is
// marked experimental by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class DrivingAlertQuietTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val world = workdayWorld()
    private val detection = FakeDetection()
    private val notification = FakeNotification()

    private fun TestScope.alertBeside(
        controller: TripController,
        on: FakeWorld = world,
    ): DrivingAlert =
        drivingAlert(controller, on, detection, notification, CrashFileStore(folder.root))

    private fun TestScope.report(alert: DrivingAlert, vararg reports: VehicleReport) {
        alert.onReports(reports.toList()) {}
        runCurrent()
    }

    private fun lastLine(of: FakeWorld = world) =
        of.log.entries.last { it.category == EventCategory.DRIVING }

    // ---- A truck is paired ------------------------------------------------------------------------

    @Test
    fun `with no truck paired a report of driving shows nothing`() = runTest {
        // The phone as it is today, before the truck has been paired.
        val unpaired = workdayWorld(truckPaired = false)
        val (controller, service) = process(unpaired)
        val alert = alertBeside(controller, on = unpaired)

        report(alert, ENTERED)

        assertEquals(0, notification.posted)
        assertEquals(emptyList<Any>(), service.startRequests)
        assertTrue(lastLine(unpaired).message.endsWith("No alert: no truck is paired"))
        assertTrue(lastLine(unpaired).detail.orEmpty().contains("A truck is paired: no."))

        // Paired on the pairing screen: the next report is the first that can alert.
        unpaired.settings.setTruck("AA:BB:CC:DD:EE:FF", name = null, associationId = null)
        report(alert, ENTERED)

        assertTrue(notification.showing)
    }

    // ---- No trip ended in the last five minutes ---------------------------------------------------

    @Test
    fun `a report just after End trip is about the drive that was recorded, and shows nothing`() =
        runTest {
            val (controller, service) = process(world)
            service.comesUpAtOnce = true
            val alert = alertBeside(controller)
            controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
            runCurrent()
            world.nowMs += 12 * MINUTE_MS
            controller.onTrigger(TripTrigger.MANUAL_END, "End button")
            runCurrent()
            assertEquals(emptyList<Any>(), world.openTrips)

            // Six seconds later the phone reports the drive it has only now noticed.
            world.nowMs += 6_000
            report(alert, ENTERED)

            assertEquals(0, notification.posted)
            assertTrue(lastLine().message.endsWith("No alert: a trip ended less than 5 min ago"))
            assertTrue(lastLine().detail.orEmpty().contains("The last trip ended: 0 min ago."))

            // Five minutes after the end, a report is a new drive.
            world.nowMs += 5 * MINUTE_MS - 6_000
            report(alert, ENTERED)

            assertEquals(1, notification.posted)
        }

    @Test
    fun `the end of the last trip is still known to a process that starts afterwards`() = runTest {
        val (first, firstService) = process(world)
        firstService.comesUpAtOnce = true
        first.onTrigger(TripTrigger.MANUAL_START, "Start button")
        runCurrent()
        first.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()

        // MilO's process is gone; a report of driving starts the next one a minute later.
        world.nowMs += MINUTE_MS
        val (second, _) = world.newProcess(backgroundScope)
        val alert = alertBeside(second)
        second.onTrigger(TripTrigger.RECONCILE, "process start")
        report(alert, ENTERED)

        assertEquals(0, notification.posted)
        assertTrue(lastLine().message.endsWith("No alert: a trip ended less than 5 min ago"))
    }

    // ---- No alert in the last thirty minutes ------------------------------------------------------

    @Test
    fun `leaving and entering again within half an hour does not sound the alert again`() =
        runTest {
            val (controller, _) = process(world)
            val alert = alertBeside(controller)
            report(alert, ENTERED)
            assertEquals(world.nowMs, world.settings.current().lastDrivingAlertAtMs)

            // A stop at a light: 19 seconds later the phone says "left", 3 seconds on "entered".
            world.nowMs += 19_000
            report(alert, LEFT)
            assertFalse(notification.showing)
            world.nowMs += 3_000
            report(alert, ENTERED)

            assertEquals(1, notification.posted)
            assertFalse(notification.showing)
            assertTrue(
                lastLine().message.endsWith("No new alert: the last one was less than 30 min ago"),
            )
            assertTrue(lastLine().detail.orEmpty().endsWith("The last alert: 0 min ago."))

            // Half an hour after the alert, to the second, the next report alerts again.
            world.nowMs += 30 * MINUTE_MS - 22_000
            report(alert, ENTERED)

            assertEquals(2, notification.posted)
            assertEquals(world.nowMs, world.settings.current().lastDrivingAlertAtMs)
        }

    @Test
    fun `an alert that is still showing stays where it is when the drive is reported again`() =
        runTest {
            val (controller, _) = process(world)
            val alert = alertBeside(controller)
            report(alert, ENTERED)

            world.nowMs += 10 * MINUTE_MS
            report(alert, ENTERED)

            // Neither posted again nor taken away: what it says is still true.
            assertEquals(1, notification.posted)
            assertTrue(notification.showing)
        }

    @Test
    fun `the limit on a second alert holds across a restart of MilO's process`() = runTest {
        val (first, _) = process(world)
        report(alertBeside(first), ENTERED)
        assertEquals(1, notification.posted)

        // The process is killed. 71 seconds after the alert a report starts the next one, which
        // knows of the alert only what the settings file holds.
        world.nowMs += 71_000
        val (second, _) = world.newProcess(backgroundScope)
        val alert = alertBeside(second)
        second.onTrigger(TripTrigger.RECONCILE, "process start")
        report(alert, LEFT, ENTERED)

        assertEquals(1, notification.posted)
        assertTrue(
            lastLine().message.endsWith("No new alert: the last one was less than 30 min ago"),
        )
    }

    @Test
    fun `within the half hour a truck that connects still takes the alert away`() = runTest {
        val (controller, _) = process(world)
        val alert = alertBeside(controller)
        report(alert, ENTERED)

        world.nowMs += MINUTE_MS
        world.truck.connected = true
        report(alert, ENTERED)

        assertFalse(notification.showing)
        assertTrue(lastLine().message.endsWith("No alert: the truck is connected"))
    }
}
