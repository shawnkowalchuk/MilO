package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.HoldOffAutoStart
import com.shawnkowalchuk.milo.core.trip.TripEffect.ReleaseHoldOff
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartTrip
import com.shawnkowalchuk.milo.core.trip.TripEvent.AndroidAutoConnection
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
 * ADR-002, amendment 5: what releases the hold-off after End was pressed with the truck still
 * connected. Whichever comes first: the truck seen disconnected, a new link more than 60 seconds
 * after the press, or 12 hours. The first of these is also covered in
 * [TripStateMachineManualTest], where the hold-off is set.
 */
class TripStateMachineHoldOffTest {
    // ---- A new link ---------------------------------------------------------------------------

    @Test
    fun `a link connect more than 60 seconds after End releases the hold-off and starts a trip`() {
        // The disconnect was never seen: the app was dead. This morning's connect event proves
        // it happened, because a new link can only form after the old one dropped.
        val nextMorning = HELD_OFF_SINCE + 15 * HOUR

        val result = HELD_OFF.on(TruckLinkConnected(nextMorning))

        assertEquals(
            listOf(
                ReleaseHoldOff(HoldOffRelease.NEW_LINK),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, nextMorning),
            ),
            result.effects,
        )
        assertNull(result.state.autoStartHeldOffSinceMs)
    }

    @Test
    fun `a link connect within 60 seconds of End is a late duplicate and releases nothing`() {
        // The same connection that was up when End was pressed, reported again by a second
        // delivery path. Releasing on it would restart the trip Shawn just ended.
        val result = HELD_OFF.on(TruckLinkConnected(HELD_OFF_SINCE + 60 * SECOND))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(HELD_OFF, result.state)
    }

    @Test
    fun `one millisecond past the 60 seconds the link connect counts`() {
        val result = HELD_OFF.on(TruckLinkConnected(HELD_OFF_SINCE + 60 * SECOND + 1))

        assertEquals(ReleaseHoldOff(HoldOffRelease.NEW_LINK), result.effects.first())
    }

    @Test
    fun `the companion callback releases the hold-off too, and its trip must be confirmed`() {
        val nextMorning = HELD_OFF_SINCE + 15 * HOUR

        val result = HELD_OFF.on(TruckAppeared(nextMorning))

        assertEquals(
            listOf(
                ReleaseHoldOff(HoldOffRelease.NEW_LINK),
                StartTrip(TripStartCause.TRUCK, truckSeen = false, nextMorning),
            ),
            result.effects,
        )
        // The old link is known to have dropped; the new one is not confirmed yet.
        assertEquals(false, result.state.truckConnected)
        assertEquals(nextMorning + START_CONFIRMATION_MS, result.state.trip?.confirmByMs)
    }

    @Test
    fun `the companion callback within 60 seconds of End changes nothing`() {
        val result = HELD_OFF.on(TruckAppeared(HELD_OFF_SINCE + 30 * SECOND))

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(HELD_OFF, result.state)
    }

    @Test
    fun `a reading of connected is not a new link and releases nothing before the time limit`() {
        // A reconcile at launch, or a profile broadcast: the truck is connected, which is
        // exactly what the hold-off already knows.
        val result = HELD_OFF.on(TruckConnection(true, HELD_OFF_SINCE + 11 * HOUR))

        assertEquals(emptyList<TripEffect>(), result.effects)
    }

    // ---- The time limit -----------------------------------------------------------------------

    @Test
    fun `after 12 hours a reading of the truck connected starts a trip again`() {
        val after = HELD_OFF_SINCE + HOLD_OFF_LIMIT_MS

        val result = HELD_OFF.on(TruckConnection(true, after))

        assertEquals(
            listOf(
                ReleaseHoldOff(HoldOffRelease.TIME_LIMIT),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, after),
            ),
            result.effects,
        )
    }

    @Test
    fun `one millisecond before the 12 hours the hold-off still holds`() {
        val result = HELD_OFF.on(TruckConnection(true, HELD_OFF_SINCE + HOLD_OFF_LIMIT_MS - 1))

        assertEquals(emptyList<TripEffect>(), result.effects)
    }

    @Test
    fun `the time limit waits for a reading of the truck`() {
        // Releasing starts a trip in the same step. That must rest on a reading of the truck,
        // not on what was believed 12 hours ago.
        val after = HELD_OFF_SINCE + 13 * HOUR

        val androidAuto = HELD_OFF.on(AndroidAutoConnection(true, after))
        val fix = HELD_OFF.on(Moved(after))

        assertEquals(emptyList<TripEffect>(), androidAuto.effects + fix.effects)
        assertNull(androidAuto.state.trip)
    }

    @Test
    fun `End pressed again after 12 hours with the truck connected still starts no trip`() {
        // The press is a reading, so the old hold-off is past its limit. But End must never be
        // the press that starts a trip.
        val after = HELD_OFF_SINCE + 13 * HOUR

        val result = HELD_OFF.on(ManualEnd(truckConnected = true, after))

        assertNull(result.state.trip)
        assertEquals(listOf(HoldOffAutoStart(after)), result.effects)
    }

    @Test
    fun `after a restart, held off for 12 hours and the truck connected - a trip starts`() {
        // Neither the disconnect nor the next connect was seen: the app was dead for both. The
        // reconcile at launch finds the truck connected, and the time limit lets the trip start.
        val nextMorning = HELD_OFF_SINCE + HOLD_OFF_LIMIT_MS

        val result = restoreHeldOff(truckConnected = true, atMs = nextMorning)

        assertEquals(
            listOf(
                ReleaseHoldOff(HoldOffRelease.TIME_LIMIT),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, nextMorning),
            ),
            result.effects,
        )
    }

    // ---- The truck seen disconnected ----------------------------------------------------------

    @Test
    fun `whichever release comes first wins - a disconnect at once, then the connect starts`() {
        val effects =
            HELD_OFF.onAll(
                TruckConnection(false, HELD_OFF_SINCE + 5 * SECOND),
                TruckLinkConnected(HELD_OFF_SINCE + 20 * SECOND),
            )

        // The 60 seconds apply to a connect event that is the only evidence. Here the
        // disconnect was seen, so the connect 15 seconds later starts a trip as usual.
        assertEquals(
            listOf(
                ReleaseHoldOff(HoldOffRelease.TRUCK_SEEN_DISCONNECTED),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, HELD_OFF_SINCE + 20 * SECOND),
            ),
            effects,
        )
    }

    @Test
    fun `End pressed again holds off from the later press`() {
        // Ten minutes later, truck still connected. The 60 seconds and the 12 hours are now
        // counted from this press.
        val again = HELD_OFF_SINCE + 10 * MINUTE

        val result = HELD_OFF.on(ManualEnd(truckConnected = true, again))

        assertEquals(listOf(HoldOffAutoStart(again)), result.effects)
        assertEquals(again, result.state.autoStartHeldOffSinceMs)
    }

    // ---- The hold-off survives the process too -------------------------------------------------

    @Test
    fun `held off and the truck still connected - no trip starts`() {
        val result = restoreHeldOff(truckConnected = true, atMs = HELD_OFF_SINCE + HOUR)

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(HELD_OFF, result.state)
    }

    @Test
    fun `held off and the truck gone - the hold-off is released`() {
        val result = restoreHeldOff(truckConnected = false, atMs = HELD_OFF_SINCE + HOUR)

        assertEquals(
            listOf(ReleaseHoldOff(HoldOffRelease.TRUCK_SEEN_DISCONNECTED)),
            result.effects,
        )
        assertEquals(IDLE, result.state)
    }

    /** A process start with no trip stored and the hold-off of [HELD_OFF] in the settings. */
    private fun restoreHeldOff(truckConnected: Boolean, atMs: Long) = TripStateMachine.restore(
        storedTrip = null,
        lastRecordedAtMs = null,
        autoStartHeldOffSinceMs = HELD_OFF_SINCE,
        truckConnected = truckConnected,
        androidAutoConnected = false,
        atMs = atMs,
        rules = RULES,
    )
}
