package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.EndTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndWaiting
import com.shawnkowalchuk.milo.core.trip.TripEffect.HoldOffAutoStart
import com.shawnkowalchuk.milo.core.trip.TripEffect.ReleaseHoldOff
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartWaiting
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckConnection
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val RANDOM_SEQUENCES = 3_000
private const val EVENTS_PER_SEQUENCE = 80

/**
 * [TripStateMachineRandomTest] again, with the parked rule on: thousands of random sequences of
 * every kind of event, and after each event the state must still make sense, with a truck that
 * may now also be parked and waited for. The wait is given three hours here, so that the random
 * steps (up to two hours) reach its limit. The seed is fixed, so a failure can be replayed.
 */
class TripStateMachineParkedRandomTest {
    private val rules =
        TripRules(gracePeriodMs = GRACE, parkedLimitMs = PARKED_LIMIT, waitingLimitMs = 3 * HOUR)

    @Test
    fun `no sequence of events can leave the state inconsistent with the parked rule on`() {
        val random = Random(20261006)
        var waits = 0
        var tripsFromParked = 0
        repeat(RANDOM_SEQUENCES) {
            var state = IDLE
            val stored = StoredSide()
            var now = T0
            repeat(EVENTS_PER_SEQUENCE) {
                now += randomTimeStep(random)
                val events = everyEventAt(now) + TripEvent.MoveTakenBack(now - HOUR, now)
                val event = events[random.nextInt(events.size)]
                val result = TripStateMachine.step(state, event, rules)
                val context = "after $event from $state"

                stored.carryOut(result.effects)
                assertConsistent(result.state, context)
                assertEquals(
                    "stored hold-off $context",
                    stored.heldOff,
                    result.state.autoStartHeldOff,
                )
                assertEquals("stored trip $context", stored.tripOpen, result.state.trip != null)
                assertEquals(
                    "stored wait $context",
                    stored.waitingSinceMs,
                    result.state.parked?.takeIf { it.watching }?.sinceMs,
                )
                assertRestartAgrees(result.state, now, context)

                waits += result.effects.count { it is StartWaiting }
                tripsFromParked += result.effects.count { it is StartTrip && it.fromParked }
                state = result.state
            }
        }
        // The sequences really did park trucks and drive them off again.
        assertTrue("only $waits waits began", waits > 1_000)
        assertTrue("only $tripsFromParked trips started from a parked truck", tripsFromParked > 100)
    }

    /**
     * Being killed and restored at this very moment must do what a fresh look at the truck
     * does. One case differs on purpose: a wait that has run past its limit unseen is a wait no
     * longer watched for a live process, and a restart with the truck connected for a new one.
     */
    private fun assertRestartAgrees(state: TripState, now: Long, context: String) {
        // Storage holds a wait only while it is watched: "no longer watched" lives in memory.
        val parked = state.parked?.takeIf { it.watching }
        if (parked != null && now - parked.sinceMs >= rules.waitingLimitMs) return
        val look = TripStateMachine.step(state, TruckConnection(state.truckConnected, now), rules)
        val restored =
            TripStateMachine.restore(
                storedTrip = state.trip,
                lastRecordedAtMs = now,
                autoStartHeldOffSinceMs = state.autoStartHeldOffSinceMs,
                truckConnected = state.truckConnected,
                androidAutoConnected = state.androidAutoConnected,
                atMs = now,
                rules = rules,
                parkedSinceMs = parked?.sinceMs,
            )
        assertEquals("restore $context", look.state, restored.state)
        assertEquals("restore $context", look.effects, restored.effects)
    }

    private fun assertConsistent(state: TripState, context: String) {
        val parked = state.parked
        assertFalse("a trip open beside a wait $context", state.trip != null && parked != null)
        if (parked != null) {
            // A wait with nothing connected could never end, and Android Auto counts only
            // while MilO is watching; a wait while held off would start a trip on movement
            // that the hold-off forbids.
            val held = state.truckConnected || (parked.watching && state.androidAutoConnected)
            assertTrue("parked with nothing connected $context", held)
            assertFalse("parked while held off $context", state.autoStartHeldOff)
        }
        if (state.trip == null) {
            // The "missed trip" check: idle beside a connected truck needs a reason.
            val reason = state.autoStartHeldOff || parked != null
            assertTrue("idle with the truck connected $context", !state.truckConnected || reason)
        }
        assertTrue(
            "held off with the truck gone $context",
            !state.autoStartHeldOff || state.truckConnected,
        )
    }

    /**
     * Stands in for storage: it carries out the effects and refuses those that make no sense,
     * such as a second wait, or a trip opened while the wait is still stored.
     */
    private class StoredSide {
        var tripOpen = false
        var heldOff = false
        var waitingSinceMs: Long? = null

        fun carryOut(effects: List<TripEffect>) {
            for (effect in effects) {
                when (effect) {
                    is StartTrip -> {
                        assertFalse("a second trip was opened", tripOpen)
                        assertNull("a trip was opened beside a stored wait", waitingSinceMs)
                        tripOpen = true
                    }

                    is EndTrip -> {
                        assertTrue("a trip was ended, but none is open", tripOpen)
                        tripOpen = false
                    }

                    is StartWaiting -> {
                        assertFalse("a wait began beside an open trip", tripOpen)
                        assertFalse("a wait began while held off", heldOff)
                        assertNull("a second wait began", waitingSinceMs)
                        waitingSinceMs = effect.sinceMs
                    }

                    is EndWaiting -> {
                        assertNotNull("a wait ended, but none is stored", waitingSinceMs)
                        waitingSinceMs = null
                    }

                    is HoldOffAutoStart -> {
                        assertNull("held off beside a stored wait", waitingSinceMs)
                        heldOff = true
                    }

                    is ReleaseHoldOff -> heldOff = false

                    TripEffect.MarkTruckSeen, is TripEffect.StartGrace, TripEffect.CancelGrace ->
                        assertTrue("$effect needs an open trip", tripOpen)
                }
            }
        }
    }

    /** Mostly seconds, sometimes longer than every timeout, now and then a clock set back. */
    private fun randomTimeStep(random: Random): Long = when (random.nextInt(10)) {
        0 -> -random.nextInt(5 * MINUTE.toInt()).toLong()
        1, 2 -> random.nextInt(2 * HOUR.toInt()).toLong()
        else -> random.nextInt(MINUTE.toInt()).toLong()
    }
}
