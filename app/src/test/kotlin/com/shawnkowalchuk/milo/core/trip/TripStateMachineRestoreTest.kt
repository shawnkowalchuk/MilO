package com.shawnkowalchuk.milo.core.trip

import com.shawnkowalchuk.milo.core.trip.TripEffect.CancelGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.EndTrip
import com.shawnkowalchuk.milo.core.trip.TripEffect.SetAutoStartHeldOff
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartGrace
import com.shawnkowalchuk.milo.core.trip.TripEffect.StartTrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * ADR-002, "State survives the process": the open trip is a row in the database, and after any
 * restart the rules pick it up from there with a fresh reading of the connections. A trip is
 * carried on only if it can still be the same drive.
 */
class TripStateMachineRestoreTest {
    private val recordingTrip = checkNotNull(RECORDING.trip)
    private val tripInGrace = checkNotNull(IN_GRACE.trip)

    /**
     * @param lastRecordedAtMs when the stored trip's newest point was recorded. By default the
     * process died and came back within the same moment.
     */
    private fun restore(
        storedTrip: ActiveTrip?,
        truckConnected: Boolean,
        atMs: Long,
        autoStartHeldOff: Boolean = false,
        androidAutoConnected: Boolean = false,
        lastRecordedAtMs: Long? = atMs,
        rules: TripRules = RULES,
    ) = TripStateMachine.restore(
        storedTrip,
        lastRecordedAtMs,
        autoStartHeldOff,
        truckConnected,
        androidAutoConnected,
        atMs,
        rules,
    )

    // ---- A trip that was recording ------------------------------------------------------------

    @Test
    fun `an open trip and the truck connected - the same trip carries on`() {
        val result = restore(recordingTrip, truckConnected = true, atMs = T0 + HOUR)

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(RECORDING, result.state)
    }

    @Test
    fun `a trip that was recording and now finds the truck gone starts its grace period`() {
        // The process died while recording, so no grace period was ever stored. The truck gets
        // the usual grace from now; if it stays away the trip is cut at the restart time, which
        // means at the last point recorded before the process died.
        val result = restore(recordingTrip, truckConnected = false, atMs = T0 + HOUR)

        assertEquals(listOf(StartGrace(T0 + HOUR, T0 + HOUR + GRACE)), result.effects)
    }

