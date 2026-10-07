package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.EndTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndWaiting
import com.shawnkowalchuk.milo.core.trip.TripEffect.HoldOffAutoStart
import com.shawnkowalchuk.milo.core.trip.TripEffect.MarkTruckSeen
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartWaiting
import com.shawnkowalchuk.milo.core.trip.TripEvent.AndroidAutoConnection
import com.shawnkowalchuk.milo.core.trip.TripEvent.ManualEnd
import com.shawnkowalchuk.milo.core.trip.TripEvent.ManualStart
import com.shawnkowalchuk.milo.core.trip.TripEvent.Moved
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckAppeared
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckConnection
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckLinkConnected
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wait beside a truck that is connected and parked (ADR-002, amendment 28): what starts the
 * next trip, what ends the wait without one, how long MilO watches, and how the wait comes
 * through a restart. The rule that closes the trip before it is in [TripStateMachineParkedTest].
 */
class TripStateMachineWaitingTest {
    private val later = WAITING_SINCE + HOUR

    // ---- A stop, then a drive: two trips ---------------------------------------------------------

    @Test
    fun `the truck moving again starts a new trip, with no trip-start sound`() {
        val result = WAITING.onParked(Moved(later))

        assertEquals(
            listOf(
                EndWaiting(WaitingEnd.MOVED),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, later, fromParked = true),
            ),
            result.effects,
        )
        // An ordinary truck trip from here on: it ends on a disconnect, or when it is parked.
        assertEquals(
            TripState(
                trip = ActiveTrip(TripStartCause.TRUCK, truckSeen = true, lastMovementAtMs = later),
                truckConnected = true,
            ),
            result.state,
        )
        // The sound means "connected to the truck", and nothing connected.
        assertFalse(result.tripReallyBegan(before = WAITING))
    }

    @Test
    fun `a drive, a stop and a drive are two trips, the second starting when the truck moved`() {
        val stopped = T0 + 40 * MINUTE
        val closed = stopped + PARKED_LIMIT
        val drivesOff = closed + 25 * MINUTE

        val result =
            RECORDING.onAllParked(
                Moved(stopped),
                TruckConnection(true, closed),
                TruckConnection(true, closed + MINUTE),
                Moved(drivesOff),
                Moved(drivesOff + 5_000),
            )

        assertEquals(
            listOf(
                EndTrip(TripEndReason.NO_MOVEMENT, stopped),
                StartWaiting(closed),
                EndWaiting(WaitingEnd.MOVED),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, drivesOff, fromParked = true),
            ),
            result.effects,
        )
        assertEquals(drivesOff + 5_000, result.state.trip?.lastMovementAtMs)
    }

    @Test
    fun `a reading that shows the truck still connected starts nothing while MilO waits`() {
        // The once-a-minute check, MilO being opened, a profile reconnecting: none is movement.
        val result = WAITING.onParked(TruckConnection(true, later))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(WAITING, result.state)
    }

    @Test
    fun `Android Auto connecting or disconnecting changes nothing about the wait`() {
        val plugged = WAITING.onParked(AndroidAutoConnection(true, later))
        val unplugged = plugged.state.onParked(AndroidAutoConnection(false, later + MINUTE))

        assertEquals(emptyList<TripEffect>(), plugged.effects + unplugged.effects)
        assertEquals(WAITING, unplugged.state)
    }

    // ---- The wait ends without a trip ---------------------------------------------------------------

    @Test
    fun `the truck disconnecting ends the wait, and nothing is recorded`() {
        val result = WAITING.onParked(TruckConnection(false, later))

        // No grace period: there is no trip to hold open.
        assertEquals(listOf(EndWaiting(WaitingEnd.TRUCK_DISCONNECTED)), result.effects)
        assertEquals(IDLE, result.state)
        assertNull(TripStateMachine.nextCheckAtMs(result.state, PARKED_RULES))
    }

    @Test
    fun `after the wait ended by disconnect the next connect starts a trip as always`() {
        val gone = WAITING.onParked(TruckConnection(false, later)).state

        val result = gone.onParked(TruckLinkConnected(later + 8 * HOUR))

        assertEquals(
            listOf(StartTrip(TripStartCause.TRUCK, truckSeen = true, later + 8 * HOUR)),
            result.effects,
        )
        assertTrue(result.tripReallyBegan(before = gone))
    }

    // ---- A new link while waiting -------------------------------------------------------------------

    @Test
    fun `a new link to the truck while waiting starts a trip as every connect does`() {
        // The old link dropped unseen (the truck was switched off and on again).
        val result = WAITING.onParked(TruckLinkConnected(later))

        assertEquals(
            listOf(
                EndWaiting(WaitingEnd.NEW_LINK),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, later),
            ),
            result.effects,
        )
        assertTrue(result.tripReallyBegan(before = WAITING))
    }

    @Test
    fun `the companion callback while waiting starts a trip that must be confirmed`() {
        val result = WAITING.onParked(TruckAppeared(later))

        assertEquals(
            listOf(
                EndWaiting(WaitingEnd.NEW_LINK),
                StartTrip(TripStartCause.TRUCK, truckSeen = false, later),
            ),
            result.effects,
        )
        assertEquals(later + START_CONFIRMATION_MS, result.state.trip?.confirmByMs)
        // What was believed about the old link is not taken for a confirmation of the new one.
        assertFalse(result.state.truckConnected)

        val confirmed = result.state.onParked(TruckLinkConnected(later + 300))
        assertEquals(listOf(MarkTruckSeen), confirmed.effects)
    }

    // ---- The buttons, and the hold-off -----------------------------------------------------------------

    @Test
    fun `Start pressed while waiting starts a trip at once`() {
        val result = WAITING.onParked(ManualStart(truckConnected = true, later))

        assertEquals(
            listOf(
                EndWaiting(WaitingEnd.START_PRESSED),
                StartTrip(TripStartCause.MANUAL, truckSeen = true, later),
            ),
            result.effects,
        )
        assertTrue(result.tripReallyBegan(before = WAITING))
    }

    @Test
    fun `End pressed while waiting ends no trip, holds automatic start off and ends the wait`() {
        val result = WAITING.onParked(ManualEnd(truckConnected = true, later))

        assertEquals(
            listOf(EndWaiting(WaitingEnd.END_PRESSED), HoldOffAutoStart(later)),
            result.effects,
        )
        assertEquals(
            TripState(truckConnected = true, autoStartHeldOffSinceMs = later),
            result.state,
        )
    }

    @Test
    fun `while automatic start is held off, movement starts no trip`() {
        // After End was pressed beside the parked truck, and after any other End press.
        val heldOff = WAITING.onParked(ManualEnd(truckConnected = true, later)).state

        for (state in listOf(heldOff, HELD_OFF)) {
            val result = state.onParked(Moved(later + 2 * HOUR))
            assertEquals(emptyList<TripEffect>(), result.effects)
            assertNull(result.state.trip)
        }
    }

    @Test
    fun `End pressed while waiting, with the truck found gone, holds nothing off`() {
        val result = WAITING.onParked(ManualEnd(truckConnected = false, later))

        assertEquals(listOf(EndWaiting(WaitingEnd.END_PRESSED)), result.effects)
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `movement starts nothing when MilO is not waiting`() {
        val result = IDLE.onParked(Moved(T0))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(IDLE, result.state)
    }
}
