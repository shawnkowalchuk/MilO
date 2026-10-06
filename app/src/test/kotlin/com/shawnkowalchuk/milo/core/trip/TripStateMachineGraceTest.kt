package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.CancelGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartTrip
import com.shawnkowalchuk.milo.core.trip.TripEvent.AndroidAutoConnection
import com.shawnkowalchuk.milo.core.trip.TripEvent.ManualEnd
import com.shawnkowalchuk.milo.core.trip.TripEvent.ManualStart
import com.shawnkowalchuk.milo.core.trip.TripEvent.Moved
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** When the grace period of [IN_GRACE] runs out. */
private const val DEADLINE = GRACE_START + GRACE

/**
 * How a grace period ends (ADR-002: "Grace ends", and "when the timer ends, the profile state is
 * read again before the trip is closed"). Three things are pinned here:
 * - only an event that brings a fresh reading of the truck closes a trip whose time has run out;
 * - a reading of "connected" that arrives a little late still saves the trip, but one that
 *   arrives long after does not revive it;
 * - Start pressed while a trip is waiting is not swallowed.
 */
class TripStateMachineGraceTest {
    private val expired: List<TripEffect> =
        listOf(EndTrip(TripEndReason.GRACE_EXPIRED, GRACE_START))

    // ---- The timer fires on time --------------------------------------------------------------

