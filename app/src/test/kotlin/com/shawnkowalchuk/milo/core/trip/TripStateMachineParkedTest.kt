package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.CancelGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndWaiting
import com.shawnkowalchuk.milo.core.trip.TripEffect.HoldOffAutoStart
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartWaiting
import com.shawnkowalchuk.milo.core.trip.TripEvent.AndroidAutoConnection
import com.shawnkowalchuk.milo.core.trip.TripEvent.ManualEnd
import com.shawnkowalchuk.milo.core.trip.TripEvent.ManualStart
import com.shawnkowalchuk.milo.core.trip.TripEvent.MoveTakenBack
import com.shawnkowalchuk.milo.core.trip.TripEvent.Moved
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parked rule (ADR-002, amendment 28): a trip that is being recorded and has not really
 * moved for the limit is closed where it last moved, even though the truck is still connected.
 * What follows while the truck stays connected, the wait and the trip that movement starts, is
 * in [TripStateMachineWaitingTest].
 */
class TripStateMachineParkedTest {
    /** A reading that shows the truck connected: the once-a-minute check, or the timer's own. */
    private fun connectedAt(atMs: Long) = TruckConnection(true, atMs)

    // ---- A drive, and a stop -------------------------------------------------------------------

    @Test
    fun `a drive is not ended while the truck keeps moving`() {
        // Two hours on the road, a reading every minute, the last movement never a minute old.
        var state = RECORDING
        for (minute in 1..120) {
            val moved = state.onParked(Moved(T0 + minute * MINUTE - 5_000))
            val read = moved.state.onParked(connectedAt(T0 + minute * MINUTE))
            assertEquals(emptyList<TripEffect>(), moved.effects + read.effects)
            state = read.state
        }
        assertNotNull(state.trip)
    }

    @Test
    fun `a stop just under the limit leaves the trip open`() {
        val stopped = RECORDING.onParked(Moved(T0 + 30 * MINUTE)).state

        val result = stopped.onParked(connectedAt(T0 + 30 * MINUTE + PARKED_LIMIT - 1))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(stopped, result.state)
    }

    @Test
    fun `a stop of the limit ends the trip where it last moved, and MilO waits beside the truck`() {
        val lastMoved = T0 + 30 * MINUTE
        val now = lastMoved + PARKED_LIMIT
        val stopped = RECORDING.onParked(Moved(lastMoved)).state

        val result = stopped.onParked(connectedAt(now))

        // Cut at the last movement, not at the limit: the ten minutes are the truck standing.
        assertEquals(
            listOf(EndTrip(TripEndReason.NO_MOVEMENT, lastMoved), StartWaiting(now)),
            result.effects,
        )
        assertEquals(TripState(truckConnected = true, parked = Parked(sinceMs = now)), result.state)
        assertTrue(result.state.waitingToMove)
    }

    @Test
    fun `the timer is set for the limit after the last movement, and moves on with each one`() {
        assertEquals(T0 + PARKED_LIMIT, TripStateMachine.nextCheckAtMs(RECORDING, PARKED_RULES))

        val moved = RECORDING.onParked(Moved(T0 + 7 * MINUTE)).state

        val due = T0 + 7 * MINUTE + PARKED_LIMIT
        assertEquals(due, TripStateMachine.nextCheckAtMs(moved, PARKED_RULES))
    }

    @Test
    fun `a trip that never moved ends at its start`() {
        // The truck connected in the driveway and was never driven.
        val result = RECORDING.onParked(connectedAt(T0 + PARKED_LIMIT))

        assertEquals(EndTrip(TripEndReason.NO_MOVEMENT, T0), result.effects.first())
    }

    @Test
    fun `only a fresh reading of the truck closes a parked trip`() {
        // What comes next depends on whether the truck is still connected, so a fix or an
        // Android Auto change that arrives after the limit closes nothing.
        val late = T0 + 3 * PARKED_LIMIT
        val notReadings = listOf(AndroidAutoConnection(false, late), MoveTakenBack(T0, late))

        for (event in notReadings) {
            val result = RECORDING.onParked(event)
            assertEquals("after $event", emptyList<TripEffect>(), result.effects)
            assertNotNull("after $event", result.state.trip)
        }
    }

    @Test
    fun `a slow crawl in traffic does not end the trip`() {
        // Stop and go for an hour: the truck really moves every eight minutes or so.
        var state = RECORDING
        for (step in 1..8) {
            val at = T0 + step * 8 * MINUTE
            state = state.onParked(Moved(at)).state
            val read = state.onParked(connectedAt(at + 7 * MINUTE))
            assertEquals(emptyList<TripEffect>(), read.effects)
            state = read.state
        }
        assertNotNull(state.trip)
    }

