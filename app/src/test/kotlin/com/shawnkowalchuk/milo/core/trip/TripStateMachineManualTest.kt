package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.CancelGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.HoldOffAutoStart
import com.shawnkowalchuk.milo.core.trip.TripEffect.MarkTruckSeen
import com.shawnkowalchuk.milo.core.trip.TripEffect.ReleaseHoldOff
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartTrip
import com.shawnkowalchuk.milo.core.trip.TripEvent.AndroidAutoConnection
import com.shawnkowalchuk.milo.core.trip.TripEvent.ManualEnd
import com.shawnkowalchuk.milo.core.trip.TripEvent.ManualStart
import com.shawnkowalchuk.milo.core.trip.TripEvent.Moved
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rules of ADR-002's "Trip rules" that involve the Start and End buttons: manual start,
 * manual end with its hold-off, and the no-movement guard for a manual trip with no truck.
 */
class TripStateMachineManualTest {
    // ---- Manual start -------------------------------------------------------------------------

    @Test
    fun `manual start while idle starts a trip`() {
        val result = IDLE.on(ManualStart(truckConnected = false, T0))

        assertEquals(
            listOf(StartTrip(TripStartCause.MANUAL, truckSeen = false, T0)),
            result.effects,
        )
        assertEquals(MANUAL_NO_TRUCK, result.state)
    }

    @Test
    fun `manual start while a trip is open does nothing`() {
        val result = RECORDING.on(ManualStart(truckConnected = true, T0 + MINUTE))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(RECORDING, result.state)
    }

    @Test
    fun `the truck connects during a manual trip - from then on it is an automatic trip`() {
        val joined = MANUAL_NO_TRUCK.on(TruckConnection(true, T0 + 5 * MINUTE))

        assertEquals(listOf(MarkTruckSeen), joined.effects)
        assertEquals(TripStartCause.MANUAL, joined.state.trip?.startedBy)

        // It now ends on disconnect plus grace...
        val disconnected = joined.state.on(TruckConnection(false, T0 + 30 * MINUTE))
        assertEquals(
            listOf(StartGrace(T0 + 30 * MINUTE, T0 + 30 * MINUTE + GRACE)),
            disconnected.effects,
        )

        // ...and no longer on standing still, however long.
        val parked = joined.state.on(Moved(T0 + 5 * MINUTE)).state.on(Moved(T0 + 9 * HOUR))
        assertEquals(emptyList<TripEffect>(), parked.effects)
        assertNotNull(parked.state.trip)
    }

    @Test
    fun `manual start with the truck connected starts a trip that ends like an automatic one`() {
        // Only reachable while automatic start is held off; otherwise the truck would have
        // started the trip itself.
        val result = HELD_OFF.on(ManualStart(truckConnected = true, T0 + 2 * HOUR))

        assertEquals(
            listOf(StartTrip(TripStartCause.MANUAL, truckSeen = true, T0 + 2 * HOUR)),
            result.effects,
        )
        assertNull(TripStateMachine.nextCheckAtMs(result.state, RULES))
    }

    // ---- Manual end while the truck is still connected ----------------------------------------

    @Test
    fun `manual end with the truck connected ends the trip and holds automatic start off`() {
        val result = RECORDING.on(ManualEnd(truckConnected = true, T0 + HOUR))

        assertEquals(
            listOf(EndTrip(TripEndReason.MANUAL, T0 + HOUR), HoldOffAutoStart(T0 + HOUR)),
            result.effects,
        )
        assertEquals(HELD_OFF, result.state)
    }

    @Test
    fun `while held off, seeing the truck still connected does not start a trip`() {
        // This is the reconcile at the next app launch, the very thing the hold-off is for.
        val result = HELD_OFF.on(TruckConnection(true, T0 + 2 * HOUR))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertNull(result.state.trip)
    }

    @Test
    fun `the hold-off is released when the truck is seen gone, and the next connect starts`() {
        val released = HELD_OFF.on(TruckConnection(false, T0 + 2 * HOUR))
        assertEquals(
            listOf(ReleaseHoldOff(HoldOffRelease.TRUCK_SEEN_DISCONNECTED)),
            released.effects,
        )
        assertEquals(IDLE, released.state)

        val next = released.state.on(TruckConnection(true, T0 + 3 * HOUR))
        assertEquals(
            listOf(StartTrip(TripStartCause.TRUCK, truckSeen = true, T0 + 3 * HOUR)),
            next.effects,
        )
    }

    @Test
    fun `Android Auto connecting or disconnecting does not release the hold-off`() {
        val connected = HELD_OFF.on(AndroidAutoConnection(true, T0 + 2 * HOUR))
        val disconnected = connected.state.on(AndroidAutoConnection(false, T0 + 3 * HOUR))

        assertEquals(emptyList<TripEffect>(), connected.effects + disconnected.effects)
        assertEquals(HELD_OFF, disconnected.state)
    }

