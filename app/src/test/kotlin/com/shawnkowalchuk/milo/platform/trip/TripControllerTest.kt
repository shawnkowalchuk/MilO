package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.driveNorth
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.platform.system.PreflightProblem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trip controller with storage, the truck and the trip service replaced by stand-ins
 * ([FakeWorld]): what is stored, when the service is asked for, and what the event log says.
 * The rules themselves are tested in `core/trip`; these tests are about making them real.
 */
// runCurrent() is how a test lets the controller's worker run. The API is marked experimental
// by the coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class TripControllerTest {
    private val world = FakeWorld()

    // ---- The service comes first ----------------------------------------------------------------

    @Test
    fun `Start asks for the service first and opens the trip only once it is in the foreground`() =
        runTest {
            val (controller, service) = process(world)

            controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
            runCurrent()

            // Asked for straight from the trigger, with the trigger riding along.
            assertEquals(TripTrigger.MANUAL_START, service.startRequests.single().trigger)
            assertEquals(emptyList<Any>(), world.trips.rows)
            assertNull(controller.activity.value.trip)

            service.comeUp()
            runCurrent()

            val trip = world.openTrips.single()
            assertEquals(TripStartCause.MANUAL, trip.startedBy)
            assertEquals(world.nowMs, trip.startedAtMs)
            assertEquals(trip.id, controller.activity.value.trip?.tripId)
            assertTrue(service.recording)
            assertEquals(1, service.tripStartsAnnounced)
        }

    @Test
    fun `a start that Android refuses opens no trip and says why`() = runTest {
        val (controller, service) = process(world)
        service.refuseWith = StartFailure(listOf(PreflightProblem.BACKGROUND_LOCATION_MISSING))

        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
        runCurrent()

        assertEquals(emptyList<Any>(), world.trips.rows)
        assertEquals(service.refuseWith, controller.activity.value.startFailure)
        assertEquals(
            listOf(
                "Could not start recording for Start button: MANUAL_START: " +
                    "BACKGROUND_LOCATION_MISSING",
            ),
            world.logged(EventCategory.SERVICE),
        )
    }

    @Test
    fun `the failure is cleared when a trip does start`() = runTest {
        val (controller, service) = process(world)
        service.refuseWith = StartFailure(listOf(PreflightProblem.LOCATION_SWITCHED_OFF))
        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
        runCurrent()

        service.refuseWith = null
        service.comesUpAtOnce = true
        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
        runCurrent()

        assertNull(controller.activity.value.startFailure)
        assertNotNull(controller.activity.value.trip)
    }

    @Test
    fun `a service started for nothing is stopped again`() = runTest {
        // End was pressed with the truck connected, so automatic start is held off. A connect
        // event in a process that has not read its state yet starts the service all the same.
        world.truck.connected = true
        world.settings.setAutoStartHeldOffSinceMs(world.nowMs - 10_000)
        val (controller, service) = world.newProcess(backgroundScope)
        service.comesUpAtOnce = true

        controller.onTrigger(TripTrigger.TRUCK_LINK_CONNECTED, "ACL connect")
        runCurrent()

        assertEquals(emptyList<Any>(), world.trips.rows)
        assertEquals(1, service.stops)
    }

    @Test
    fun `a service that has just come up is not stopped before its own trigger is reached`() =
        runTest {
            val (controller, service) = world.newProcess(backgroundScope)

            // Ahead of the service's trigger in the inbox: the reconcile at process start, which
            // finds nothing to record.
            controller.onTrigger(TripTrigger.RECONCILE, "process start")
            val pressed = StartRequest(TripTrigger.MANUAL_START, "Start button", world.nowMs)
            controller.onServiceStarted(service, pressed)
            runCurrent()

            // Stopped after the reconcile, the service would have had to be started a second
            // time for the press that was already waiting.
            assertEquals(0, service.stops)
            assertEquals(emptyList<StartRequest>(), service.startRequests)
            assertEquals(1, world.openTrips.size)
        }

    // ---- Recording and finalising -----------------------------------------------------------------

    @Test
    fun `fixes are stored under the open trip and the distance so far is published`() = runTest {
        val (controller, _) = recordingManualTrip(world)

        drive(world, controller, fixCount = 11, metresPerFix = 100.0)

        val tripId = world.openTrips.single().id
        assertEquals(11, world.points.rows.size)
        assertTrue(world.points.rows.all { it.tripId == tripId })
        assertEquals(1_000.0, controller.activity.value.trip?.distanceMetres ?: 0.0, 1.0)
    }

    @Test
    fun `End closes the trip with its distance and positions, and stops the service`() = runTest {
        val (controller, service) = recordingManualTrip(world)
        drive(world, controller, fixCount = 11, metresPerFix = 100.0)

        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()

        val trip = world.trips.rows.single()
        val fixes = driveNorth(fixCount = 11, metresPerFix = 100.0)
        assertEquals(TripStatus.FINISHED, trip.status)
        assertEquals(1_000.0, trip.distanceMetres, 1.0)
        assertEquals(fixes.last().wallClockMs, trip.endedAtMs)
        assertEquals(fixes.first().latitude, trip.startLatitude)
        assertEquals(fixes.last().latitude, trip.endLatitude)
        assertNull(controller.activity.value.trip)
        assertEquals(1, service.stops)
        assertTrue(world.logged(EventCategory.TRIP).last().startsWith("Trip 1: finished, 1000 m"))
    }

    @Test
    fun `a trip under the minimum distance is kept as discarded`() = runTest {
        val (controller, _) = recordingManualTrip(world)
        drive(world, controller, fixCount = 3, metresPerFix = 100.0)

        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()

        val trip = world.trips.rows.single()
        assertEquals(TripStatus.DISCARDED, trip.status)
        assertEquals(200.0, trip.distanceMetres, 1.0)
        assertTrue(world.logged(EventCategory.TRIP).last().contains("under the minimum of 300 m"))
    }

    @Test
    fun `a fix that arrives after the trip closed is not stored`() = runTest {
        val (controller, _) = recordingManualTrip(world)
        drive(world, controller, fixCount = 3, metresPerFix = 100.0)
        controller.onTrigger(TripTrigger.MANUAL_END, "End button")
        runCurrent()

        controller.onFix(driveNorth(fixCount = 1, metresPerFix = 0.0).single().asFix())
        runCurrent()

        assertEquals(3, world.points.rows.size)
    }

    // ---- The event log ------------------------------------------------------------------------------

    @Test
    fun `every trigger is logged with its source and the state before and after`() = runTest {
        val (controller, service) = process(world)
        service.comesUpAtOnce = true

        controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
        runCurrent()

        // The reading the press took is part of the line: how it was reached, in brackets.
        val pressed =
            world.log.entries.single {
                it.message == "Start button: Start pressed, truck not connected (the test says so)"
            }
        assertEquals(EventCategory.TRIGGER, pressed.category)
        assertEquals(
            "Before: no trip; truck not connected; Android Auto not connected\n" +
                "After: trip open, truck not seen in it; truck not connected; " +
                "Android Auto not connected",
            pressed.detail,
        )
        // And the effect it had, on a line of its own.
        assertEquals(listOf("Trip 1 started by MANUAL"), world.logged(EventCategory.TRIP))
    }
}
