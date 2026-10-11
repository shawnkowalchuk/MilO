package com.shawnkowalchuk.milo.feature.home

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.TruckLinkLook
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What Home draws from moment to moment when the truck arrives: which layout, which look of the
 * truck's tile, which words, and when each changes. The clock is the test's own, so the 2.6 and
 * the 1.2 seconds are counted exactly.
 */
// The test's clock, and letting its coroutines run, are marked experimental by the coroutines
// library and have no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class HomeShownTest {
    private fun ui(state: TruckState, trip: CurrentTrip? = null) = HomeUi(
        date = LocalDate.of(2026, 10, 6),
        activity = TripActivity(trip, truckConnected = state.connected),
        setupNeedsAttention = false,
        figures = null,
        truck = TruckTileState(state, truckName = "F-150"),
        reportWaiting = null,
        nowMs = 0,
        unit = DistanceUnit.KILOMETRES,
    )

    private fun trip(by: TripStartCause, metres: Double = 0.0) = CurrentTrip(
        tripId = 7,
        startedAtMs = 0,
        startedBy = by,
        distanceMetres = metres,
        waitingForTruck = false,
    )

    private val away = ui(TruckState.NOT_CONNECTED)
    private val connected = ui(TruckState.CONNECTED_NOT_RECORDING)
    private val recording = ui(TruckState.CONNECTED_RECORDING, trip(TripStartCause.TRUCK))
    private val byHand = ui(TruckState.RECORDING_WITHOUT_TRUCK, trip(TripStartCause.MANUAL))

    private val seen = MutableStateFlow(away)
    private val onScreen = MutableStateFlow(true)
    private val presses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Everything Home was told to draw, in order, from the moment the screen is opened. */
    private fun TestScope.open(): List<HomeShown> {
        val drawn = mutableListOf<HomeShown>()
        backgroundScope.launch {
            homeShown(seen, onScreen, presses) { currentTime }.collect { drawn += it }
        }
        runCurrent()
        return drawn
    }

    /** Lets the test's clock run on, and everything that was waiting for that moment. */
    private fun TestScope.after(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    private fun TestScope.sees(ui: HomeUi) {
        seen.value = ui
        runCurrent()
    }

    // ---- The arrival, from start to end ---------------------------------------------------------

    @Test
    fun `a connect that starts a trip plays Connecting, then Connected, then the trip`() = runTest {
        val drawn = open()
        assertEquals(listOf(HomeShown(away)), drawn)

        sees(connected)
        val connecting = drawn.last()
        assertEquals(ArrivalStage.CONNECTING, connecting.stage)
        assertFalse(connecting.recordingLayout)
        assertEquals(TruckLinkLook.CONNECTING, connecting.truckTile.look)
        assertEquals(R.string.home_truck_connecting, connecting.truckTile.title)
        assertEquals(R.string.home_truck_connecting_text, connecting.truckTile.sentence)

        // The trip opens a moment later. Home still shows the layout without one.
        after(300)
        sees(recording)
        assertEquals(ArrivalStage.CONNECTING, drawn.last().stage)
        assertFalse(drawn.last().recordingLayout)

        after(CONNECTING_MS - 300 - 1)
        assertEquals(ArrivalStage.CONNECTING, drawn.last().stage)
        after(1)
        val linked = drawn.last()
        assertEquals(ArrivalStage.CONNECTED, linked.stage)
        assertFalse(linked.recordingLayout)
        assertEquals(TruckLinkLook.CONNECTED, linked.truckTile.look)
        assertEquals(R.string.home_truck_connected, linked.truckTile.title)
        assertEquals(R.string.home_truck_recording_started, linked.truckTile.sentence)

        after(CONNECTED_MS - 1)
        assertEquals(ArrivalStage.CONNECTED, drawn.last().stage)
        after(1)
        assertTrue(drawn.last().recordingLayout)
        assertTrue(drawn.last().fadesToRecording)

        // One value for each change, and one for each stage that ran out. Nothing else.
        val size = drawn.size
        advanceUntilIdle()
        assertEquals(5, size)
        assertEquals(size, drawn.size)
    }

    @Test
    fun `a connect that starts no trip plays Connecting and then says what is so`() = runTest {
        val heldOff = ui(TruckState.CONNECTED_HELD_OFF)
        val drawn = open()

        sees(heldOff)
        assertEquals(TruckLinkLook.CONNECTING, drawn.last().truckTile.look)

        after(CONNECTING_MS)
        assertEquals(HomeShown(heldOff), drawn.last())
        assertEquals(TruckLinkLook.CONNECTED, drawn.last().truckTile.look)
        assertEquals(R.string.home_truck_held_off_text, drawn.last().truckTile.sentence)

        advanceUntilIdle()
        assertEquals(3, drawn.size)
    }

    @Test
    fun `what changes meanwhile is drawn at once and does not start the times again`() = runTest {
        val drawn = open()
        sees(recording)

        // A second into the arrival the trip has its first metres.
        after(1_000)
        val further =
            recording.copy(activity = TripActivity(trip(TripStartCause.TRUCK, 40.0), null, true))
        sees(further)
        assertEquals(further, drawn.last().ui)
        assertEquals(ArrivalStage.CONNECTING, drawn.last().stage)

        after(CONNECTING_MS - 1_000)
        assertEquals(ArrivalStage.CONNECTED, drawn.last().stage)
        after(CONNECTED_MS)
        assertEquals(ArrivalStage.HANDED_OVER, drawn.last().stage)
        assertEquals(CONNECTING_MS + CONNECTED_MS, currentTime)
    }

    @Test
    fun `the Start tile keeps its line while the arrival plays`() = runTest {
        val drawn = open()
        assertEquals(R.string.home_start_line, drawn.last().startLine)

        sees(recording)
        assertEquals(R.string.home_start_line, drawn.last().startLine)
        after(CONNECTING_MS)
        assertEquals(ArrivalStage.CONNECTED, drawn.last().stage)
        assertEquals(R.string.home_start_line, drawn.last().startLine)
    }

    // ---- What is shown at once ------------------------------------------------------------------

    @Test
    fun `a trip started with Start is shown at once, without a fade`() = runTest {
        val drawn = open()

        sees(byHand)
        assertTrue(drawn.last().recordingLayout)
        assertFalse(drawn.last().fadesToRecording)
        assertEquals(0L, currentTime)

        advanceUntilIdle()
        assertEquals(2, drawn.size)
    }

    @Test
    fun `Home opened during a trip shows the trip at once`() = runTest {
        seen.value = recording
        val drawn = open()

        assertTrue(drawn.last().recordingLayout)
        assertFalse(drawn.last().fadesToRecording)
        advanceUntilIdle()
        assertEquals(1, drawn.size)
    }

    @Test
    fun `Home opened beside a connected truck shows it connected at once`() = runTest {
        seen.value = connected
        val drawn = open()

        advanceUntilIdle()
        assertEquals(listOf(HomeShown(connected)), drawn)
        assertEquals(TruckLinkLook.CONNECTED, drawn.last().truckTile.look)
    }

    @Test
    fun `Start pressed during the arrival shows the trip at once`() = runTest {
        val drawn = open()
        sees(recording)

        after(3_000)
        assertEquals(ArrivalStage.CONNECTED, drawn.last().stage)
        assertFalse(drawn.last().recordingLayout)

        presses.emit(Unit)
        runCurrent()
        assertEquals(3_000L, currentTime)
        assertTrue(drawn.last().recordingLayout)
        assertFalse(drawn.last().fadesToRecording)

        val size = drawn.size
        advanceUntilIdle()
        assertEquals(size, drawn.size)
    }

    @Test
    fun `a truck that goes again during Connecting is shown as gone at once`() = runTest {
        val drawn = open()
        sees(connected)

        after(1_000)
        sees(away)
        assertEquals(HomeShown(away), drawn.last())

        val size = drawn.size
        advanceUntilIdle()
        assertEquals(size, drawn.size)
    }

    // ---- Only what happens in front of the owner ------------------------------------------------

    @Test
    fun `what the truck does while Home is not in front is never played`() = runTest {
        val drawn = open()

        onScreen.value = false
        runCurrent()
        sees(recording)
        // Not in front: what is so is kept up to date all the same.
        assertEquals(HomeShown(recording), drawn.last())

        onScreen.value = true
        runCurrent()
        assertEquals(HomeShown(recording), drawn.last())
        advanceUntilIdle()
        assertEquals(HomeShown(recording), drawn.last())
    }

    @Test
    fun `leaving the screen in the middle of an arrival ends it`() = runTest {
        val drawn = open()
        sees(recording)
        assertEquals(ArrivalStage.CONNECTING, drawn.last().stage)

        after(1_000)
        onScreen.value = false
        runCurrent()
        assertEquals(HomeShown(recording), drawn.last())

        val size = drawn.size
        advanceUntilIdle()
        assertEquals(size, drawn.size)
    }

    @Test
    fun `back in front, the next arrival plays`() = runTest {
        val drawn = open()
        onScreen.value = false
        runCurrent()
        onScreen.value = true
        runCurrent()

        sees(recording)
        assertEquals(ArrivalStage.CONNECTING, drawn.last().stage)
    }

    @Test
    fun `a screen that is not in front yet plays nothing until it is`() = runTest {
        onScreen.value = false
        val drawn = open()

        sees(recording)
        assertEquals(HomeShown(recording), drawn.last())
    }
}
