package com.shawnkowalchuk.milo.platform.driving

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.platform.trip.FakeWorld
import com.shawnkowalchuk.milo.platform.trip.TripController
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
 * When the driving alert asks the phone to report driving, and when it takes the request back:
 * the Settings switch, the Physical activity permission, and a phone that refuses.
 */
// runCurrent() is how a test lets the alert's and the controller's coroutines run. The API is
// marked experimental by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class DrivingAlertWatchTest {
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

    @Test
    fun `the first call in a process asks the phone, and a repeat asks nothing`() = runTest {
        val (controller, _) = process(world)
        val alert = alertBeside(controller)

        alert.arm("process start")
        alert.arm("MilO in front")
        alert.arm("MilO in front")
        runCurrent()

        assertEquals(listOf("watch"), detection.asked)
        assertEquals(listOf("Driving alert: watching for driving (process start)"), logged())
    }

    @Test
    fun `without the permission the phone is asked nothing, until it is granted`() = runTest {
        val (controller, _) = process(world)
        val alert = alertBeside(controller)
        detection.granted = false

        alert.arm("process start")
        runCurrent()

        assertEquals(emptyList<String>(), detection.asked)
        assertEquals(
            "Driving alert: not watching for driving (process start): the Physical activity " +
                "permission is not granted",
            logged().single(),
        )

        // Granted on the Setup screen; MilO is in front again.
        detection.granted = true
        alert.arm("MilO in front")
        runCurrent()

        assertEquals(listOf("watch"), detection.asked)
        assertEquals("Driving alert: watching for driving (MilO in front)", logged().last())
    }

    @Test
    fun `switching the alert off stops the reports and takes a showing alert away`() = runTest {
        val (controller, _) = process(world)
        val alert = alertBeside(controller)
        alert.arm("process start")
        report(alert, ENTERED)
        assertTrue(notification.showing)

        world.settings.setDrivingAlertEnabled(false)
        alert.arm("the Settings switch")
        runCurrent()

        assertEquals(listOf("watch", "stop"), detection.asked)
        assertFalse(notification.showing)
        assertEquals(
            "Driving alert: not watching for driving (the Settings switch): it is switched off " +
                "in Settings",
            logged().last(),
        )

        world.settings.setDrivingAlertEnabled(true)
        alert.arm("the Settings switch")
        runCurrent()

        assertEquals(listOf("watch", "stop", "watch"), detection.asked)
    }

    @Test
    fun `a report that arrives while the alert is switched off shows nothing`() = runTest {
        val (controller, _) = process(world)
        val alert = alertBeside(controller)
        world.settings.setDrivingAlertEnabled(false)

        report(alert, ENTERED)

        assertFalse(notification.showing)
        assertTrue(
            logged().last().endsWith("No alert: the driving alert is switched off in Settings"),
        )
    }

    @Test
    fun `a request the phone refuses is logged once and tried again at the next call`() = runTest {
        val (controller, _) = process(world)
        val alert = alertBeside(controller)
        detection.refuseWith = "ApiException: 17: API: ActivityRecognition.API is not available"

        alert.arm("process start")
        alert.arm("MilO in front")
        runCurrent()

        assertEquals(listOf("watch", "watch"), detection.asked)
        val error = world.log.entries.single { it.category == EventCategory.ERROR }
        assertTrue(error.message.contains("did not agree to report driving (process start)"))
        assertEquals(detection.refuseWith, error.detail)
        assertEquals(emptyList<String>(), logged())

        detection.refuseWith = null
        alert.arm("MilO in front")
        runCurrent()

        assertEquals("Driving alert: watching for driving (MilO in front)", logged().single())
    }

    @Test
    fun `the parked wait counts on the phone's reports only once the phone has agreed`() = runTest {
        val (controller, _) = process(world)
        val alert = alertBeside(controller)
        // Before the first request of the process has been answered: GPS stays on.
        assertFalse(alert.reportsComing())

        alert.arm("process start")
        runCurrent()
        assertTrue(alert.reportsComing())

        // The permission taken away in the phone's settings, before MilO has noticed.
        detection.granted = false
        assertFalse(alert.reportsComing())
        detection.granted = true

        world.settings.setDrivingAlertEnabled(false)
        alert.arm("the Settings switch")
        runCurrent()
        assertFalse(alert.reportsComing())
    }

    @Test
    fun `a phone that refuses to report driving leaves the parked wait on GPS`() = runTest {
        val (controller, _) = process(world)
        val alert = alertBeside(controller)
        detection.refuseWith = "ApiException: 17: API: ActivityRecognition.API is not available"

        alert.arm("process start")
        runCurrent()

        assertFalse(alert.reportsComing())
    }
}
