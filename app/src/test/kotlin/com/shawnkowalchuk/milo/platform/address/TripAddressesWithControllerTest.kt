package com.shawnkowalchuk.milo.platform.address

import com.shawnkowalchuk.milo.core.trip.RESTART_GAP_LIMIT_MS
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.FakeWorld
import com.shawnkowalchuk.milo.platform.trip.TripController
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import com.shawnkowalchuk.milo.platform.trip.drive
import com.shawnkowalchuk.milo.platform.trip.process
import com.shawnkowalchuk.milo.platform.trip.recordingManualTrip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private val YARD = PlaceParts(houseNumber = "12", street = "Shop Rd", town = "Edmonton")
private const val YARD_LINE = "12 Shop Rd, Edmonton"

/**
 * The address lookup beside a real trip controller, on the stand-ins of the controller's own
 * tests: the passes that follow from what the controller does to a trip. [TripAddressesTest]
 * covers the lookup itself, with the controller's state set by hand.
 */
// See TripAddressesTest for why runCurrent() needs the opt-in.
@OptIn(ExperimentalCoroutinesApi::class)
class TripAddressesWithControllerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val world = FakeWorld()
    private var online = true
    private var questions = 0

    /** The lookup as `AppContainer` builds it, with a geocoder that knows one address. */
    private fun TestScope.addressesBeside(controller: TripController): TripAddresses =
        TripAddresses(
            trips = TripRepository(world.trips),
            eventLog = EventLogRepository(world.log),
            crashFileStore = CrashFileStore(folder.root),
            lookup = { _, _ ->
                questions++
                LookupAnswer.Places(listOf(YARD))
            },
            isOnline = { online },
            tripActivity = controller.activity,
            clock = { world.nowMs },
            scope = backgroundScope,
        ).also { runCurrent() }

    @Test
    fun `a trip the restart rules close is looked up by the pass asked for at process start`() =
        runTest {
            // A process that recorded 1 km and was killed. Its trip row stays open, and the
            // lookup of the next process never sees that trip in progress.
            val (killed, _) = recordingManualTrip(world)
            drive(world, killed, fixCount = 11, metresPerFix = 100.0)
            world.nowMs += RESTART_GAP_LIMIT_MS + 60_000
            // Without this the test could not tell the order apart: a pass asked for beside
            // the reconcile, not after it, runs while the truck is being read and finds the
            // trip still open.
            world.truck.takesAMoment = true

            val (controller, _) = world.newProcess(backgroundScope)
            val addresses = addressesBeside(controller)
            // What MiloApplication does at process start.
            controller.onTrigger(TripTrigger.RECONCILE, "process start")
            controller.whenCaughtUp { addresses.catchUp("process start") }
            runCurrent()

            val trip = world.trips.rows.single()
            assertEquals(TripStatus.FINISHED, trip.status)
            assertEquals(YARD_LINE, trip.startAddress)
            assertEquals(YARD_LINE, trip.endAddress)
            assertEquals(
                listOf("Trip 1: start address found; end address found."),
                world.logged(EventCategory.ADDRESS),
            )
        }

    @Test
    fun `a trip that starts and ends with no network is said to be waiting, once as each`() =
        runTest {
            online = false
            val (controller, service) = process(world)
            service.comesUpAtOnce = true
            val addresses = addressesBeside(controller)

            controller.onTrigger(TripTrigger.MANUAL_START, "Start button")
            runCurrent()
            drive(world, controller, fixCount = 11, metresPerFix = 100.0)
            controller.onTrigger(TripTrigger.MANUAL_END, "End button")
            runCurrent()
            // The Trips screen comes to the front: the same trip is still waiting, so no line.
            addresses.catchUp("the Trips screen")
            runCurrent()

            val trip = world.trips.rows.single()
            assertEquals(TripStatus.FINISHED, trip.status)
            assertEquals(
                listOf(
                    "Address lookup put off (trip 1 has a start): no network connection. " +
                        "Waiting: 0 finished trips and the start of trip 1 (in progress).",
                    "Address lookup put off (trip 1 is over): no network connection. " +
                        "Waiting: 1 finished trip.",
                ),
                world.logged(EventCategory.ADDRESS),
            )
            assertEquals("Nothing is asked without a network", 0, questions)
            assertEquals(0, trip.addressAttempts)
            assertNull(trip.addressLastAttemptAtMs)

            // Back online, the next pass fills both ends in.
            online = true
            addresses.catchUp("the Trips screen")
            runCurrent()
            assertEquals(YARD_LINE, world.trips.rows.single().startAddress)
            assertEquals(YARD_LINE, world.trips.rows.single().endAddress)
            assertTrue(world.logged(EventCategory.ADDRESS).last().startsWith("Trip 1: start"))
        }
}