    @Test
    fun `movement that turns out to be one bad fix does not keep a parked trip open`() {
        // Parked since T0. Nine minutes in, one stray fix looks like movement, and the fix
        // after it takes that back.
        val strayAt = T0 + 9 * MINUTE
        val stray = RECORDING.onParked(Moved(strayAt)).state
        assertEquals(strayAt + PARKED_LIMIT, TripStateMachine.nextCheckAtMs(stray, PARKED_RULES))

        val takenBack = stray.onParked(MoveTakenBack(lastMovedAtMs = T0, atMs = strayAt + 5_000))
        assertEquals(emptyList<TripEffect>(), takenBack.effects)
        assertEquals(RECORDING, takenBack.state)

        // So the limit runs out when it would have, and the trip is cut where it really stopped.
        val result = takenBack.state.onParked(connectedAt(T0 + PARKED_LIMIT))
        assertEquals(EndTrip(TripEndReason.NO_MOVEMENT, T0), result.effects.first())
    }

    @Test
    fun `taking a movement back never moves the last movement forward`() {
        val moved = RECORDING.onParked(Moved(T0 + 5 * MINUTE)).state

        val result = moved.onParked(MoveTakenBack(lastMovedAtMs = T0 + 9 * MINUTE, T0 + 9 * MINUTE))

        assertEquals(T0 + 5 * MINUTE, result.state.trip?.lastMovementAtMs)
    }

    // ---- Every trip, however it started and whatever is connected --------------------------------

    @Test
    fun `Android Auto being connected does not hold a parked trip open`() {
        val plugged = RECORDING.onParked(AndroidAutoConnection(true, T0 + MINUTE)).state

        val result = plugged.onParked(connectedAt(T0 + PARKED_LIMIT))

        assertEquals(
            listOf(EndTrip(TripEndReason.NO_MOVEMENT, T0), StartWaiting(T0 + PARKED_LIMIT)),
            result.effects,
        )
    }

    @Test
    fun `with nothing connected a parked trip ends, and nothing waits`() {
        // A trip started by hand that the truck never joined: nothing could ever end a wait.
        val result = MANUAL_NO_TRUCK.onParked(TruckConnection(false, T0 + PARKED_LIMIT))

        assertEquals(listOf(EndTrip(TripEndReason.NO_MOVEMENT, T0)), result.effects)
        assertNull(result.state.parked)
        assertNull(result.state.trip)
    }

    @Test
    fun `a manual trip the truck joined is parked like any other`() {
        val joined = MANUAL_NO_TRUCK.onParked(connectedAt(T0 + MINUTE)).state

        val result = joined.onParked(connectedAt(T0 + PARKED_LIMIT))

        assertEquals(
            listOf(EndTrip(TripEndReason.NO_MOVEMENT, T0), StartWaiting(T0 + PARKED_LIMIT)),
            result.effects,
        )
    }

    @Test
    fun `a companion start still to be confirmed is not judged by the parked rule`() {
        val started = IDLE.onParked(TripEvent.TruckAppeared(T0)).state
        val confirmBy = checkNotNull(started.trip?.confirmByMs)

        assertEquals(confirmBy, TripStateMachine.nextCheckAtMs(started, PARKED_RULES))
        // Not confirmed: a false start, as before, whatever the parked limit says.
        val result = started.onParked(TruckConnection(false, T0 + PARKED_LIMIT))
        assertEquals(listOf(EndTrip(TripEndReason.FALSE_START, T0 + PARKED_LIMIT)), result.effects)
    }

    // ---- A disconnect during the stop: the grace period wins -------------------------------------

    @Test
    fun `a disconnect during the stop starts the grace period, and the trip ends by its rules`() {
        // Stopped at T0. Eight minutes on, Shawn switches the truck off and walks away.
        val gone = T0 + 8 * MINUTE
        val inGrace = RECORDING.onParked(TruckConnection(false, gone))
        assertEquals(listOf(StartGrace(gone, gone + GRACE)), inGrace.effects)
        assertEquals(gone + GRACE, TripStateMachine.nextCheckAtMs(inGrace.state, PARKED_RULES))

        // The parked limit and the grace deadline fall in the same moment. The grace period
        // decides: the trip ends where the truck was found gone, and nothing waits.
        val result = inGrace.state.onParked(TruckConnection(false, T0 + PARKED_LIMIT))

        assertEquals(listOf(EndTrip(TripEndReason.GRACE_EXPIRED, gone)), result.effects)
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `a truck that comes back inside the grace period to a long stop is parked at once`() {
        val gone = T0 + 15 * MINUTE
        val inGrace = RECORDING.onParked(TruckConnection(false, gone)).state

        // Back a minute later, and still where it stopped a quarter of an hour ago.
        val result = inGrace.onParked(connectedAt(gone + MINUTE))

        assertEquals(
            listOf(
                CancelGrace,
                EndTrip(TripEndReason.NO_MOVEMENT, T0),
                StartWaiting(gone + MINUTE),
            ),
            result.effects,
        )
    }
}
