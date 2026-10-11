package com.shawnkowalchuk.milo.feature.home

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.TruckLinkLook
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import com.shawnkowalchuk.milo.platform.trip.ParkedTruckWatch
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What Home says about the truck in every state MilO can really be in: which state is shown,
 * in which look, and that the facts are taken from where MilO keeps them.
 */
class TruckStateTest {
    /** A paired truck, not connected, no trip, nothing in the way: an ordinary morning. */
    private val morning =
        TruckFacts(
            paired = true,
            connected = false,
            recording = false,
            waitingForTruck = false,
            autoStartHeldOff = false,
            setupNeedsAttention = false,
        )

    // ---- No trip open -----------------------------------------------------------------------------

    @Test
    fun `a paired truck that is not connected reads as the design draws it`() {
        val state = truckState(morning)

        assertEquals(TruckState.NOT_CONNECTED, state)
        assertEquals(TruckLinkLook.IDLE, state.look)
        assertEquals(R.string.home_truck_not_connected, state.title)
        assertEquals(R.string.home_truck_not_connected_text, state.sentence)
    }

    @Test
    fun `while Setup needs attention the tile does not promise a start by itself`() {
        val state = truckState(morning.copy(setupNeedsAttention = true))

        assertEquals(TruckState.NOT_CONNECTED_SETUP_OPEN, state)
        assertEquals(R.string.home_truck_may_not_start_text, state.sentence)
    }

    @Test
    fun `with no truck paired the tile says so, whatever else is known`() {
        val unpaired = morning.copy(paired = false)

        assertEquals(TruckState.NO_TRUCK, truckState(unpaired))
        assertEquals(TruckState.NO_TRUCK, truckState(unpaired.copy(connected = null)))
        assertEquals(TruckState.NO_TRUCK, truckState(unpaired.copy(setupNeedsAttention = true)))
    }

    @Test
    fun `until the settings and the truck have been read, nothing is claimed`() {
        assertEquals(TruckState.CHECKING, truckState(morning.copy(paired = null)))
        assertEquals(TruckState.CHECKING, truckState(morning.copy(connected = null)))
        assertEquals(TruckLinkLook.IDLE, TruckState.CHECKING.look)
    }

    @Test
    fun `connected after End was pressed says that the next trip waits for a disconnect`() {
        val state = truckState(morning.copy(connected = true, autoStartHeldOff = true))

        assertEquals(TruckState.CONNECTED_HELD_OFF, state)
        assertEquals(TruckLinkLook.CONNECTED, state.look)
        assertEquals(R.string.home_truck_held_off_text, state.sentence)
    }

    @Test
    fun `connected with no trip and no hold-off says only that nothing is recorded`() {
        val state = truckState(morning.copy(connected = true))

        assertEquals(TruckState.CONNECTED_NOT_RECORDING, state)
        assertEquals(TruckLinkLook.CONNECTED, state.look)
        assertEquals(R.string.home_truck_not_recording_text, state.sentence)
    }

    @Test
    fun `a hold-off that is still stored means nothing once the truck is gone`() {
        assertEquals(TruckState.NOT_CONNECTED, truckState(morning.copy(autoStartHeldOff = true)))
    }

    // ---- Beside a parked truck --------------------------------------------------------------------

    @Test
    fun `a parked truck that MilO watches is connected, and says a trip starts when it moves`() {
        val waiting = morning.copy(connected = true, parked = ParkedTruckWatch.WAITING_TO_MOVE)
        val state = truckState(waiting)

        assertEquals(TruckState.PARKED_WAITING, state)
        assertEquals(TruckLinkLook.CONNECTED, state.look)
        assertEquals(R.string.trip_status_parked, state.title)
        assertEquals(R.string.home_trip_parked_detail, state.sentence)
    }

    @Test
    fun `a parked truck that MilO has stopped watching says to press Start`() {
        val unwatched = morning.copy(connected = true, parked = ParkedTruckWatch.NO_LONGER_WATCHED)
        val state = truckState(unwatched)

        assertEquals(TruckState.PARKED_NOT_WATCHED, state)
        assertEquals(TruckLinkLook.CONNECTED, state.look)
        assertEquals(R.string.trip_status_parked_not_watched, state.title)
        assertEquals(R.string.home_trip_parked_not_watched_detail, state.sentence)
    }

