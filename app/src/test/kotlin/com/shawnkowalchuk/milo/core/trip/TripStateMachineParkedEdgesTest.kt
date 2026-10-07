package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.CancelGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndWaiting
import com.shawnkowalchuk.milo.core.trip.TripEffect.HoldOffAutoStart
import com.shawnkowalchuk.milo.core.trip.TripEffect.ReleaseHoldOff
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
 * The parked rule where it meets the rest of the trip rules: the two buttons, the hold-off, a
 * restart of the process, and the setting. The rule itself is in [TripStateMachineParkedTest].
 */
class TripStateMachineParkedEdgesTest {
    /** A reading that shows the truck connected: the once-a-minute check, or the timer's own. */
    private fun connectedAt(atMs: Long) = TruckConnection(true, atMs)

    // ---- The buttons on a trip that has stood too long -----------------------------------------------

    @Test
    fun `End pressed on a trip that stood too long closes it where it last moved and holds off`() {
        val pressed = T0 + HOUR

        val result = RECORDING.onParked(ManualEnd(truckConnected = true, pressed))

        // No wait is begun only to be ended by the same press.
        assertEquals(
            listOf(EndTrip(TripEndReason.NO_MOVEMENT, T0), HoldOffAutoStart(pressed)),
            result.effects,
        )
        assertNull(result.state.parked)
    }

    @Test
    fun `Start pressed on a trip that stood too long closes it and starts a new one`() {
        val pressed = T0 + HOUR

        val result = RECORDING.onParked(ManualStart(truckConnected = true, pressed))

        assertEquals(
            listOf(
                EndTrip(TripEndReason.NO_MOVEMENT, T0),
                StartTrip(TripStartCause.MANUAL, truckSeen = true, pressed),
            ),
            result.effects,
        )
        assertTrue(result.tripReallyBegan(before = RECORDING))
    }

    // ---- The hold-off and the parked rule -------------------------------------------------------------

    @Test
    fun `a trip started by hand while held off, once parked, ends the hold-off and MilO waits`() {
        // The evening of 2026-10-06: End pressed with the truck connected, then, later, Start.
        // Kept through the stop, the hold-off would leave the truck connected, parked and
        // unwatched, and the drive after the stop would be recorded by nothing.
        val started = T0 + 2 * HOUR
        val byHand = HELD_OFF.onParked(ManualStart(truckConnected = true, started)).state
        assertEquals(HELD_OFF_SINCE, byHand.autoStartHeldOffSinceMs)

        val parked = byHand.onParked(connectedAt(started + PARKED_LIMIT))

        assertEquals(
            listOf(
                EndTrip(TripEndReason.NO_MOVEMENT, started),
                ReleaseHoldOff(HoldOffRelease.TRIP_PARKED),
                StartWaiting(started + PARKED_LIMIT),
            ),
            parked.effects,
        )
        assertTrue(parked.state.waitingToMove)
        assertFalse(parked.state.autoStartHeldOff)

        // And the drive after the stop is a trip.
        val drivesOn = parked.state.onParked(Moved(started + HOUR))
        assertEquals(TripStartCause.TRUCK, drivesOn.state.trip?.startedBy)
    }

    @Test
    fun `the same after End was pressed beside the parked truck, and then Start`() {
        val ended = WAITING.onParked(ManualEnd(truckConnected = true, WAITING_SINCE + MINUTE))
        val started = WAITING_SINCE + 2 * MINUTE
        val byHand = ended.state.onParked(ManualStart(truckConnected = true, started)).state

        val parked = byHand.onParked(connectedAt(started + PARKED_LIMIT))

        assertEquals(Parked(sinceMs = started + PARKED_LIMIT), parked.state.parked)
        assertNull(parked.state.autoStartHeldOffSinceMs)
    }

    @Test
    fun `End pressed on that trip holds automatic start off as it always does`() {
        // The hold-off is given up only for a wait. A press of End sets it again.
        val started = T0 + 2 * HOUR
        val byHand = HELD_OFF.onParked(ManualStart(truckConnected = true, started)).state

        val result = byHand.onParked(ManualEnd(truckConnected = true, started + HOUR))

        assertEquals(
            listOf(EndTrip(TripEndReason.NO_MOVEMENT, started), HoldOffAutoStart(started + HOUR)),
            result.effects,
        )
        assertNull(result.state.parked)
    }

    // ---- After a restart --------------------------------------------------------------------------------

    @Test
    fun `a restart finds a trip that stood too long and closes it where it last moved`() {
        val trip = checkNotNull(RECORDING.trip).copy(lastMovementAtMs = T0 + HOUR)
        val now = T0 + HOUR + 20 * MINUTE

        // Its newest stored point is five minutes old, so it is not stale: it is parked.
        val result =
            TripStateMachine.restore(trip, now - 5 * MINUTE, null, true, false, now, PARKED_RULES)

        assertEquals(
            listOf(EndTrip(TripEndReason.NO_MOVEMENT, T0 + HOUR), StartWaiting(now)),
            result.effects,
        )
    }

    @Test
    fun `a restart inside the limit carries the trip on`() {
        val trip = checkNotNull(RECORDING.trip).copy(lastMovementAtMs = T0 + HOUR)
        val now = T0 + HOUR + 4 * MINUTE

        val result = TripStateMachine.restore(trip, now, null, true, false, now, PARKED_RULES)

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertFalse(result.state.waitingToMove)
    }

    // ---- The settings ---------------------------------------------------------------------------------------

    @Test
    fun `the limit is whatever the settings say`() {
        val quarterHour = TripRules(gracePeriodMs = GRACE, parkedLimitMs = 15 * MINUTE)

        val early = TripStateMachine.step(RECORDING, connectedAt(T0 + 14 * MINUTE), quarterHour)
        val due = TripStateMachine.step(RECORDING, connectedAt(T0 + 15 * MINUTE), quarterHour)

        assertEquals(emptyList<TripEffect>(), early.effects)
        assertEquals(EndTrip(TripEndReason.NO_MOVEMENT, T0), due.effects.first())
    }

    @Test
    fun `a parked limit that is not positive is refused`() {
        for (limit in listOf(0L, -1L)) {
            val refused = runCatching { TripRules(gracePeriodMs = GRACE, parkedLimitMs = limit) }
            assertTrue("$limit ms", refused.exceptionOrNull() is IllegalArgumentException)
        }
        val waiting = runCatching { TripRules(gracePeriodMs = GRACE, waitingLimitMs = 0) }
        assertTrue(waiting.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `ending a wait is never the moment a trip really began`() {
        val result = WAITING.onParked(TruckConnection(false, WAITING_SINCE + MINUTE))

        assertEquals(listOf(EndWaiting(WaitingEnd.TRUCK_DISCONNECTED)), result.effects)
        assertFalse(result.tripReallyBegan(before = WAITING))
    }
}
