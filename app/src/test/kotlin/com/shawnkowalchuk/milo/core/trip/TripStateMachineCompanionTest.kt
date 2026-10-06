package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.EndTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.MarkTruckSeen
import com.shawnkowalchuk.milo.core.trip.TripEffect.ReleaseHoldOff
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartTrip
import com.shawnkowalchuk.milo.core.trip.TripEvent.AndroidAutoConnection
import com.shawnkowalchuk.milo.core.trip.TripEvent.ManualEnd
import com.shawnkowalchuk.milo.core.trip.TripEvent.ManualStart
import com.shawnkowalchuk.milo.core.trip.TripEvent.Moved
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckAppeared
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckConnection
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckLinkConnected
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val SECOND = 1_000L

/** When the trip in [UNCONFIRMED] must be confirmed by. */
private const val CONFIRM_BY = T0 + START_CONFIRMATION_MS

/** The companion callback opened a trip at [T0] and nothing has confirmed the truck yet. */
private val UNCONFIRMED = IDLE.on(TruckAppeared(T0)).state

/**
 * ADR-002, amendment 7: a trip opened by the companion "appeared" callback alone. The callback
 * starts recording at once, and the trip is a false start unless something trustworthy shows
 * the truck connected within 15 seconds.
 */
class TripStateMachineCompanionTest {
    @Test
    fun `the companion callback while idle starts a trip that waits to be confirmed`() {
        val result = IDLE.on(TruckAppeared(T0))

        // The truck is not marked as seen: nothing trustworthy has shown it yet.
        assertEquals(listOf(StartTrip(TripStartCause.TRUCK, truckSeen = false, T0)), result.effects)
        assertEquals(false, result.state.truckConnected)
        assertEquals(CONFIRM_BY, result.state.trip?.confirmByMs)
        assertEquals(CONFIRM_BY, TripStateMachine.nextCheckAtMs(result.state, RULES))
    }

    @Test
    fun `a link connect event for the truck confirms the trip`() {
        val result = UNCONFIRMED.on(TruckLinkConnected(T0 + 200))

        assertEquals(listOf(MarkTruckSeen), result.effects)
        assertNull(result.state.trip?.confirmByMs)
        assertNull(TripStateMachine.nextCheckAtMs(result.state, RULES))
    }

    @Test
    fun `the profile state read as connected confirms the trip`() {
        val result = UNCONFIRMED.on(TruckConnection(true, T0 + 8 * SECOND))

        assertEquals(listOf(MarkTruckSeen), result.effects)
        assertNull(result.state.trip?.confirmByMs)
    }

    @Test
    fun `a confirmed trip ends like any truck trip, through the grace period`() {
        val confirmed = UNCONFIRMED.on(TruckLinkConnected(T0 + 200)).state

        val result = confirmed.on(TruckConnection(false, T0 + HOUR))

        assertEquals(listOf(StartGrace(T0 + HOUR, T0 + HOUR + GRACE)), result.effects)
    }