    @Test
    fun `a trip whose last point is old is closed there, and the truck starts a new one`() {
        // The process died in mid-drive yesterday afternoon. This morning the truck connects.
        val lastPoint = T0 + HOUR
        val nextMorning = T0 + 16 * HOUR

        val result =
            restore(
                recordingTrip,
                truckConnected = true,
                atMs = nextMorning,
                lastRecordedAtMs = lastPoint,
            )

        // Two trips. Carried on, it would be one trip that started yesterday.
        assertEquals(
            listOf(
                EndTrip(TripEndReason.STALE_AT_RESTART, lastPoint),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, nextMorning),
            ),
            result.effects,
        )
    }

    @Test
    fun `a trip whose last point is old is closed at once when the truck is gone as well`() {
        val result =
            restore(
                recordingTrip,
                truckConnected = false,
                atMs = T0 + 16 * HOUR,
                lastRecordedAtMs = T0 + HOUR,
            )

        // No grace period: opened for two more minutes, a connect could still revive it.
        assertEquals(listOf(EndTrip(TripEndReason.STALE_AT_RESTART, T0 + HOUR)), result.effects)
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `a short gap in recording is one drive, up to the limit and not beyond`() {
        // Killed in mid-drive and restarted with the truck still connected.
        val now = T0 + 2 * HOUR
        val atTheLimit = now - RESTART_GAP_LIMIT_MS

        val inside =
            restore(recordingTrip, truckConnected = true, atMs = now, lastRecordedAtMs = atTheLimit)
        val beyond =
            restore(
                recordingTrip,
                truckConnected = true,
                atMs = now,
                lastRecordedAtMs = atTheLimit - 1,
            )

        assertEquals(emptyList<TripEffect>(), inside.effects)
        assertEquals(EndTrip(TripEndReason.STALE_AT_RESTART, atTheLimit - 1), beyond.effects[0])
    }

    @Test
    fun `an old manual trip is not joined by the truck that connects the next day`() {
        val manualTrip = checkNotNull(MANUAL_NO_TRUCK.trip)

        val result =
            restore(
                manualTrip,
                truckConnected = true,
                atMs = T0 + 16 * HOUR,
                lastRecordedAtMs = T0 + HOUR,
            )

        assertEquals(
            listOf(
                EndTrip(TripEndReason.STALE_AT_RESTART, T0 + HOUR),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, T0 + 16 * HOUR),
            ),
            result.effects,
        )
    }

    @Test
    fun `a forgotten manual trip is closed where it last moved`() {
        val manualTrip = checkNotNull(MANUAL_NO_TRUCK.trip).copy(lastMovementAtMs = T0 + HOUR)

        val result = restore(manualTrip, truckConnected = false, atMs = T0 + 9 * HOUR)

        assertEquals(listOf(EndTrip(TripEndReason.NO_MOVEMENT, T0 + HOUR)), result.effects)
    }

    @Test
    fun `a stored trip without the time of its last point is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            restore(recordingTrip, truckConnected = true, atMs = T0, lastRecordedAtMs = null)
        }
    }

    // ---- A trip that was in its grace period --------------------------------------------------

    @Test
    fun `a trip in grace and the truck back in time - the grace period is cancelled`() {
        val result = restore(tripInGrace, truckConnected = true, atMs = GRACE_START + MINUTE)

        assertEquals(listOf(CancelGrace), result.effects)
        assertEquals(RECORDING, result.state)
    }

    @Test
    fun `a trip in grace and the truck back long after the deadline - a new trip starts`() {
        // A cleaner killed the process a minute into the grace period, so the timer never
        // fired. Four hours later the truck connects and wakes the app.
        val later = GRACE_START + 4 * HOUR

        val result = restore(tripInGrace, truckConnected = true, atMs = later)

        assertEquals(
            listOf(
                EndTrip(TripEndReason.GRACE_EXPIRED, GRACE_START),
                StartTrip(TripStartCause.TRUCK, truckSeen = true, later),
            ),
            result.effects,
        )
    }

    @Test
    fun `the truck gone and the grace period over - the trip is closed at its last point`() {
        // The process was dead for hours. The deadline passed long ago.
        val result = restore(tripInGrace, truckConnected = false, atMs = GRACE_START + 6 * HOUR)

        assertEquals(listOf(EndTrip(TripEndReason.GRACE_EXPIRED, GRACE_START)), result.effects)
        assertNull(result.state.trip)
    }

    @Test
    fun `the truck gone and the grace period still running - the timer is resumed`() {
        val result = restore(tripInGrace, truckConnected = false, atMs = GRACE_START + MINUTE)

        assertEquals(emptyList<TripEffect>(), result.effects)
        // The original deadline, not a new one counted from the restart.
        assertEquals(GRACE_START + GRACE, TripStateMachine.nextCheckAtMs(result.state, RULES))
    }

    @Test
    fun `a trip in grace is held open again by Android Auto, but only inside the deadline`() {
        val inTime =
            restore(
                tripInGrace,
                truckConnected = false,
                androidAutoConnected = true,
                atMs = GRACE_START + MINUTE,
            )
        val tooLate =
            restore(
                tripInGrace,
                truckConnected = false,
                androidAutoConnected = true,
                atMs = GRACE_START + 6 * HOUR,
            )

        assertEquals(listOf(CancelGrace), inTime.effects)
        assertEquals(listOf(EndTrip(TripEndReason.GRACE_EXPIRED, GRACE_START)), tooLate.effects)
        assertNull(tooLate.state.trip)
    }

    @Test
    fun `a trip in grace is judged by its deadline, not by the age of its last point`() {
        // Shawn has set a grace period of two hours. An hour in, the process restarts and the
        // truck is back. The last point is an hour old, but the trip is still inside its grace.
        val longGrace = TripRules(gracePeriodMs = 2 * HOUR)
        val waiting = tripInGrace.copy(grace = Grace(GRACE_START, GRACE_START + 2 * HOUR))

        val result =
            restore(
                waiting,
                truckConnected = true,
                atMs = GRACE_START + HOUR,
                lastRecordedAtMs = GRACE_START,
                rules = longGrace,
            )

        assertEquals(listOf(CancelGrace), result.effects)
    }

    // ---- No stored trip: the reconcile at boot, after an update and at launch ------------------

    @Test
    fun `no trip and the truck already connected - a trip starts although no event ever fired`() {
        val result = restore(storedTrip = null, truckConnected = true, atMs = T0)

        assertEquals(listOf(StartTrip(TripStartCause.TRUCK, truckSeen = true, T0)), result.effects)
    }

    @Test
    fun `no trip and no truck - nothing happens`() {
        val result = restore(storedTrip = null, truckConnected = false, atMs = T0)

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(IDLE, result.state)
    }

    @Test
    fun `no trip, Android Auto connected but no truck - nothing starts`() {
        val result =
            restore(
                storedTrip = null,
                truckConnected = false,
                androidAutoConnected = true,
                atMs = T0,
            )

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertNull(result.state.trip)
    }

    // ---- The hold-off survives the process too -------------------------------------------------

    @Test
    fun `held off and the truck still connected - no trip starts`() {
        val result =
            restore(storedTrip = null, truckConnected = true, autoStartHeldOff = true, atMs = T0)

        assertEquals(emptyList<TripEffect>(), result.effects)
        assertEquals(HELD_OFF, result.state)
    }

    @Test
    fun `held off and the truck gone - the hold-off is released`() {
        val result =
            restore(storedTrip = null, truckConnected = false, autoStartHeldOff = true, atMs = T0)

        assertEquals(listOf(SetAutoStartHeldOff(false)), result.effects)
        assertEquals(IDLE, result.state)
    }
}