    @Test
    fun `the wait is the trip rules' word, whatever else is read or not read`() {
        val waiting = morning.copy(parked = ParkedTruckWatch.WAITING_TO_MOVE)

        // Android Auto on the cable holds a wait while Bluetooth reads as not connected.
        assertEquals(TruckState.PARKED_WAITING, truckState(waiting))
        // End was pressed on a trip before: the wait took the hold-off's place.
        val heldOff = waiting.copy(connected = true, autoStartHeldOff = true)
        assertEquals(TruckState.PARKED_WAITING, truckState(heldOff))
        assertEquals(
            TruckState.PARKED_WAITING,
            truckState(waiting.copy(setupNeedsAttention = true)),
        )
        assertEquals(TruckState.PARKED_WAITING, truckState(waiting.copy(paired = null)))
        assertEquals(TruckState.PARKED_WAITING, truckState(waiting.copy(connected = null)))
    }

    @Test
    fun `an open trip comes before a wait, should both ever be reported`() {
        val both = morning.copy(recording = true, connected = true)

        for (watch in ParkedTruckWatch.entries) {
            assertEquals(TruckState.CONNECTED_RECORDING, truckState(both.copy(parked = watch)))
        }
    }

    // ---- A trip is open ---------------------------------------------------------------------------

    @Test
    fun `a trip with the truck connected is the connected look`() {
        val state = truckState(morning.copy(recording = true, connected = true))

        assertEquals(TruckState.CONNECTED_RECORDING, state)
        assertEquals(TruckLinkLook.CONNECTED, state.look)
        assertEquals(R.string.home_truck_connected, state.title)
    }

    @Test
    fun `a trip without the truck says not connected, also with no truck paired`() {
        val byHand = morning.copy(recording = true)

        assertEquals(TruckState.RECORDING_WITHOUT_TRUCK, truckState(byHand))
        assertEquals(TruckState.RECORDING_WITHOUT_TRUCK, truckState(byHand.copy(paired = false)))
        assertEquals(TruckLinkLook.IDLE, TruckState.RECORDING_WITHOUT_TRUCK.look)
    }

    @Test
    fun `the grace period keeps the sentence Home has always shown for it`() {
        val state = truckState(morning.copy(recording = true, waitingForTruck = true))

        assertEquals(TruckState.WAITING_TO_RECONNECT, state)
        assertEquals(TruckLinkLook.IDLE, state.look)
        assertEquals(R.string.trip_status_waiting_for_truck, state.sentence)
    }

    @Test
    fun `the tile of the trip being recorded says the grace period and nothing else`() {
        // Since 2026-10-10 the tile is two rows. Only the wait for the truck adds a line.
        assertEquals(
            R.string.trip_status_waiting_for_truck,
            recordingNoteRes(TruckState.WAITING_TO_RECONNECT),
        )
        for (state in TruckState.entries - TruckState.WAITING_TO_RECONNECT) {
            assertNull("$state", recordingNoteRes(state))
        }
    }

    @Test
    fun `while a trip is open, a setup that needs attention does not change the truck's word`() {
        val recording = morning.copy(recording = true, connected = true, setupNeedsAttention = true)

        assertEquals(TruckState.CONNECTED_RECORDING, truckState(recording))
    }

    // ---- Every state ------------------------------------------------------------------------------

    @Test
    fun `every state shown with no trip open has a sentence, for the large tile`() {
        val idleStates =
            listOf(
                TruckState.CHECKING,
                TruckState.NO_TRUCK,
                TruckState.NOT_CONNECTED,
                TruckState.NOT_CONNECTED_SETUP_OPEN,
                TruckState.CONNECTED_HELD_OFF,
                TruckState.CONNECTED_NOT_RECORDING,
                TruckState.PARKED_WAITING,
                TruckState.PARKED_NOT_WATCHED,
            )

        for (state in idleStates) assertNotNull(state.name, state.sentence)
    }

    @Test
    fun `the connected look always has a title that says connected, and the idle look never`() {
        val connectedTitles =
            setOf(
                R.string.home_truck_connected,
                R.string.trip_status_parked,
                R.string.trip_status_parked_not_watched,
            )

        for (state in TruckState.entries) {
            val saysConnected = state.title in connectedTitles
            assertEquals(state.name, state.look == TruckLinkLook.CONNECTED, saysConnected)
        }
    }

    // ---- Where the facts come from ----------------------------------------------------------------

    private val trip =
        CurrentTrip(
            tripId = 7,
            startedAtMs = 1_000,
            startedBy = TripStartCause.TRUCK,
            distanceMetres = 0.0,
            waitingForTruck = false,
        )