    @Test
    fun `not confirmed within 15 seconds - a false start, ended at once with no grace period`() {
        // The timer set from nextCheckAtMs fires and reads the profile state: no truck.
        val result = UNCONFIRMED.on(TruckConnection(false, CONFIRM_BY))

        assertEquals(listOf(EndTrip(TripEndReason.FALSE_START, CONFIRM_BY)), result.effects)
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `the truck found connected when the confirmation timer fires saves the trip`() {
        val result = UNCONFIRMED.on(TruckConnection(true, CONFIRM_BY))

        assertEquals(listOf(MarkTruckSeen), result.effects)
    }

    @Test
    fun `before the deadline a reading of not connected proves nothing`() {
        // The profiles connect some seconds after the link. A reading taken too early must not
        // throw away a real start.
        val early = UNCONFIRMED.on(TruckConnection(false, T0 + 3 * SECOND))

        assertEquals(emptyList<TripEffect>(), early.effects)
        assertEquals(UNCONFIRMED, early.state)

        val confirmed = early.state.on(TruckConnection(true, T0 + 7 * SECOND))
        assertEquals(listOf(MarkTruckSeen), confirmed.effects)
    }

    @Test
    fun `a GPS fix or an Android Auto change after the deadline decides nothing`() {
        // Neither knows anything about the truck. The trip waits for the reading.
        val late = CONFIRM_BY + MINUTE

        val effects =
            UNCONFIRMED.onAll(
                Moved(late),
                AndroidAutoConnection(true, late),
                AndroidAutoConnection(false, late),
            )

        assertEquals(emptyList<TripEffect>(), effects)
    }

    @Test
    fun `a second companion callback does not push the deadline back`() {
        val result = UNCONFIRMED.on(TruckAppeared(T0 + 10 * SECOND))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(CONFIRM_BY, result.state.trip?.confirmByMs)
    }

    @Test
    fun `an unconfirmed trip is not ended for standing still`() {
        // It is not a manual trip: its way out is the confirmation, not the 30 minutes.
        val result = UNCONFIRMED.on(Moved(T0 + SECOND))

        assertEquals(CONFIRM_BY, TripStateMachine.nextCheckAtMs(result.state, RULES))
    }

    // ---- The moment for the trip-start sound --------------------------------------------------

    @Test
    fun `an unconfirmed start is not yet the moment for the sound, the confirmation is`() {
        // The sound says "connected to the truck". The callback alone does not show that.
        val appeared = IDLE.on(TruckAppeared(T0))
        assertEquals(false, appeared.tripReallyBegan(before = IDLE))

        val confirmed = appeared.state.on(TruckLinkConnected(T0 + 200))
        assertEquals(true, confirmed.tripReallyBegan(before = appeared.state))
    }

    @Test
    fun `a false start never has its moment`() {
        val result = UNCONFIRMED.on(TruckConnection(false, CONFIRM_BY))

        assertEquals(false, result.tripReallyBegan(before = UNCONFIRMED))
    }

    @Test
    fun `every other start is its own moment, and the truck joining a manual trip is not one`() {
        val byTruck = IDLE.on(TruckLinkConnected(T0))
        val byHand = IDLE.on(ManualStart(truckConnected = false, T0))
        val truckJoins = MANUAL_NO_TRUCK.on(TruckConnection(true, T0 + MINUTE))

        assertEquals(true, byTruck.tripReallyBegan(before = IDLE))
        assertEquals(true, byHand.tripReallyBegan(before = IDLE))
        // The sound played when Start was pressed.
        assertEquals(listOf(MarkTruckSeen), truckJoins.effects)
        assertEquals(false, truckJoins.tripReallyBegan(before = MANUAL_NO_TRUCK))
    }

    @Test
    fun `Start pressed after a false start's deadline is the moment for the manual trip`() {
        val late = CONFIRM_BY + 10 * SECOND

        val result = UNCONFIRMED.on(ManualStart(truckConnected = false, late))

        assertEquals(true, result.tripReallyBegan(before = UNCONFIRMED))
    }

    // ---- The callback while a trip is already open --------------------------------------------

    @Test
    fun `the callback does not mark the truck as seen in a manual trip`() {
        // Bluetooth failed to connect, so Shawn pressed Start. The head unit is in range, and
        // the callback fires on that alone. Believed, it would end this trip on a "disconnect"
        // two minutes after the next reading showed no truck.
        val result = MANUAL_NO_TRUCK.on(TruckAppeared(T0 + MINUTE))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(MANUAL_NO_TRUCK, result.state)
    }

    @Test
    fun `the callback changes nothing while a trip is recording or in grace`() {
        val recording = RECORDING.on(TruckAppeared(T0 + MINUTE))
        val inGrace = IN_GRACE.on(TruckAppeared(GRACE_START + MINUTE))

        assertEquals(emptyList<TripEffect>(), recording.effects + inGrace.effects)
        assertEquals(RECORDING, recording.state)
        // The grace period is cancelled by a signal that can be trusted, or by the reading the
        // timer takes at the deadline.
        assertEquals(IN_GRACE, inGrace.state)
    }

    @Test
    fun `with a trip open the callback releases an old hold-off and leaves the trip alone`() {
        // End with the truck connected, then Start again by hand: the trip is open and the
        // hold-off is still set. An "appeared" a minute or more later shows the hold-off is
        // out of date. It must not also drop "the truck is connected", or the trip would start
        // its grace period on a disconnect nobody saw.
        val heldOff = RECORDING.copy(autoStartHeldOffSinceMs = T0)

        val result = heldOff.on(TruckAppeared(T0 + 2 * MINUTE))

        assertEquals(listOf(ReleaseHoldOff(HoldOffRelease.NEW_LINK)), result.effects)
        assertEquals(RECORDING, result.state)
    }

    @Test
    fun `with a trip open the callback inside the 60 seconds changes nothing at all`() {
        val heldOff = RECORDING.copy(autoStartHeldOffSinceMs = T0)

        val result = heldOff.on(TruckAppeared(T0 + 30 * SECOND))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(heldOff, result.state)
    }

    // ---- The buttons during the wait ----------------------------------------------------------

    @Test
    fun `End pressed while unconfirmed ends the trip by hand`() {
        val result = UNCONFIRMED.on(ManualEnd(truckConnected = false, T0 + 5 * SECOND))

        assertEquals(listOf(EndTrip(TripEndReason.MANUAL, T0 + 5 * SECOND)), result.effects)
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `Start pressed after the deadline replaces the false start with a manual trip`() {
        val late = CONFIRM_BY + 10 * SECOND

        val result = UNCONFIRMED.on(ManualStart(truckConnected = false, late))

        assertEquals(
            listOf(
                EndTrip(TripEndReason.FALSE_START, late),
                StartTrip(TripStartCause.MANUAL, truckSeen = false, late),
            ),
            result.effects,
        )
    }

    // ---- After a restart ----------------------------------------------------------------------

    @Test
    fun `a stored companion start gets its confirmation deadline back`() {
        // The deadline is not stored. It follows from the stored columns: the truck started
        // the trip, and the truck was never seen in it.
        assertEquals(
            T0 + START_CONFIRMATION_MS,
            confirmByMsFor(TripStartCause.TRUCK, truckSeen = false, startedAtMs = T0, RULES),
        )
        assertNull(confirmByMsFor(TripStartCause.TRUCK, truckSeen = true, startedAtMs = T0, RULES))
        assertNull(
            confirmByMsFor(TripStartCause.MANUAL, truckSeen = false, startedAtMs = T0, RULES),
        )
    }

    @Test
    fun `a companion start restored after its deadline with no truck is a false start`() {
        val unconfirmed =
            ActiveTrip(
                TripStartCause.TRUCK,
                truckSeen = false,
                lastMovementAtMs = T0,
                confirmByMs = T0 + START_CONFIRMATION_MS,
            )

        val result =
            TripStateMachine.restore(
                storedTrip = unconfirmed,
                lastRecordedAtMs = T0,
                autoStartHeldOffSinceMs = null,
                truckConnected = false,
                androidAutoConnected = false,
                atMs = T0 + MINUTE,
                rules = RULES,
            )

        assertEquals(listOf(EndTrip(TripEndReason.FALSE_START, T0 + MINUTE)), result.effects)
        assertEquals(IDLE, result.state)
    }
}
