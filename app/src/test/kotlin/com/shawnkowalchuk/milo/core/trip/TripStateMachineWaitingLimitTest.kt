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
 * The wait beside a parked truck, continued from [TripStateMachineWaitingTest]: the limit after
 * which MilO stops watching, what starts the next trip then, and how a wait comes through a
 * restart of the process.
 */
class TripStateMachineWaitingLimitTest {
    private val later = WAITING_SINCE + HOUR

    private fun restore(
        parkedSinceMs: Long?,
        truckConnected: Boolean,
        atMs: Long,
        storedTrip: ActiveTrip? = null,
    ) = TripStateMachine.restore(
        storedTrip = storedTrip,
        lastRecordedAtMs = storedTrip?.let { atMs },
        autoStartHeldOffSinceMs = null,
        truckConnected = truckConnected,
        androidAutoConnected = false,
        atMs = atMs,
        rules = PARKED_RULES,
        parkedSinceMs = parkedSinceMs,
    )

    // ---- The limit on waiting --------------------------------------------------------------------------

    @Test
    fun `the timer is set for the end of the wait`() {
        assertEquals(
            WAITING_SINCE + WAITING_LIMIT_MS,
            TripStateMachine.nextCheckAtMs(WAITING, PARKED_RULES),
        )
    }

    @Test
    fun `the wait outlasts a night at home and a weekend`() {
        // By the times of 2026-10-06 (driven off at 07:18, parked at 17:08) a wait that begins
        // at 17:18 has to last 14 hours to see the next morning's drive, and from a Friday to
        // a Monday 62. A wait that had ended in between would leave a truck that is still
        // connected to drive off with nothing recording.
        val began = WAITING_SINCE
        for (hoursLater in listOf(12, 14, 24, 62)) {
            val stillWatching = WAITING.onParked(TruckConnection(true, began + hoursLater * HOUR))
            assertEquals("after $hoursLater h", emptyList<TripEffect>(), stillWatching.effects)
            assertTrue("after $hoursLater h", stillWatching.state.waitingToMove)
        }

        val mondayMorning = WAITING.onParked(Moved(began + 62 * HOUR))

        assertEquals(EndWaiting(WaitingEnd.MOVED), mondayMorning.effects.first())
        assertTrue(mondayMorning.state.trip != null)
    }

    @Test
    fun `after the limit MilO stops watching, and that reading starts no trip`() {
        val limit = WAITING_SINCE + WAITING_LIMIT_MS
        val early = WAITING.onParked(TruckConnection(true, limit - 1))
        assertEquals(emptyList<TripEffect>(), early.effects)

        val result = WAITING.onParked(TruckConnection(true, limit))

        assertEquals(listOf(EndWaiting(WaitingEnd.TIME_LIMIT)), result.effects)
        assertNull(result.state.trip)
        assertEquals(Parked(WAITING_SINCE, watching = false), result.state.parked)
        assertFalse(result.state.waitingToMove)
        // Nothing is left for the service to do.
        assertNull(TripStateMachine.nextCheckAtMs(result.state, PARKED_RULES))
    }

    @Test
    fun `movement seen after the limit, before any reading, still starts the trip`() {
        // The phone slept through the timer and wakes with the truck driving off.
        val limit = WAITING_SINCE + WAITING_LIMIT_MS

        val result = WAITING.onParked(Moved(limit + MINUTE))

        assertEquals(EndWaiting(WaitingEnd.MOVED), result.effects.first())
        assertTrue(result.state.trip != null)
    }

    @Test
    fun `the limit waits for a reading of the truck`() {
        // Like every timeout. Android Auto changing says nothing about the truck, and the
        // reading that does come may show it gone, which ends the wait another way.
        val late = WAITING_SINCE + WAITING_LIMIT_MS + HOUR

        val result = WAITING.onParked(AndroidAutoConnection(true, late))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertTrue(result.state.waitingToMove)
    }

    @Test
    fun `while automatic start is held off, movement starts nothing even beside a wait`() {
        // The rules never leave a wait and a hold-off standing together. If storage ever
        // handed both back, the hold-off is the one Shawn set by hand.
        val both = WAITING.copy(autoStartHeldOffSinceMs = WAITING_SINCE + MINUTE)

        val result = both.onParked(Moved(later))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertNull(result.state.trip)
    }

