package com.shawnkowalchuk.milo.platform.driving

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.platform.trip.ParkedScene
import com.shawnkowalchuk.milo.platform.trip.ParkedTruckWatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The driving alert beside a real trip controller that is waiting next to a parked truck
 * (ADR-002, amendment 28). While MilO watches the truck there is no alert: the truck reads as
 * connected, and its moving starts the trip. Once the wait has reached its limit and MilO has
 * stopped watching, a connected truck starts nothing when it drives off, and the alert is the
 * one thing left that says so. `DrivingAlertRulesTest` has the rule; this is the wiring: that
 * the alert reads "no longer watched" from what the controller publishes.
 */
// runCurrent() is how a test lets the alert's and the controller's coroutines run. The API is
// marked experimental by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class DrivingAlertParkedTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val world = workdayWorld()
    private val detection = FakeDetection()
    private val notification = FakeNotification()

    private fun lastLine() = world.log.entries.last { it.category == EventCategory.DRIVING }

    @Test
    fun `no alert while MilO watches the parked truck, and one once it has stopped watching`() =
        runTest {
            // The scene's drive is on the Saturday the stand-in clock starts on.
            val scene = ParkedScene(this, world)
            scene.driveAndPark()
            val controller = scene.controller
            val crashFiles = CrashFileStore(folder.root)
            val alert = drivingAlert(controller, world, detection, notification, crashFiles)
            assertEquals(ParkedTruckWatch.WAITING_TO_MOVE, controller.activity.value.parked)

            alert.onReports(listOf(ENTERED)) {}
            runCurrent()

            assertEquals(0, notification.posted)
            assertTrue(lastLine().message.endsWith("No alert: the truck is connected"))

            // Three days on, a Tuesday at noon, the wait reaches its limit: the truck is
            // still connected, and nothing watches it any more.
            scene.timerRunsOut()
            assertEquals(ParkedTruckWatch.NO_LONGER_WATCHED, controller.activity.value.parked)

            alert.onReports(listOf(ENTERED)) {}
            runCurrent()

            assertEquals(1, notification.posted)
            assertTrue(notification.showing)
            val shown =
                "Alert shown: no trip is being recorded, and the truck is connected but has " +
                    "stood so long that MilO stopped watching it"
            assertTrue(lastLine().message, lastLine().message.endsWith(shown))
        }
}
