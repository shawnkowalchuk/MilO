package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.CancelGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.HoldOffAutoStart
import com.shawnkowalchuk.milo.core.trip.TripEffect.MarkTruckSeen
import com.shawnkowalchuk.milo.core.trip.TripEffect.ReleaseHoldOff
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartTrip
import com.shawnkowalchuk.milo.core.trip.TripEvent.ManualEnd
import com.shawnkowalchuk.milo.core.trip.TripEvent.Moved
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckAppeared
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckConnection
import com.shawnkowalchuk.milo.core.trip.TripEvent.TruckLinkConnected
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val SECOND = 1_000L

/**
 * The start and the stop that were seen in the truck on 2026-10-06 (FINDINGS_LOG, "First day in
 * the truck"), run through the rules as the app runs them: with the parked rule on.
 *
 * Every other test of these rules (a connect, the grace period, the hold-off, the companion
 * start, the awkward orderings) runs with [RULES], which leaves the parked rule off so that a
 * truck may stand for hours in them. The app never runs that way. So each sequence here is given
 * to both sets of rules, and they must agree step for step: as long as the truck has moved
 * within the parked limit, the parked rule changes nothing about how a trip starts from a
 * connect or ends on a disconnect.
 */
class TripStateMachineProvenDayTest {
    /**
     * Runs [events] through the rules with and without the parked rule, which must ask for the
     * same things and reach the same state after every one of them.
     */
    private fun TripState.onBoth(vararg events: TripEvent): TripTransition {
        var plain = this
        var parked = this
        val effects = mutableListOf<TripEffect>()
        for (event in events) {
            val without = TripStateMachine.step(plain, event, RULES)
            val with = TripStateMachine.step(parked, event, PARKED_RULES)
            assertEquals("what $event asks for", without.effects, with.effects)
            assertEquals("the state after $event", without.state, with.state)
            effects += with.effects
            plain = without.state
            parked = with.state
        }
        return TripTransition(parked, effects)
    }

    /** A drive as the service reports it: movement every 5 seconds, a reading every minute. */
    private fun driving(fromMs: Long, minutes: Int): Array<TripEvent> = (1..minutes * 12)
        .flatMap { tick ->
            val atMs = fromMs + tick * 5 * SECOND
            listOfNotNull(Moved(atMs), TruckConnection(true, atMs).takeIf { tick % 12 == 0 })
        }.toTypedArray()

    /** The truck connects at [T0]: the companion callback first, then the link broadcast. */
    private fun connected(): TripTransition = IDLE.onBoth(TruckAppeared(T0), TruckLinkConnected(T0))

    @Test
    fun `the truck connects - one trip starts, however often the connect is reported`() {
        val result =
            IDLE.onBoth(
                TruckAppeared(T0),
                TruckLinkConnected(T0),
                // The trip service's own receiver delivers the broadcast again, the companion
                // service is created, and a profile connects a few seconds after the link.
                TruckLinkConnected(T0 + 300),
                TruckAppeared(T0 + 400),
                TruckConnection(true, T0 + 3 * SECOND),
            )

        val started = StartTrip(TripStartCause.TRUCK, truckSeen = false, T0)
        assertEquals(listOf(started, MarkTruckSeen), result.effects)
        assertNull(result.state.parked)
    }

    @Test
    fun `the morning drive ends at the disconnect after its grace period, and nothing waits`() {
        // 07:18 to 08:01 on the day: 43 minutes, then the truck is left at work.
        val gone = T0 + 43 * MINUTE + 2 * SECOND
        val drive = connected().state.onBoth(*driving(T0, minutes = 43))
        assertEquals(emptyList<TripEffect>(), drive.effects)

        // The companion service, both receivers and the two profiles all report it.
        val left =
            drive.state.onBoth(
                TruckConnection(false, gone),
                TruckConnection(false, gone + 200),
                TruckConnection(false, gone + SECOND),
                TruckConnection(false, gone + SECOND + 300),
            )
        assertEquals(listOf(StartGrace(gone, gone + GRACE)), left.effects)

        val timer = left.state.onBoth(TruckConnection(false, gone + GRACE))

        // Closed where the truck was found gone. Idle: no wait beside a truck that has gone.
        assertEquals(listOf(EndTrip(TripEndReason.GRACE_EXPIRED, gone)), timer.effects)
        assertEquals(IDLE, timer.state)
        assertNull(TripStateMachine.nextCheckAtMs(timer.state, PARKED_RULES))
    }

