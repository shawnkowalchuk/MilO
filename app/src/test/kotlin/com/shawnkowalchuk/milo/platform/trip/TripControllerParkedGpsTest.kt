package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.GPS_AFTER_VEHICLE_REPORT_MS
import com.shawnkowalchuk.milo.core.trip.PARKED_GPS_MS
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A wait beside the parked truck reads GPS for its first hour, then leaves the watching to the
 * phone's motion sensor (Shawn's choice of 2026-10-07: "GPS 1 hour, then sensor"). The times
 * are `parkedGpsUntilMs` in `core/trip`; this is the controller passing them to the service.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerParkedGpsTest {
    private val world = FakeWorld().apply { motionSensorWatching = true }

    /** Three hours after the wait began: GPS has long been off. */
    private val threeHoursOn = STOPPED_AT_SECOND + 600 + 3 * 3_600

    @Test
    fun `the service is told to read GPS for the first hour of the wait`() = runTest {
        val scene = ParkedScene(this, world)

        scene.driveAndPark()

        assertTrue(scene.service.watchingParked)
        assertEquals(world.nowMs + PARKED_GPS_MS, scene.service.gpsUntilMs)
    }

    @Test
    fun `without the phone's reports it reads GPS for the whole wait`() = runTest {
        world.motionSensorWatching = false
        val scene = ParkedScene(this, world)

        scene.driveAndPark()

        assertTrue(scene.service.watchingParked)
        assertNull(scene.service.gpsUntilMs)
    }

    @Test
    fun `GPS goes back on for the whole wait when the reports stop coming`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()

        // The Physical activity permission taken away; the next minute check passes it on.
        world.motionSensorWatching = false
        scene.minuteCheck()

        assertNull(scene.service.gpsUntilMs)
    }

    @Test
    fun `getting into a vehicle hours later reads GPS for ten minutes, and says so`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()
        world.nowMs = scene.timeOf(threeHoursOn)

        val noticedAt = world.nowMs - 40_000
        scene.controller.onVehicleEntered(noticedAt)
        runCurrent()

        assertEquals(noticedAt + GPS_AFTER_VEHICLE_REPORT_MS, scene.service.gpsUntilMs)
        assertEquals(
            "The phone reports getting into a vehicle 40 s ago: the parked truck's position is " +
                "read for the next 10 min, and a trip starts if it drives off",
            world.logged(EventCategory.LOCATION).single(),
        )
    }

    @Test
    fun `the drive the report announces starts its trip where the truck was parked`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()
        val pointsBefore = world.points.rows.size
        world.nowMs = scene.timeOf(threeHoursOn)
        scene.controller.onVehicleEntered(world.nowMs)
        runCurrent()

        // GPS is on again; its first fixes find the truck already on its way.
        scene.fix(northMetres = DRIVEN_METRES + 600, second = threeHoursOn + 20)
        scene.fix(northMetres = DRIVEN_METRES + 900, second = threeHoursOn + 50)

        val (first, second) = world.trips.rows
        assertEquals(TripStatus.OPEN, second.status)
        assertEquals(scene.timeOf(threeHoursOn + 20), second.startedAtMs)
        // The stretch from the parked place to the first fix is counted.
        val carried = world.points.rows.drop(pointsBefore)
        assertEquals(first.endLatitude, carried.first().latitude)
        val current = checkNotNull(scene.controller.activity.value.trip)
        assertEquals(900.0, current.distanceMetres, 1.0)
        assertTrue(scene.service.recording)
    }

    @Test
    fun `a report that arrives before the stored wait is picked up still turns GPS on`() = runTest {
        val scene = ParkedScene(this, world)
        scene.driveAndPark()
        world.nowMs = scene.timeOf(threeHoursOn)

        // HyperOS killed MilO; the report itself starts a new process. The process start
        // reads the stored wait first, which needs the service; the report comes next.
        scene.newProcess()
        scene.controller.onTrigger(TripTrigger.RECONCILE, "process start")
        runCurrent()
        scene.controller.onVehicleEntered(world.nowMs - 10_000)
        runCurrent()
        scene.service.comeUp()
        runCurrent()

        assertTrue(scene.service.watchingParked)
        assertEquals(
            world.nowMs - 10_000 + GPS_AFTER_VEHICLE_REPORT_MS,
            scene.service.gpsUntilMs,
        )
    }

    @Test
    fun `a report with no wait to watch only remembers`() = runTest {
        val scene = ParkedScene(this, world)
        scene.connect()

        scene.controller.onVehicleEntered(world.nowMs)
        runCurrent()

        // A trip is being recorded: GPS is on anyway, and nothing is written.
        assertTrue(scene.service.recording)
        assertNull(scene.service.gpsUntilMs)
        assertTrue(world.logged(EventCategory.LOCATION).isEmpty())
    }
}
