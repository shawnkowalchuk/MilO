package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.CancelGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.HoldOffAutoStart
import com.shawnkowalchuk.milo.core.trip.TripEffect.MarkTruckSeen
import com.shawnkowalchuk.milo.core.trip.TripEffect.ReleaseHoldOff
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartTrip
import com.shawnkowalchuk.milo.core.trip.TripEvent.AndroidAutoConnection
import com.shawnkowalchuk.milo.core.trip.TripEvent.Moved
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckAppeared
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckConnection
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val RANDOM_SEQUENCES = 3_000
private const val EVENTS_PER_SEQUENCE = 80

/**
 * Throws thousands of random event sequences at the trip rules: every kind of event, in any
 * order, repeated, minutes or hours apart, now and then with the clock set back. After every
 * single event the state must still make sense. The seed is fixed, so a failure can be replayed.
 */
class TripStateMachineRandomTest {
    @Test
    fun `no sequence of events can leave the state inconsistent`() {
        val random = Random(20261003)
        repeat(RANDOM_SEQUENCES) {
            var state = IDLE
            val stored = StoredSide()
            var now = T0
            repeat(EVENTS_PER_SEQUENCE) {
                now += randomTimeStep(random)
                val event = everyEventAt(now).let { it[random.nextInt(it.size)] }
                val result = state.on(event)

                stored.carryOut(result.effects)
                assertConsistent(result.state, now, "after $event from $state")
                assertEquals("stored trip after $event", stored.trip, result.state.trip?.stored())
                assertEquals(
                    "stored hold-off",
                    stored.heldOffSinceMs,
                    result.state.autoStartHeldOffSinceMs,
                )

                // Being killed and restored at this very moment, with the same readings, must
                // do exactly what a fresh look at the truck does: one set of rules, two doors.
                val look = result.state.on(TruckConnection(result.state.truckConnected, now))
                val restored =
                    TripStateMachine.restore(
                        storedTrip = result.state.trip,
                        lastRecordedAtMs = now,
                        autoStartHeldOffSinceMs = result.state.autoStartHeldOffSinceMs,
                        truckConnected = result.state.truckConnected,
                        androidAutoConnected = result.state.androidAutoConnected,
                        atMs = now,
                        rules = RULES,
                    )
                assertEquals("restore after $event", look.state, restored.state)
                assertEquals("restore after $event", look.effects, restored.effects)

                // And if the event itself had just read the truck, nothing is left to be done.
                val readTheTruck =
                    event !is Moved && event !is AndroidAutoConnection && event !is TruckAppeared
                if (readTheTruck) {
                    assertEquals("restore after $event", result.state, restored.state)
                    assertEquals("restore after $event", emptyList<TripEffect>(), restored.effects)
                }

                state = result.state
            }
        }
    }

    /** What must hold for every state the rules ever produce. [now] is the last event's time. */
    private fun assertConsistent(state: TripState, now: Long, context: String) {
        val trip = state.trip
        if (trip == null) {
            // Idle with the truck connected is only allowed while held off: otherwise a trip
            // should be running. This is the "missed trip" check.
            assertTrue(
                "idle with the truck connected $context",
                !state.truckConnected || state.autoStartHeldOff,
            )
        } else {
            val heldOpen = state.truckConnected || state.androidAutoConnected
            val shouldBeInGrace = trip.truckSeen && !heldOpen
            assertTrue(
                "waiting to be confirmed, yet the truck was seen $context",
                trip.confirmByMs == null || !trip.truckSeen,
            )
            // The one exception: a grace period that ran out long ago. That trip is over and
            // only waits for a reading of the truck to be closed, whatever has connected since.
            val grace = trip.grace
            val overAndWaiting =
                grace != null && now > grace.deadlineMs + RULES.lateCheckToleranceMs
            if (!overAndWaiting) {
                assertEquals("grace running $context", shouldBeInGrace, grace != null)
            }
            assertTrue(
                "truck connected but not marked seen $context",
                trip.truckSeen || !state.truckConnected,
            )
        }
        // A hold-off with the truck gone would swallow the next trip.
        assertTrue(
            "held off with the truck gone $context",
            !state.autoStartHeldOff || state.truckConnected,
        )
    }

    /**
     * Stands in for the database and the settings store: it carries out the effects and nothing
     * else. If it ends up agreeing with the rules after every step, then the effects really do
     * describe every stored change. It also refuses effects that make no sense, such as opening
     * a second trip.
     */
    private class StoredSide {
        var trip: ActiveTrip? = null
        var heldOffSinceMs: Long? = null

        fun carryOut(effects: List<TripEffect>) {
            for (effect in effects) {
                when (effect) {
                    is StartTrip -> {
                        assertNull("a second trip was opened", trip)
                        trip = ActiveTrip(effect.startedBy, effect.truckSeen, lastMovementAtMs = 0)
                    }

                    MarkTruckSeen -> {
                        assertEquals("truck marked seen twice", false, openTrip().truckSeen)
                        trip = openTrip().copy(truckSeen = true)
                    }

                    is StartGrace -> {
                        assertNull("grace started twice", openTrip().grace)
                        trip = openTrip().copy(grace = Grace(effect.startedAtMs, effect.deadlineMs))
                    }

                    CancelGrace -> {
                        assertNotNull("grace cancelled but not running", openTrip().grace)
                        trip = openTrip().copy(grace = null)
                    }

                    is EndTrip -> {
                        openTrip()
                        trip = null
                    }

                    is HoldOffAutoStart -> {
                        assertNotEquals(
                            "hold-off set again to the same time",
                            heldOffSinceMs,
                            effect.sinceMs,
                        )
                        heldOffSinceMs = effect.sinceMs
                    }

                    is ReleaseHoldOff -> {
                        assertNotNull("hold-off released but not set", heldOffSinceMs)
                        heldOffSinceMs = null
                    }
                }
            }
        }

        private fun openTrip(): ActiveTrip = checkNotNull(trip) { "an effect needed an open trip" }
    }

    /**
     * The stored columns of a trip: everything but the movement time and the confirmation
     * deadline, which are not stored.
     */
    private fun ActiveTrip.stored(): ActiveTrip = copy(lastMovementAtMs = 0, confirmByMs = null)

    /** Mostly seconds, sometimes longer than every timeout, now and then a clock set back. */
    private fun randomTimeStep(random: Random): Long = when (random.nextInt(10)) {
        0 -> -random.nextInt(5 * MINUTE.toInt()).toLong()
        1, 2 -> random.nextInt(2 * HOUR.toInt()).toLong()
        else -> random.nextInt(MINUTE.toInt()).toLong()
    }
}
