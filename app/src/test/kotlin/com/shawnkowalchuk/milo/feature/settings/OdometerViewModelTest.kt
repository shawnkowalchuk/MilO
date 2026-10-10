package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.odometer.TripAtReading
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.FakeTripDao
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TRUCK = "AA:BB:CC:DD:EE:FF"

/**
 * The odometer tile's "Save reading" (2026-10-10): a reading typed while a trip is being
 * recorded keeps which trip that is and how far it has gone at the press, so that only what the
 * trip drives afterwards is added to it. Shawn typed his before driving off, with the trip MilO
 * had opened at the connect, and the drive was never added.
 */
// A ViewModel's coroutines run on the main dispatcher, which a plain JVM test has to supply
// (setMain), and runCurrent() lets them run. Both are marked experimental by the coroutines
// library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class OdometerViewModelTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val file = FakeSettingsFile()
    private val settings = SettingsStore(file)
    private val trips = FakeTripDao()
    private val activity = MutableStateFlow(TripActivity())
    private var now = 1_791_000_000_000L

    /** MilO opened the trip two minutes before, when the phone connected to the truck. */
    private val tripStartedAt = now - 120_000

    private fun TestScope.viewModel(): OdometerViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel =
            OdometerViewModel(
                settings = settings,
                trips = TripRepository(trips),
                activity = activity,
                eventLog = EventLogRepository(FakeEventLogDao()),
                clock = { now },
                zone = { zone },
            )
        // The tile is on screen: its state is worked out only while something watches it.
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
        return viewModel
    }

    private fun recording(metres: Double, truckSeen: Boolean = true, tripId: Long = 41) =
        TripActivity(
            trip =
                CurrentTrip(
                    tripId = tripId,
                    startedAtMs = tripStartedAt,
                    startedBy = TripStartCause.TRUCK,
                    distanceMetres = metres,
                    waitingForTruck = false,
                    truckSeen = truckSeen,
                ),
            truckConnected = truckSeen,
            vehicle = TRUCK.takeIf { truckSeen },
        )

    private fun stored(): List<OdometerReading> = runBlocking {
        settings.current().odometerReadings
    }

    private fun TestScope.shown(viewModel: OdometerViewModel): List<Long?> {
        runCurrent()
        return viewModel.state.value.orEmpty().map { it.figure?.value }
    }

    @After
    fun giveTheMainDispatcherBack() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a reading typed with a trip open keeps the trip and how far it has gone`() = runTest {
        val viewModel = viewModel()
        activity.value = recording(metres = 37.5)
        runCurrent()

        assertTrue(viewModel.onSaveReading("102720", DistanceUnit.KILOMETRES, TRUCK))
        runCurrent()

        assertEquals(
            listOf(
                OdometerReading(
                    now,
                    102_720,
                    DistanceUnit.KILOMETRES,
                    TRUCK,
                    TripAtReading(tripId = 41, metres = 37.5),
                ),
            ),
            stored(),
        )
    }

    @Test
    fun `it is the trip of the press - what is driven afterwards is not in the reading`() =
        runTest {
            val viewModel = viewModel()
            activity.value = recording(metres = 0.0)
            runCurrent()

            viewModel.onSaveReading("102720", DistanceUnit.KILOMETRES)
            // The truck drives on before the write has run.
            now += 120_000
            activity.value = recording(metres = 2_000.0)
            runCurrent()

            assertEquals(listOf(TripAtReading(41, 0.0)), stored().map { it.duringTrip })
            assertEquals(listOf(now - 120_000), stored().map { it.atMs })
            // And the tile counts the drive up from the reading: Shawn's 9 km.
            assertEquals(listOf(102_722L), shown(viewModel))
            now += 600_000
            activity.value = recording(metres = 9_200.0)
            assertEquals(listOf(102_729L), shown(viewModel))
        }

    @Test
    fun `when the trip has closed, the tile still shows the reading plus the drive`() = runTest {
        val viewModel = viewModel()
        activity.value = recording(metres = 0.0)
        runCurrent()
        viewModel.onSaveReading("102720", DistanceUnit.KILOMETRES)
        runCurrent()

        trips.rows +=
            Trip(
                id = 41,
                startedAtMs = tripStartedAt,
                endedAtMs = now + 1_200_000,
                status = TripStatus.FINISHED,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                distanceMetres = 9_200.0,
            )
        now += 3_600_000
        activity.value = TripActivity()
        // A fresh tile, as when Settings is opened later: it reads the stored trip.
        val later = viewModel()

        assertEquals(listOf(102_729L), shown(later))
    }

    @Test
    fun `a reading typed with no trip open keeps none`() = runTest {
        val viewModel = viewModel()

        assertTrue(viewModel.onSaveReading("102720", DistanceUnit.KILOMETRES, TRUCK))
        runCurrent()

        assertEquals(
            listOf(OdometerReading(now, 102_720, DistanceUnit.KILOMETRES, TRUCK)),
            stored(),
        )
    }

    @Test
    fun `a trip no paired vehicle has been seen in yet is kept too`() = runTest {
        // Started with the button; the truck may still join, and the trip then moves the
        // odometer from the reading on. If it never does, the trip moves no odometer at all.
        val viewModel = viewModel()
        activity.value = recording(metres = 1_500.0, truckSeen = false)
        runCurrent()

        viewModel.onSaveReading("102720", DistanceUnit.KILOMETRES)
        runCurrent()

        assertEquals(listOf(TripAtReading(41, 1_500.0)), stored().map { it.duringTrip })
    }

    @Test
    fun `what is not a reading stores nothing, with a trip open or not`() = runTest {
        val viewModel = viewModel()
        activity.value = recording(metres = 500.0)
        runCurrent()

        assertFalse(viewModel.onSaveReading("12a", DistanceUnit.KILOMETRES))
        runCurrent()

        assertTrue(stored().isEmpty())
    }
}