    @Test
    fun `manual end believes the fresh reading, not the old one`() {
        // The disconnect was missed, so the state still says "connected". Holding off now would
        // swallow the next real trip. The reading taken at the button press says the truck is gone.
        val result = RECORDING.on(ManualEnd(truckConnected = false, T0 + HOUR))

        // The truck is found gone and the trip is ended in the same moment. No hold-off.
        assertEquals(
            listOf(
                StartGrace(T0 + HOUR, T0 + HOUR + GRACE),
                EndTrip(TripEndReason.MANUAL, T0 + HOUR),
            ),
            result.effects,
        )
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `manual end during the grace period ends the trip where the truck was found gone`() {
        val result = IN_GRACE.on(ManualEnd(truckConnected = false, GRACE_START + MINUTE))

        assertEquals(listOf(EndTrip(TripEndReason.MANUAL, GRACE_START)), result.effects)
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `manual end during the grace period with the truck back ends the trip now`() {
        // The reconnect was missed; the reading at the button press shows the truck is here.
        val result = IN_GRACE.on(ManualEnd(truckConnected = true, GRACE_START + MINUTE))

        assertEquals(
            listOf(
                CancelGrace,
                EndTrip(TripEndReason.MANUAL, GRACE_START + MINUTE),
                HoldOffAutoStart(GRACE_START + MINUTE),
            ),
            result.effects,
        )
    }

    @Test
    fun `End pressed on a forgotten manual trip closes it where it last moved, not at the press`() {
        // Five hours in, the timer was missed and Shawn presses End. The trip was over long ago.
        val result = MANUAL_NO_TRUCK.on(ManualEnd(truckConnected = false, T0 + 5 * HOUR))

        assertEquals(listOf(EndTrip(TripEndReason.NO_MOVEMENT, T0)), result.effects)
    }

    @Test
    fun `manual end while idle with no truck does nothing`() {
        val result = IDLE.on(ManualEnd(truckConnected = false, T0))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `manual end while idle with the truck connected holds off and never starts a trip`() {
        // The connect was missed, so no trip is open, and the reading taken at the button press
        // is the first the rules hear of the truck. End must not be the press that starts a trip.
        val result = IDLE.on(ManualEnd(truckConnected = true, HELD_OFF_SINCE))

        assertEquals(listOf(HoldOffAutoStart(HELD_OFF_SINCE)), result.effects)
        assertEquals(HELD_OFF, result.state)
    }

    // ---- A manual trip with no truck connected ------------------------------------------------

    @Test
    fun `a manual trip with no truck ends on End Trip, with no hold-off`() {
        val driving = MANUAL_NO_TRUCK.on(Moved(T0 + 55 * MINUTE)).state

        val result = driving.on(ManualEnd(truckConnected = false, T0 + HOUR))

        assertEquals(listOf(EndTrip(TripEndReason.MANUAL, T0 + HOUR)), result.effects)
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `a manual trip with no truck ends after 30 minutes without movement`() {
        val result = MANUAL_NO_TRUCK.on(TruckConnection(false, T0 + 30 * MINUTE))

        // Closed where it last moved (here it never did, so at its start), not 30 minutes later.
        assertEquals(listOf(EndTrip(TripEndReason.NO_MOVEMENT, T0)), result.effects)
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `movement restarts the 30 minutes`() {
        val moving = MANUAL_NO_TRUCK.on(Moved(T0 + 20 * MINUTE)).state

        val stillOpen = moving.on(TruckConnection(false, T0 + 49 * MINUTE))
        assertEquals(emptyList<TripEffect>(), stillOpen.effects)

        val closed = moving.on(TruckConnection(false, T0 + 50 * MINUTE))
        assertEquals(
            listOf(EndTrip(TripEndReason.NO_MOVEMENT, T0 + 20 * MINUTE)),
            closed.effects,
        )
    }

    @Test
    fun `a late report of older movement does not move the clock back`() {
        val moving =
            MANUAL_NO_TRUCK
                .on(Moved(T0 + 20 * MINUTE))
                .state
                .on(Moved(T0 + 10 * MINUTE))
                .state

        assertEquals(T0 + 20 * MINUTE, moving.trip?.lastMovementAtMs)
    }

    @Test
    fun `a truck trip is never ended for standing still`() {
        // Decided at kickoff: a long stop with the engine running stays inside one trip.
        val result = RECORDING.on(TruckConnection(true, T0 + 9 * HOUR))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(RECORDING, result.state)
    }

    @Test
    fun `the no-movement guard waits while Android Auto is connected`() {
        val plugged = MANUAL_NO_TRUCK.on(AndroidAutoConnection(true, T0 + MINUTE)).state

        val held = plugged.on(TruckConnection(false, T0 + 2 * HOUR))
        assertEquals(emptyList<TripEffect>(), held.effects)

        // Unplugged, the guard applies again, counted from the last movement. The limit is
        // long past, but unplugging says nothing about the truck: the trip waits for a reading.
        val unplugged = held.state.on(AndroidAutoConnection(false, T0 + 3 * HOUR))
        assertEquals(emptyList<TripEffect>(), unplugged.effects)
        assertEquals(T0 + 30 * MINUTE, TripStateMachine.nextCheckAtMs(unplugged.state, RULES))

        val read = unplugged.state.on(TruckConnection(false, T0 + 3 * HOUR + 1))
        assertEquals(listOf(EndTrip(TripEndReason.NO_MOVEMENT, T0)), read.effects)
    }

    @Test
    fun `the truck found connected when the no-movement timer fires joins the trip instead`() {
        // The connect event was missed. The timer's reading finds the truck, so this is not a
        // forgotten trip, and it must not be closed on the old belief that no truck is there.
        val result = MANUAL_NO_TRUCK.on(TruckConnection(true, T0 + 30 * MINUTE))

        assertEquals(listOf(MarkTruckSeen), result.effects)
        assertNotNull(result.state.trip)
    }

    @Test
    fun `Android Auto disconnecting does not put a manual trip with no truck into grace`() {
        val plugged = MANUAL_NO_TRUCK.on(AndroidAutoConnection(true, T0 + MINUTE)).state

        val result = plugged.on(AndroidAutoConnection(false, T0 + 2 * MINUTE))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertNull(result.state.trip?.grace)
    }
}
