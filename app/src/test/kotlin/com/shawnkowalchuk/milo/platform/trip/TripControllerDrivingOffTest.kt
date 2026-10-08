package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TRACK_START_WALL_CLOCK_MS
import com.shawnkowalchuk.milo.core.trip.fixAt
import com.shawnkowalchuk.milo.data.settings.TripSound
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** 6 m a second, 21.6 km/h: a truck pulling out. */
private const val PULLING_OUT = 6f

/**
 * The trip-start sound (2026-10-08) through the trip controller: asked for once per trip, at
 * the first fix that shows the truck driving, after the connect sound; not again after a
 * restart, and not for a trip that is not yet known to be one.
 */
// See TripControllerTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerDrivingOffTest {
    private val world = FakeWorld()

    /** One fix [northMetres] up the road with the phone's speed reading, the clock moved to it. */
    private fun TestScope.fix(
        controller: TripController,
        northMetres: Double,
        second: Int,
        speed: Float,
    ) {
        world.nowMs = TRACK_START_WALL_CLOCK_MS + second * 1000L
        val fix = fixAt(northMetres = northMetres, second = second, speedMetresPerSecond = speed)
        controller.onFix(fix.asFix())
        runCurrent()
    }

    /** The truck connects and a trip starts, its service up at once. */
    private fun TestScope.connected(): Pair<TripController, FakeService> {
        world.truck.connected = true
        val (controller, service) = process(world)
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
        runCurrent()
        return controller to service
    }

    @Test
    fun `the trip-start sound comes once, at the first fix at 15 km per hour`() = runTest {
        val (controller, service) = connected()
        assertEquals(listOf(TripSound.CONNECT), service.sounds)

        // The engine runs in the yard: the fixes read nought.
        fix(controller, northMetres = 0.0, second = 0, speed = 0f)
        fix(controller, northMetres = 1.0, second = 5, speed = 0f)
        assertEquals(0, service.drivingOffsAnnounced)

        fix(controller, northMetres = 30.0, second = 10, speed = PULLING_OUT)
        fix(controller, northMetres = 100.0, second = 15, speed = 15f)
        fix(controller, northMetres = 200.0, second = 20, speed = 20f)

        assertEquals(listOf(TripSound.CONNECT, TripSound.DRIVING_OFF), service.sounds)
    }

    @Test
    fun `a trip started with Start has both sounds too, the trip-start sound when it drives`() =
        runTest {
            val (controller, service) = recordingManualTrip(world)

            fix(controller, northMetres = 0.0, second = 0, speed = 0f)
            fix(controller, northMetres = 30.0, second = 5, speed = PULLING_OUT)

            assertEquals(listOf(TripSound.CONNECT, TripSound.DRIVING_OFF), service.sounds)
        }

    @Test
    fun `a trip picked up after a restart that had driven off has no second trip-start sound`() =
        runTest {
            val (before, _) = connected()
            fix(before, northMetres = 0.0, second = 0, speed = 0f)
            fix(before, northMetres = 30.0, second = 5, speed = PULLING_OUT)

            val (controller, service) = world.newProcess(backgroundScope)
            service.comesUpAtOnce = true
            controller.onTrigger(TripTrigger.RECONCILE, "process start")
            runCurrent()
            fix(controller, northMetres = 100.0, second = 10, speed = 15f)

            assertEquals(1, world.openTrips.size)
            assertEquals(emptyList<TripSound>(), service.sounds)
        }

    @Test
    fun `a trip picked up after a restart before it drove off has its sound when it does`() =
        runTest {
            val (before, _) = connected()
            fix(before, northMetres = 0.0, second = 0, speed = 0f)

            val (controller, service) = world.newProcess(backgroundScope)
            service.comesUpAtOnce = true
            controller.onTrigger(TripTrigger.RECONCILE, "process start")
            runCurrent()
            fix(controller, northMetres = 30.0, second = 5, speed = PULLING_OUT)

            assertEquals(listOf(TripSound.DRIVING_OFF), service.sounds)
        }

    @Test
    fun `a trip the companion callback alone opened waits for the truck to be confirmed`() =
        runTest {
            val (controller, service) = process(world)
            service.comesUpAtOnce = true
            controller.onTrigger(TripTrigger.TRUCK_APPEARED, "companion service")
            runCurrent()
            fix(controller, northMetres = 0.0, second = 0, speed = 0f)
            fix(controller, northMetres = 30.0, second = 5, speed = PULLING_OUT)
            assertEquals(emptyList<TripSound>(), service.sounds)

            world.truck.connected = true
            controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "Bluetooth receiver")
            runCurrent()

            // Both at the confirmation, the connect sound first.
            assertEquals(listOf(TripSound.CONNECT, TripSound.DRIVING_OFF), service.sounds)
        }
}