    @Test
    fun `a drop of two seconds - the same trip carries on`() {
        // 12:05 on the day: the link dropped and came straight back.
        val dropped = T0 + MINUTE + 2 * SECOND
        val drive = connected().state.onBoth(*driving(T0, minutes = 1))

        val result =
            drive.state.onBoth(
                TruckConnection(false, dropped),
                TruckLinkConnected(dropped + 2 * SECOND),
                TruckLinkConnected(dropped + 2 * SECOND + 300),
                *driving(dropped + 2 * SECOND, minutes = 7),
            )

        assertEquals(listOf(StartGrace(dropped, dropped + GRACE), CancelGrace), result.effects)
        assertEquals(TripStartCause.TRUCK, result.state.trip?.startedBy)
    }

    @Test
    fun `nine minutes at a standstill with the truck connected - still one trip`() {
        val stopped = T0 + 5 * MINUTE
        val drive = connected().state.onBoth(*driving(T0, minutes = 5))

        val readings = (1..9).map { TruckConnection(true, stopped + it * MINUTE) }
        val result = drive.state.onBoth(*readings.toTypedArray(), *driving(stopped + 9 * MINUTE, 5))

        assertEquals(emptyList<TripEffect>(), result.effects)
    }

    @Test
    fun `a reconnect long after the grace period ran out - the old trip ends, a new one starts`() {
        // The process was frozen through the grace period, and the truck comes back.
        val gone = T0 + 5 * MINUTE + 2 * SECOND
        val back = gone + 20 * MINUTE
        val left = connected().state.onBoth(*driving(T0, minutes = 5), TruckConnection(false, gone))

        val result = left.state.onBoth(TruckLinkConnected(back), TruckLinkConnected(back + 300))

        assertEquals(
            listOf(
                EndTrip(TripEndReason.GRACE_EXPIRED, gone),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, back),
            ),
            result.effects,
        )
    }

    @Test
    fun `End with the truck connected holds automatic start off until the truck is seen gone`() {
        // 18:03 on the day.
        val pressed = T0 + 5 * MINUTE + 30 * SECOND
        val drive = connected().state.onBoth(*driving(T0, minutes = 5))

        val ended =
            drive.state.onBoth(
                ManualEnd(truckConnected = true, pressed),
                // Neither a late duplicate of the connect nor a reading starts a trip.
                TruckLinkConnected(pressed + 30 * SECOND),
                TruckConnection(true, pressed + MINUTE),
                TruckConnection(true, pressed + HOUR),
            )
        assertEquals(
            listOf(EndTrip(TripEndReason.MANUAL, pressed), HoldOffAutoStart(pressed)),
            ended.effects,
        )
        assertNull(ended.state.parked)

        val nextDrive =
            ended.state.onBoth(
                TruckConnection(false, pressed + 2 * HOUR),
                TruckLinkConnected(pressed + 13 * HOUR),
            )

        assertEquals(
            listOf(
                ReleaseHoldOff(HoldOffRelease.TRUCK_SEEN_DISCONNECTED),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, pressed + 13 * HOUR),
            ),
            nextDrive.effects,
        )
    }

    @Test
    fun `a companion start that nothing confirms - a false start, as before`() {
        val result =
            IDLE.onBoth(
                TruckAppeared(T0),
                // The profiles are not up yet: this reading proves nothing.
                TruckConnection(false, T0 + 5 * SECOND),
                TruckConnection(false, T0 + START_CONFIRMATION_MS),
            )

        assertEquals(
            listOf(
                StartTrip(TripStartCause.TRUCK, truckSeen = false, T0),
                EndTrip(TripEndReason.FALSE_START, T0 + START_CONFIRMATION_MS),
            ),
            result.effects,
        )
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `a restart in the middle of a drive - the same trip carries on under both sets of rules`() {
        val now = T0 + 20 * MINUTE
        val stored = ActiveTrip(TripStartCause.TRUCK, truckSeen = true, lastMovementAtMs = now)

        for (rules in listOf(RULES, PARKED_RULES)) {
            val carriedOn =
                TripStateMachine.restore(
                    storedTrip = stored,
                    lastRecordedAtMs = now,
                    autoStartHeldOffSinceMs = null,
                    truckConnected = true,
                    androidAutoConnected = false,
                    atMs = now + 4 * SECOND,
                    rules = rules,
                )
            val fresh =
                TripStateMachine.restore(
                    storedTrip = null,
                    lastRecordedAtMs = null,
                    autoStartHeldOffSinceMs = null,
                    truckConnected = true,
                    androidAutoConnected = false,
                    atMs = now,
                    rules = rules,
                )

            assertEquals(emptyList<TripEffect>(), carriedOn.effects)
            assertEquals(stored, carriedOn.state.trip)
            // And with nothing stored, a connected truck starts a trip: the proven restart.
            val started = StartTrip(TripStartCause.TRUCK, truckSeen = true, now)
            assertEquals(listOf(started), fresh.effects)
        }
    }
}
