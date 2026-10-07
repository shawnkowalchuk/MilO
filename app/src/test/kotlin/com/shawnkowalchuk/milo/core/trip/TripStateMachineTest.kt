package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.CancelGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartTrip
import com.shawnkowalchuk.milo.core.trip.TripEvent.AndroidAutoConnection
import com.shawnkowalchuk.milo.core.trip.TripEvent.Moved
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The rules of ADR-002's "Trip rules" for a trip the truck starts and ends, in the ADR's order.
 * How a grace period ends is in [TripStateMachineGraceTest], the buttons are in
 * [TripStateMachineManualTest], the awkward orderings (repeated, missing and reversed events) in
 * [TripStateMachineOrderingTest] and [TripStateMachineRandomTest], and recovery after a restart
 * in [TripStateMachineRestoreTest].
 */
class TripStateMachineTest {
    // ---- Idle, truck connects: a trip starts --------------------------------------------------

    @Test
    fun `idle and the truck connects - a trip starts`() {
        val result = IDLE.on(TruckConnection(true, T0))

        assertEquals(listOf(StartTrip(TripStartCause.TRUCK, truckSeen = true, T0)), result.effects)
        assertEquals(RECORDING, result.state)
    }

    @Test
    fun `Android Auto connecting never starts a trip`() {
        val result = IDLE.on(AndroidAutoConnection(true, T0))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertNull(result.state.trip)
    }

    // ---- Recording, truck disconnects ---------------------------------------------------------

    @Test
    fun `recording and the truck disconnects - the grace period starts`() {
        val result = RECORDING.on(TruckConnection(false, T0 + 10 * MINUTE))

        val expected = StartGrace(T0 + 10 * MINUTE, T0 + 10 * MINUTE + GRACE)
        assertEquals(listOf(expected), result.effects)
        assertEquals(Grace(expected.startedAtMs, expected.deadlineMs), result.state.trip?.grace)
    }

    @Test
    fun `the truck disconnects while Android Auto is connected - the trip carries on`() {
        val withAndroidAuto = RECORDING.on(AndroidAutoConnection(true, T0 + MINUTE)).state

        val result = withAndroidAuto.on(TruckConnection(false, T0 + 10 * MINUTE))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertNotNull(result.state.trip)
        assertNull(result.state.trip?.grace)
    }

    @Test
    fun `Android Auto disconnects after the truck already has - the grace period starts then`() {
        val heldByAndroidAuto =
            RECORDING
                .on(AndroidAutoConnection(true, T0 + MINUTE))
                .state
                .on(TruckConnection(false, T0 + 10 * MINUTE))
                .state

        val result = heldByAndroidAuto.on(AndroidAutoConnection(false, T0 + 20 * MINUTE))

        assertEquals(
            listOf(StartGrace(T0 + 20 * MINUTE, T0 + 20 * MINUTE + GRACE)),
            result.effects,
        )
    }

    @Test
    fun `Android Auto disconnecting while the truck is still connected changes nothing`() {
        val both = RECORDING.on(AndroidAutoConnection(true, T0 + MINUTE)).state

        val result = both.on(AndroidAutoConnection(false, T0 + 2 * MINUTE))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertNull(result.state.trip?.grace)
    }

    // ---- Grace, truck reconnects --------------------------------------------------------------

    @Test
    fun `in grace and the truck reconnects - the same trip carries on`() {
        val result = IN_GRACE.on(TruckConnection(true, GRACE_START + MINUTE))

        // CancelGrace and nothing else: in particular no second StartTrip.
        assertEquals(listOf(CancelGrace), result.effects)
        assertEquals(RECORDING, result.state)
    }

    @Test
    fun `in grace and Android Auto connects - the same trip carries on`() {
        val result = IN_GRACE.on(AndroidAutoConnection(true, GRACE_START + MINUTE))

        assertEquals(listOf(CancelGrace), result.effects)
        assertNull(result.state.trip?.grace)
    }

    // ---- Connect while recording, disconnect while idle: nothing ------------------------------

    @Test
    fun `a connect while recording does nothing`() {
        val result = RECORDING.on(TruckConnection(true, T0 + MINUTE))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(RECORDING, result.state)
    }

    @Test
    fun `a disconnect while idle does nothing`() {
        val result = IDLE.on(TruckConnection(false, T0))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(IDLE, result.state)
    }

    // ---- When to look again -------------------------------------------------------------------

    @Test
    fun `no timer is needed while idle or while the truck holds the trip open`() {
        assertNull(TripStateMachine.nextCheckAtMs(IDLE, RULES))
        assertNull(TripStateMachine.nextCheckAtMs(RECORDING, RULES))
        assertNull(TripStateMachine.nextCheckAtMs(HELD_OFF, RULES))
    }

    @Test
    fun `during the grace period the next look is at its deadline`() {
        assertEquals(GRACE_START + GRACE, TripStateMachine.nextCheckAtMs(IN_GRACE, RULES))
    }

    @Test
    fun `while a trip is recorded the next look is the parked limit after it last moved`() {
        val manual = MANUAL_NO_TRUCK.onParked(Moved(T0 + 20 * MINUTE)).state
        val truck = RECORDING.onParked(Moved(T0 + 20 * MINUTE)).state

        val due = T0 + 20 * MINUTE + PARKED_LIMIT
        assertEquals(due, TripStateMachine.nextCheckAtMs(manual, PARKED_RULES))
        assertEquals(due, TripStateMachine.nextCheckAtMs(truck, PARKED_RULES))
    }

    @Test
    fun `Android Auto being connected does not take that timer away`() {
        val plugged = MANUAL_NO_TRUCK.onParked(AndroidAutoConnection(true, T0 + MINUTE)).state

        assertEquals(T0 + PARKED_LIMIT, TripStateMachine.nextCheckAtMs(plugged, PARKED_RULES))
    }

    // ---- The settings the rules run with ------------------------------------------------------

    @Test
    fun `a negative grace period, tolerance or restart gap is refused`() {
        assertThrows(IllegalArgumentException::class.java) { TripRules(gracePeriodMs = -1) }
        assertThrows(IllegalArgumentException::class.java) {
            TripRules(gracePeriodMs = GRACE, lateCheckToleranceMs = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TripRules(gracePeriodMs = GRACE, restartGapLimitMs = -1)
        }
    }
}
