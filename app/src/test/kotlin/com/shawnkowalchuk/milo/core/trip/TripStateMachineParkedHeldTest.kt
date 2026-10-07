package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.CancelGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndWaiting
import com.shawnkowalchuk.milo.core.trip.TripEffect.MarkTruckSeen
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartWaiting
import com.shawnkowalchuk.milo.core.trip.TripEvent.AndroidAutoConnection
import com.shawnkowalchuk.milo.core.trip.TripEvent.Moved
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckConnection
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckLinkConnected
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parked rule where a second connection is in play (ADR-002, amendment 28): Android Auto on
 * a cable, which holds a wait as it holds a trip, and a new Bluetooth link that arrives while a
 * trip has stood too long. Each case here was a drive that went unrecorded, or an outcome that
 * depended on how often an event was delivered, as the rule was first built.
 */
class TripStateMachineParkedHeldTest {
    /** Bluetooth dropped two minutes into [RECORDING], with the phone on the cable. */
    private val onTheCable =
        RECORDING
            .onAllParked(
                AndroidAutoConnection(true, T0 + MINUTE),
                TruckConnection(false, T0 + 2 * MINUTE),
            ).state

    /** The trip of [onTheCable] closed by the parked rule: MilO waits, held by Android Auto. */
    private val waitingOnTheCable = onTheCable.onParked(TruckConnection(false, T0 + PARKED_LIMIT))

    private val later = T0 + HOUR

    // ---- Android Auto holds the wait ---------------------------------------------------------------

    @Test
    fun `a parked trip that only Android Auto held open ends, and MilO waits there too`() {
        // Before the parked rule this trip stayed open through the stop, and the drive after
        // it was recorded. It still must be.
        assertNull(onTheCable.trip?.grace)

        assertEquals(
            listOf(EndTrip(TripEndReason.NO_MOVEMENT, T0), StartWaiting(T0 + PARKED_LIMIT)),
            waitingOnTheCable.effects,
        )
        assertTrue(waitingOnTheCable.state.waitingToMove)
        assertFalse(waitingOnTheCable.state.truckConnected)
    }

