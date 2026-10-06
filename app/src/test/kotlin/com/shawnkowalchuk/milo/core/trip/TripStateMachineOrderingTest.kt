package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.CancelGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.HoldOffAutoStart
import com.shawnkowalchuk.milo.core.trip.TripEffect.MarkTruckSeen
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartTrip
import com.shawnkowalchuk.milo.core.trip.TripEvent.AndroidAutoConnection
import com.shawnkowalchuk.milo.core.trip.TripEvent.ManualEnd
import com.shawnkowalchuk.milo.core.trip.TripEvent.ManualStart
import com.shawnkowalchuk.milo.core.trip.TripEvent.Moved
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * ADR-002: Bluetooth events can be dropped, repeated or delivered in reverse order, and none of
 * that may corrupt a trip. This file walks through those orderings one at a time.
 * [TripStateMachineRandomTest] then throws thousands of random sequences at the rules.
 */
class TripStateMachineOrderingTest {
    // ---- Repeated events ----------------------------------------------------------------------

    @Test
    fun `the same connect from three triggers starts one trip`() {
        // The companion service, the ACL broadcast and the hands-free broadcast all report one
        // physical connection.
        val effects =
            IDLE.onAll(
                TruckConnection(true, T0),
                TruckConnection(true, T0 + 300),
                TruckConnection(true, T0 + 4_000),
            )

        assertEquals(listOf(StartTrip(TripStartCause.TRUCK, truckSeen = true, T0)), effects)
    }

    @Test
    fun `a repeated disconnect does not push the grace deadline back`() {
        val once = RECORDING.on(TruckConnection(false, GRACE_START)).state

        val twice = once.on(TruckConnection(false, GRACE_START + MINUTE))

        assertEquals(emptyList<TripEffect>(), twice.effects)
        assertEquals(GRACE_START + GRACE, twice.state.trip?.grace?.deadlineMs)
    }

    @Test
    fun `End pressed twice ends one trip`() {
        val effects =
            RECORDING.onAll(
                ManualEnd(truckConnected = true, T0 + HOUR),
                ManualEnd(truckConnected = true, T0 + HOUR + 200),
            )

        // One trip is ended. The hold-off is counted from the later press.
        assertEquals(
            listOf(
                EndTrip(TripEndReason.MANUAL, T0 + HOUR),
                HoldOffAutoStart(T0 + HOUR),
                HoldOffAutoStart(T0 + HOUR + 200),
            ),
            effects,
        )
    }

    @Test
    fun `Start pressed on the phone and on the car screen starts one trip`() {
        val effects =
            IDLE.onAll(
                ManualStart(truckConnected = false, T0),
                ManualStart(truckConnected = false, T0 + 500),
            )

        assertEquals(listOf(StartTrip(TripStartCause.MANUAL, truckSeen = false, T0)), effects)
    }

    @Test
    fun `any event given twice does nothing the second time`() {
        val states = listOf(IDLE, RECORDING, IN_GRACE, MANUAL_NO_TRUCK, HELD_OFF)
        val times = listOf(T0 + MINUTE, GRACE_START + GRACE, T0 + 2 * HOUR)
        for (state in states) {
            for (event in times.flatMap(::everyEventAt)) {
                val first = state.on(event)
                val second = first.state.on(event)

                assertEquals("$event twice from $state", emptyList<TripEffect>(), second.effects)
                assertEquals("$event twice from $state", first.state, second.state)
            }
        }
    }

    // ---- Missing events -----------------------------------------------------------------------

    @Test
    fun `a missed connect is made good by the next reading`() {
        // No connect event arrived. The reconcile at app launch reads the connection.
        val result = IDLE.on(TruckConnection(true, T0 + 5 * MINUTE))

        assertEquals(1, result.effects.filterIsInstance<StartTrip>().size)
    }

    @Test
    fun `a missed disconnect is made good by the next reading`() {
        // No disconnect event arrived. A later reading finds the truck gone, and the trip then
        // ends the normal way.
        val found = RECORDING.on(TruckConnection(false, T0 + 3 * HOUR))
        val closed = found.state.on(TruckConnection(false, T0 + 3 * HOUR + GRACE))

        assertEquals(listOf(StartGrace(T0 + 3 * HOUR, T0 + 3 * HOUR + GRACE)), found.effects)
        assertEquals(listOf(EndTrip(TripEndReason.GRACE_EXPIRED, T0 + 3 * HOUR)), closed.effects)
    }

    @Test
    fun `a missed reconnect during grace is caught when the timer reads the connection again`() {
        val result = IN_GRACE.on(TruckConnection(true, GRACE_START + GRACE))

        assertEquals(listOf(CancelGrace), result.effects)
        assertNotNull(result.state.trip)
    }

