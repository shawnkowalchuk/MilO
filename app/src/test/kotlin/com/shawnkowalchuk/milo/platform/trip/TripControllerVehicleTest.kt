package com.shawnkowalchuk.milo.platform.trip

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val TRUCK = "AA:BB:CC:DD:EE:FF"
private const val VAN = "22:33:44:55:66:77"

/**
 * The vehicle each trip records (2026-10-08, when MilO learned several): the one a connect
 * named, or the one a reading found, and none for a trip no vehicle joined.
 */
// runCurrent() is how a test lets the controller's worker run. The API is marked experimental
// by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerVehicleTest {
    private val world = FakeWorld()

    @Test
    fun `a trip a connect starts records the vehicle the connect named`() = runTest {
        val (controller, service) = process(world)

        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "Bluetooth receiver", VAN)
        runCurrent()
        service.comeUp()
        runCurrent()

        assertEquals(VAN, world.openTrips.single().vehicleAddress)
        assertEquals(VAN, controller.activity.value.vehicle)
    }

    @Test
    fun `a trip started with Start records the vehicle a reading finds once it joins`() = runTest {
        val (controller, service) = process(world)
        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
        runCurrent()
        service.comeUp()
        runCurrent()
        assertNull(world.openTrips.single().vehicleAddress)

        world.truck.connected = true
        world.truck.vehicle = TRUCK
        controller.onTrigger(TripTrigger.RECONCILE, "hands-free profile connected")
        runCurrent()

        assertEquals(TRUCK, world.openTrips.single().vehicleAddress)
    }

    @Test
    fun `a trip no vehicle joins records none`() = runTest {
        val (controller, service) = process(world)

        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
        runCurrent()
        service.comeUp()
        runCurrent()
        controller.onTrigger(TripTrigger.RECONCILE, "app opened")
        runCurrent()

        assertNull(world.openTrips.single().vehicleAddress)
    }

    @Test
    fun `the disconnect of another vehicle is read, and the trip goes on`() = runTest {
        val (controller, service) = process(world)
        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "Bluetooth receiver", VAN)
        runCurrent()
        service.comeUp()
        runCurrent()

        world.truck.connected = true
        world.truck.vehicle = VAN
        controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "Bluetooth receiver", TRUCK)
        runCurrent()

        val trip = world.openTrips.single()
        assertNull("No grace period for another vehicle's disconnect", trip.graceStartedAtMs)
        assertEquals(VAN, trip.vehicleAddress)
    }

    @Test
    fun `the disconnect of the trip's own vehicle starts the grace period as it always did`() =
        runTest {
            val (controller, service) = process(world)
            controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "Bluetooth receiver", VAN)
            runCurrent()
            service.comeUp()
            runCurrent()

            controller.onTrigger(TripTrigger.TRUCK_DISCONNECTED, "Bluetooth receiver", VAN)
            runCurrent()

            assertEquals(world.nowMs, world.openTrips.single().graceStartedAtMs)
        }
}