    @Test
    fun `once MilO has stopped watching, what starts the next trip`() {
        val limit = WAITING_SINCE + WAITING_LIMIT_MS
        val unwatched = WAITING.onParked(TruckConnection(true, limit)).state
        val next = limit + HOUR

        // A reconcile that still shows the truck: MilO was opened, or its process restarted.
        val reconciled = unwatched.onParked(TruckConnection(true, next))
        assertEquals(
            listOf(StartTrip(TripStartCause.TRUCK, truckSeen = true, next)),
            reconciled.effects,
        )
        // A new link, the companion callback, and Start.
        val linked = unwatched.onParked(TruckLinkConnected(next))
        assertEquals(
            listOf(StartTrip(TripStartCause.TRUCK, truckSeen = true, next)),
            linked.effects,
        )
        val appeared = unwatched.onParked(TruckAppeared(next))
        assertEquals(
            listOf(StartTrip(TripStartCause.TRUCK, truckSeen = false, next)),
            appeared.effects,
        )
        val pressed = unwatched.onParked(ManualStart(truckConnected = true, next))
        assertEquals(
            listOf(StartTrip(TripStartCause.MANUAL, truckSeen = true, next)),
            pressed.effects,
        )
        // Movement does not: nobody is watching.
        assertEquals(emptyList<TripEffect>(), unwatched.onParked(Moved(next)).effects)
        // And the truck seen gone leaves MilO idle, without a second word about the wait.
        val gone = unwatched.onParked(TruckConnection(false, next))
        assertEquals(emptyList<TripEffect>(), gone.effects)
        assertEquals(IDLE, gone.state)
    }

    // ---- The wait survives the process ----------------------------------------------------------------

    @Test
    fun `a restart while waiting finds the wait again, not a new trip`() {
        val result = restore(parkedSinceMs = WAITING_SINCE, truckConnected = true, atMs = later)

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(WAITING, result.state)
    }

    @Test
    fun `a restart while waiting with the truck gone ends the wait`() {
        val result = restore(parkedSinceMs = WAITING_SINCE, truckConnected = false, atMs = later)

        assertEquals(listOf(EndWaiting(WaitingEnd.TRUCK_DISCONNECTED)), result.effects)
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `a restart past the limit is a restart with the truck connected - a trip starts`() {
        // Nobody watched in between, so this cannot be told from a truck found connected at
        // boot. If it is still standing, the parked rule closes the trip and the wait resumes.
        val now = WAITING_SINCE + WAITING_LIMIT_MS + HOUR

        val result = restore(parkedSinceMs = WAITING_SINCE, truckConnected = true, atMs = now)

        assertEquals(
            listOf(
                EndWaiting(WaitingEnd.TIME_LIMIT),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, now),
            ),
            result.effects,
        )
    }

    @Test
    fun `a restart one millisecond inside the limit finds the wait, and at the limit a trip`() {
        val limit = WAITING_SINCE + WAITING_LIMIT_MS

        val inside = restore(parkedSinceMs = WAITING_SINCE, truckConnected = true, atMs = limit - 1)
        val atTheLimit = restore(parkedSinceMs = WAITING_SINCE, truckConnected = true, atMs = limit)

        assertEquals(emptyList<TripEffect>(), inside.effects)
        assertEquals(WAITING, inside.state)
        assertEquals(EndWaiting(WaitingEnd.TIME_LIMIT), atTheLimit.effects.first())
        assertTrue(atTheLimit.state.trip != null)
    }

    @Test
    fun `a restart that finds a trip open as well as a wait keeps the trip`() {
        val trip = checkNotNull(RECORDING.trip).copy(lastMovementAtMs = later)

        val result =
            restore(
                parkedSinceMs = WAITING_SINCE,
                truckConnected = true,
                atMs = later,
                storedTrip = trip,
            )

        assertEquals(listOf(EndWaiting(WaitingEnd.TRIP_OPEN)), result.effects)
        assertEquals(trip, result.state.trip)
        assertNull(result.state.parked)
    }

    @Test
    fun `no stored wait and the truck connected at a restart starts a trip, as it always did`() {
        // The proven rule, unchanged: with the rule on, that trip is closed if nothing moves.
        val result = restore(parkedSinceMs = null, truckConnected = true, atMs = later)
        assertEquals(
            listOf(StartTrip(TripStartCause.TRUCK, truckSeen = true, later)),
            result.effects,
        )

        val parked = result.state.onParked(TruckConnection(true, later + PARKED_LIMIT))
        assertEquals(
            listOf(EndTrip(TripEndReason.NO_MOVEMENT, later), StartWaiting(later + PARKED_LIMIT)),
            parked.effects,
        )
    }
}