    @Test
    fun `the truck moving again starts a trip that Android Auto goes on holding open`() {
        val moved = waitingOnTheCable.state.onParked(Moved(later))

        assertEquals(
            listOf(
                EndWaiting(WaitingEnd.MOVED),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, later, fromParked = true),
            ),
            moved.effects,
        )
        // No grace period: Android Auto holds it, as it held the trip before the stop.
        val read = moved.state.onParked(TruckConnection(false, later + MINUTE))
        assertEquals(emptyList<TripEffect>(), read.effects)
        // And it ends the way such a trip always did, when Android Auto goes too.
        val unplugged = read.state.onParked(AndroidAutoConnection(false, later + 2 * MINUTE))
        assertEquals(
            listOf(StartGrace(later + 2 * MINUTE, later + 2 * MINUTE + GRACE)),
            unplugged.effects,
        )
    }

    @Test
    fun `Android Auto disconnecting ends a wait that it alone was holding`() {
        val result = waitingOnTheCable.state.onParked(AndroidAutoConnection(false, later))

        assertEquals(listOf(EndWaiting(WaitingEnd.TRUCK_DISCONNECTED)), result.effects)
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `a reading of the truck still gone changes nothing while Android Auto holds the wait`() {
        // The once-a-minute check, every minute of the stop.
        val result = waitingOnTheCable.state.onParked(TruckConnection(false, later))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(waitingOnTheCable.state, result.state)
    }

    @Test
    fun `beside a truck on Bluetooth and on the cable, the wait ends when both have gone`() {
        val both = WAITING.onParked(AndroidAutoConnection(true, later)).state

        // The truck is switched off: whichever of the two reports first, the other still holds.
        val bluetoothFirst = both.onParked(TruckConnection(false, later + MINUTE))
        assertEquals(emptyList<TripEffect>(), bluetoothFirst.effects)
        val thenTheCable =
            bluetoothFirst.state.onParked(AndroidAutoConnection(false, later + MINUTE + 500))
        assertEquals(listOf(EndWaiting(WaitingEnd.TRUCK_DISCONNECTED)), thenTheCable.effects)

        val cableFirst = both.onParked(AndroidAutoConnection(false, later + MINUTE))
        assertEquals(emptyList<TripEffect>(), cableFirst.effects)
        val thenBluetooth = cableFirst.state.onParked(TruckConnection(false, later + MINUTE + 500))
        assertEquals(listOf(EndWaiting(WaitingEnd.TRUCK_DISCONNECTED)), thenBluetooth.effects)
        assertEquals(IDLE, thenBluetooth.state)
    }

    @Test
    fun `once MilO has stopped watching, Android Auto holds nothing`() {
        // Nothing reports Android Auto then, so its last word must not keep "connected and no
        // longer watched" on the screens after the truck has gone.
        val limit = WAITING_SINCE + WAITING_LIMIT_MS
        val unwatched =
            WAITING
                .onAllParked(AndroidAutoConnection(true, later), TruckConnection(true, limit))
                .state
        assertEquals(Parked(WAITING_SINCE, watching = false), unwatched.parked)

        val gone = unwatched.onParked(TruckConnection(false, limit + HOUR))

        assertEquals(emptyList<TripEffect>(), gone.effects)
        assertNull(gone.state.parked)
    }

    @Test
    fun `at its limit a wait that Android Auto alone was holding is simply over`() {
        val since = checkNotNull(waitingOnTheCable.state.parked).sinceMs

        val result =
            waitingOnTheCable.state.onParked(TruckConnection(false, since + WAITING_LIMIT_MS))

        assertEquals(listOf(EndWaiting(WaitingEnd.TIME_LIMIT)), result.effects)
        assertNull(result.state.parked)
        assertNull(result.state.trip)
    }

    // ---- A new link finds a trip that has stood too long ---------------------------------------------

    @Test
    fun `a new link to a trip that stood too long closes it and starts the next in one step`() {
        // Stood nine minutes, disconnected, and reconnected ninety seconds later.
        val gone = T0 + 9 * MINUTE
        val back = gone + 90_000
        val inGrace = RECORDING.onParked(TruckConnection(false, gone)).state

        val result = inGrace.onParked(TruckLinkConnected(back))

        assertEquals(
            listOf(
                CancelGrace,
                EndTrip(TripEndReason.NO_MOVEMENT, T0),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, back),
            ),
            result.effects,
        )
        assertNull(result.state.parked)
        // A connect, so the trip-start sound plays, as on every connect.
        assertTrue(result.tripReallyBegan(before = inGrace))

        // Both Bluetooth receivers deliver the broadcast. The second one finds nothing to do.
        val again = result.state.onParked(TruckLinkConnected(back + 200))
        assertEquals(emptyList<TripEffect>(), again.effects)
        assertEquals(result.state, again.state)
    }

    @Test
    fun `a new link to a forgotten manual trip does the same`() {
        val result = MANUAL_NO_TRUCK.onParked(TruckLinkConnected(later))

        assertEquals(
            listOf(
                MarkTruckSeen,
                EndTrip(TripEndReason.NO_MOVEMENT, T0),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, later),
            ),
            result.effects,
        )
    }

    @Test
    fun `a reading that finds the truck back, which is no new link, still parks the trip`() {
        // A reconcile or the timer's own reading: nothing connected just now, so MilO waits.
        val gone = T0 + 9 * MINUTE
        val inGrace = RECORDING.onParked(TruckConnection(false, gone)).state

        val result = inGrace.onParked(TruckConnection(true, gone + 90_000))

        assertEquals(StartWaiting(gone + 90_000), result.effects.last())
    }

    @Test
    fun `with the parked rule on, any event given twice does nothing the second time`() {
        // One state is left out on purpose: a wait at its limit. The reading that brings the
        // limit stops the watch, and the next reading of a connected truck starts a trip (a
        // reconcile, as designed); the service's own timers are kept from being that reading
        // by the trip controller.
        val states =
            listOf(IDLE, RECORDING, IN_GRACE, MANUAL_NO_TRUCK, HELD_OFF, WAITING) +
                listOf(onTheCable, waitingOnTheCable.state)
        val times = listOf(T0 + MINUTE, GRACE_START + GRACE, T0 + 2 * HOUR)
        for (state in states) {
            for (event in times.flatMap(::everyEventAt)) {
                val first = state.onParked(event)
                val second = first.state.onParked(event)

                assertEquals("$event twice from $state", emptyList<TripEffect>(), second.effects)
                assertEquals("$event twice from $state", first.state, second.state)
            }
        }
    }
}