    @Test
    fun `a missed grace timer is made good by the next reading of the truck`() {
        // The process was frozen and the timer never fired. Hours later something reads the
        // truck's connection and finds it still gone.
        val result = IN_GRACE.on(TruckConnection(false, GRACE_START + 5 * HOUR))

        assertEquals(listOf(EndTrip(TripEndReason.GRACE_EXPIRED, GRACE_START)), result.effects)
    }

    @Test
    fun `a missed grace timer and then a connect - the old trip is closed and a new one starts`() {
        // The same, but hours later the truck connects. The old trip ended when its grace
        // period ran out; the connect belongs to a new drive.
        val later = GRACE_START + 5 * HOUR

        val result = IN_GRACE.on(TruckConnection(true, later))

        assertEquals(
            listOf(
                EndTrip(TripEndReason.GRACE_EXPIRED, GRACE_START),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, later),
            ),
            result.effects,
        )
    }

    @Test
    fun `a missed grace timer is not made good by a GPS fix, which knows nothing of the truck`() {
        val result = IN_GRACE.on(Moved(GRACE_START + 5 * HOUR))

        assertEquals(emptyList<TripEffect>(), result.effects)
        // The overdue deadline is still what the caller is told to act on.
        assertEquals(GRACE_START + GRACE, TripStateMachine.nextCheckAtMs(result.state, RULES))
    }

    @Test
    fun `a missed disconnect with the process alive - a reconnect carries the old trip on`() {
        // Nothing told the rules the truck ever left, so to them it is one long trip. This is
        // the cost of a lost disconnect while the process lives; the rules cannot do better
        // without the event. (After a restart they can: see TripStateMachineRestoreTest.)
        val result = RECORDING.on(TruckConnection(true, T0 + 20 * HOUR))

        assertEquals(emptyList<TripEffect>(), result.effects)
    }

    // ---- Reversed events ----------------------------------------------------------------------

    @Test
    fun `disconnect then connect while idle starts exactly one trip`() {
        // A quick connect-and-drop delivered backwards. The trip starts; the 15-second
        // confirmation in the service then reads the truth and sends a disconnect.
        val effects = IDLE.onAll(TruckConnection(false, T0), TruckConnection(true, T0 + 100))

        assertEquals(listOf(StartTrip(TripStartCause.TRUCK, truckSeen = true, T0 + 100)), effects)
    }

    @Test
    fun `a drop and reconnect delivered backwards costs nothing once the timer reads the truth`() {
        // Real order: disconnect, connect. Delivered: connect, disconnect.
        val delivered =
            RECORDING
                .on(TruckConnection(true, GRACE_START))
                .state
                .on(TruckConnection(false, GRACE_START + 50))
        assertEquals(1, delivered.effects.filterIsInstance<StartGrace>().size)

        // The grace timer fires, the connection is read: the truck is there.
        val checked = delivered.state.on(TruckConnection(true, GRACE_START + 50 + GRACE))
        assertEquals(listOf(CancelGrace), checked.effects)
        assertEquals(RECORDING, checked.state)
    }

    @Test
    fun `the truck and Android Auto dropping in either order start one grace period`() {
        val both = RECORDING.on(AndroidAutoConnection(true, T0 + MINUTE)).state

        val truckFirst =
            both.onAll(
                TruckConnection(false, GRACE_START),
                AndroidAutoConnection(false, GRACE_START + 100),
            )
        val androidAutoFirst =
            both.onAll(
                AndroidAutoConnection(false, GRACE_START),
                TruckConnection(false, GRACE_START + 100),
            )

        val expected = listOf(StartGrace(GRACE_START + 100, GRACE_START + 100 + GRACE))
        assertEquals(expected, truckFirst)
        assertEquals(expected, androidAutoFirst)
    }

    @Test
    fun `End crossing with the truck disconnecting leaves no hold-off behind in either order`() {
        // A hold-off left set with the truck gone would swallow the next trip.
        val endFirst =
            RECORDING
                .on(ManualEnd(truckConnected = true, T0 + HOUR))
                .state
                .on(TruckConnection(false, T0 + HOUR + 100))
                .state
        val disconnectFirst =
            RECORDING
                .on(TruckConnection(false, T0 + HOUR))
                .state
                .on(ManualEnd(truckConnected = false, T0 + HOUR + 100))
                .state

        assertEquals(IDLE, endFirst)
        assertEquals(IDLE, disconnectFirst)
    }

    @Test
    fun `Start crossing with the truck connecting gives one trip in either order`() {
        val startFirst =
            IDLE.onAll(ManualStart(truckConnected = false, T0), TruckConnection(true, T0 + 100))
        val connectFirst =
            IDLE.onAll(TruckConnection(true, T0), ManualStart(truckConnected = true, T0 + 100))

        assertEquals(
            listOf(StartTrip(TripStartCause.MANUAL, truckSeen = false, T0), MarkTruckSeen),
            startFirst,
        )
        assertEquals(listOf(StartTrip(TripStartCause.TRUCK, truckSeen = true, T0)), connectFirst)
    }
}