    @Test
    fun `a stored truck address is what makes a truck paired`() {
        val idle = TripActivity(truckConnected = false)

        assertEquals(true, truckFacts(MiloSettings(truckAddress = "AA:BB"), idle, false).paired)
        assertEquals(false, truckFacts(MiloSettings(), idle, false).paired)
        assertNull(truckFacts(stored = null, activity = idle, setupNeedsAttention = false).paired)
    }

    @Test
    fun `the hold-off is the stored one, and unread settings hold nothing off`() {
        val idle = TripActivity(truckConnected = true)
        val heldOff = MiloSettings(truckAddress = "AA:BB", autoStartHeldOffSinceMs = 5_000)

        assertTrue(truckFacts(heldOff, idle, false).autoStartHeldOff)
        assertFalse(truckFacts(MiloSettings(truckAddress = "AA:BB"), idle, false).autoStartHeldOff)
        assertFalse(truckFacts(null, idle, false).autoStartHeldOff)
    }

    @Test
    fun `the trip and the truck's connection are the trip controller's`() {
        val waiting = TripActivity(trip.copy(waitingForTruck = true), truckConnected = false)
        val facts = truckFacts(MiloSettings(truckAddress = "AA:BB"), waiting, true)

        assertTrue(facts.recording)
        assertTrue(facts.waitingForTruck)
        assertEquals(false, facts.connected)
        assertTrue(facts.setupNeedsAttention)
        assertNull(truckFacts(null, TripActivity(), false).connected)
    }

    @Test
    fun `the wait beside a parked truck is the trip controller's too`() {
        val stored = MiloSettings(truckAddress = "AA:BB")
        val waiting = TripActivity(truckConnected = true, parked = ParkedTruckWatch.WAITING_TO_MOVE)

        assertEquals(ParkedTruckWatch.WAITING_TO_MOVE, truckFacts(stored, waiting, false).parked)
        assertNull(truckFacts(stored, TripActivity(truckConnected = true), false).parked)
        assertEquals(
            TruckState.PARKED_NOT_WATCHED,
            truckState(
                truckFacts(
                    stored,
                    waiting.copy(parked = ParkedTruckWatch.NO_LONGER_WATCHED),
                    setupNeedsAttention = false,
                ),
            ),
        )
    }

    // ---- The vehicle's name, at the end of the tile's first line ----------------------------------

    @Test
    fun `with no vehicle paired the tile names none, and does not fill the place with a word`() {
        assertNull(TruckTileState(TruckState.NO_TRUCK, truckName = null).vehicleShown("The truck"))
    }

    @Test
    fun `while MilO is still reading, only a name it has read already is shown`() {
        val unread = TruckTileState(TruckState.CHECKING, truckName = null)
        val read = TruckTileState(TruckState.CHECKING, truckName = "F-150")

        assertNull(unread.vehicleShown("The truck"))
        assertEquals("F-150", read.vehicleShown("The truck"))
    }

    @Test
    fun `every other state of the large tile names its vehicle, by a stand-in if it has no name`() {
        val named = TruckState.entries - setOf(TruckState.NO_TRUCK, TruckState.CHECKING)

        for (state in named) {
            val withName = TruckTileState(state, "F-150").vehicleShown("The truck")
            val withoutName = TruckTileState(state, null).vehicleShown("The truck")

            assertEquals(state.name, "F-150", withName)
            assertEquals(state.name, "The truck", withoutName)
        }
    }

    // ---- The line on the Start tile ---------------------------------------------------------------

    @Test
    fun `the Start tile offers the truck only while connecting it would start a trip`() {
        val ownLine =
            setOf(
                TruckState.NOT_CONNECTED,
                TruckState.PARKED_WAITING,
                TruckState.PARKED_NOT_WATCHED,
            )

        assertEquals(R.string.home_start_line, startLineRes(TruckState.NOT_CONNECTED))
        for (state in TruckState.entries - ownLine) {
            assertEquals(state.name, R.string.home_start_line_by_hand, startLineRes(state))
        }
    }

    @Test
    fun `beside a parked truck the Start tile says what starts the next trip`() {
        // Watched: the truck is connected already, and the trip starts when it moves.
        assertEquals(R.string.home_start_line_parked, startLineRes(TruckState.PARKED_WAITING))
        // No longer watched: nothing starts it but the press.
        assertEquals(
            R.string.home_start_line_not_watched,
            startLineRes(TruckState.PARKED_NOT_WATCHED),
        )
    }
}