    @Test
    fun `the grace period ends - the trip is closed where the truck was found gone`() {
        // The timer fired, the connection was read again, and the truck is still gone.
        val result = IN_GRACE.on(TruckConnection(false, DEADLINE))

        // Cut at the start of the grace period, not at its end.
        assertEquals(expired, result.effects)
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `one millisecond before the deadline the trip is still open`() {
        val result = IN_GRACE.on(TruckConnection(false, DEADLINE - 1))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(IN_GRACE, result.state)
    }

    @Test
    fun `the timer fires but the truck is in fact connected - the trip is not closed`() {
        // The disconnect was a false alarm, or the reconnect event was lost. Reading the
        // connection again when the timer fires is what catches it.
        val result = IN_GRACE.on(TruckConnection(true, DEADLINE))

        assertEquals(listOf(CancelGrace), result.effects)
        assertEquals(RECORDING, result.state)
    }

    @Test
    fun `with a grace period of zero a disconnect closes the trip at once`() {
        val noGrace = TripRules(gracePeriodMs = 0)

        val result = TripStateMachine.step(RECORDING, TruckConnection(false, T0 + MINUTE), noGrace)

        assertEquals(
            listOf(
                StartGrace(T0 + MINUTE, T0 + MINUTE),
                EndTrip(TripEndReason.GRACE_EXPIRED, T0 + MINUTE),
            ),
            result.effects,
        )
        assertEquals(IDLE, result.state)
    }

    // ---- Only a fresh reading of the truck closes a trip --------------------------------------

    @Test
    fun `an overdue grace period is closed only by a fresh reading of the truck`() {
        val overdue = DEADLINE + 5 * MINUTE
        val knowNothingNewAboutTheTruck =
            listOf(
                Moved(overdue),
                AndroidAutoConnection(false, overdue),
                AndroidAutoConnection(true, overdue),
            )

        for (event in knowNothingNewAboutTheTruck) {
            val result = IN_GRACE.on(event)

            assertEquals("$event", emptyList<TripEffect>(), result.effects)
            // The caller is told to read the truck at once: the time to look is already past.
            assertEquals("$event", DEADLINE, TripStateMachine.nextCheckAtMs(result.state, RULES))
            // That reading then closes the trip.
            val read = result.state.on(TruckConnection(false, overdue + 1))
            assertEquals("$event", expired, read.effects)
        }
        assertEquals(expired, IN_GRACE.on(TruckConnection(false, overdue)).effects)
        // End finds the trip already over: it is closed as expired, not stretched to the press.
        assertEquals(expired, IN_GRACE.on(ManualEnd(truckConnected = false, overdue)).effects)
    }

    @Test
    fun `a GPS fix that gets in before the timer's reading does not cut the drive in two`() {
        // A false disconnect while driving: the reconnect event was lost and the truck is there
        // all along. The grace timer runs a second late, and a GPS fix is handled first.
        val effects =
            RECORDING.onAll(
                TruckConnection(false, GRACE_START),
                Moved(DEADLINE + 1_000),
                TruckConnection(true, DEADLINE + 2_000),
            )

        // One trip. Closing it on the fix would have ended it at GRACE_START and started a
        // second one, with the two minutes in between belonging to neither.
        assertEquals(listOf(StartGrace(GRACE_START, DEADLINE), CancelGrace), effects)
    }

    @Test
    fun `a connect and a disconnect delivered 50 ms apart do not cut the new trip short`() {
        // The truck is connected; the pair of events arrived as connect, then disconnect.
        val effects =
            IDLE.onAll(
                TruckConnection(true, T0),
                TruckConnection(false, T0 + 50),
                Moved(T0 + MINUTE),
                Moved(T0 + 50 + GRACE + 1_000),
                TruckConnection(true, T0 + 50 + GRACE + 1_050),
            )

        assertEquals(
            listOf(
                StartTrip(TripStartCause.TRUCK, truckSeen = true, T0),
                StartGrace(T0 + 50, T0 + 50 + GRACE),
                CancelGrace,
            ),
            effects,
        )
    }

    @Test
    fun `with a grace period of zero, Android Auto dropping waits for a reading of the truck`() {
        val noGrace = TripRules(gracePeriodMs = 0)
        val heldByAndroidAuto =
            TripState(
                trip = RECORDING.trip,
                truckConnected = false,
                androidAutoConnected = true,
            )

        val result =
            TripStateMachine.step(heldByAndroidAuto, AndroidAutoConnection(false, T0), noGrace)

        // The grace period starts and is already over, but what is known about the truck is old.
        assertEquals(listOf(StartGrace(T0, T0)), result.effects)
        assertEquals(T0, TripStateMachine.nextCheckAtMs(result.state, noGrace))
    }

    // ---- A reading that arrives late ----------------------------------------------------------

    @Test
    fun `the reading of a late timer still saves the trip, up to the tolerance`() {
        val result = IN_GRACE.on(TruckConnection(true, DEADLINE + LATE_CHECK_TOLERANCE_MS))

        assertEquals(listOf(CancelGrace), result.effects)
        assertEquals(RECORDING, result.state)
    }

    @Test
    fun `a reconnect long after the deadline closes the old trip and starts a new one`() {
        // The process was frozen, so the timer never fired. Next morning the truck connects.
        // Carrying the trip on would make yesterday's drive and today's one trip.
        val nextMorning = GRACE_START + 15 * HOUR
        val newTrip = StartTrip(TripStartCause.TRUCK, truckSeen = true, nextMorning)

        val result = IN_GRACE.on(TruckConnection(true, nextMorning))

        assertEquals(expired + newTrip, result.effects)
        assertNull(result.state.trip?.grace)

        // The same one millisecond past the tolerance.
        val justLate = DEADLINE + LATE_CHECK_TOLERANCE_MS + 1
        assertEquals(
            expired + StartTrip(TripStartCause.TRUCK, truckSeen = true, justLate),
            IN_GRACE.on(TruckConnection(true, justLate)).effects,
        )
    }

    @Test
    fun `Android Auto connecting long after the deadline does not revive the trip`() {
        val nextMorning = GRACE_START + 15 * HOUR

        val plugged = IN_GRACE.on(AndroidAutoConnection(true, nextMorning))
        assertEquals(emptyList<TripEffect>(), plugged.effects)

        // The reading of the truck then closes yesterday's trip. Android Auto starts nothing.
        val read = plugged.state.on(TruckConnection(false, nextMorning + 1))
        assertEquals(expired, read.effects)
        assertNull(read.state.trip)
    }

    @Test
    fun `End pressed long after the deadline with the truck connected starts no new trip`() {
        val nextMorning = GRACE_START + 15 * HOUR

        val result = IN_GRACE.on(ManualEnd(truckConnected = true, nextMorning))

        assertEquals(expired + TripEffect.SetAutoStartHeldOff(true), result.effects)
        assertEquals(HELD_OFF, result.state)
    }

    // ---- Start pressed while a trip is waiting ------------------------------------------------

    @Test
    fun `Start pressed during the grace period ends the waiting trip and starts a manual one`() {
        // Bluetooth did not come back, so Shawn presses Start 90 s into the grace period.
        // Swallowed, the press would leave nothing recording 30 s later.
        val pressedAt = GRACE_START + 90_000

        val result = IN_GRACE.on(ManualStart(truckConnected = false, pressedAt))

        assertEquals(
            listOf(
                EndTrip(TripEndReason.REPLACED_BY_MANUAL_START, GRACE_START),
                StartTrip(TripStartCause.MANUAL, truckSeen = false, pressedAt),
            ),
            result.effects,
        )
        // The new trip outlives the old deadline: it ends by the manual rules now.
        val later = result.state.on(TruckConnection(false, DEADLINE))
        assertEquals(emptyList<TripEffect>(), later.effects)
        assertEquals(TripStartCause.MANUAL, later.state.trip?.startedBy)
    }

    @Test
    fun `Start pressed during the grace period with the truck back carries the same trip on`() {
        // The reconnect was missed; the reading taken at the press shows the truck is here.
        val result = IN_GRACE.on(ManualStart(truckConnected = true, GRACE_START + MINUTE))

        assertEquals(listOf(CancelGrace), result.effects)
        assertEquals(RECORDING, result.state)
    }

    @Test
    fun `Start pressed after the grace period ran out closes the old trip and starts a new one`() {
        // The timer was missed, so the old trip is still open when Shawn presses Start. The
        // press must not be swallowed by a trip that is already over.
        val overdue = DEADLINE + 5 * MINUTE

        val result = IN_GRACE.on(ManualStart(truckConnected = false, overdue))

        assertEquals(
            expired + StartTrip(TripStartCause.MANUAL, truckSeen = false, overdue),
            result.effects,
        )
    }
}
