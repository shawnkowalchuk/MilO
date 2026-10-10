package com.shawnkowalchuk.milo.clockjump

import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.classifyTrip
import com.shawnkowalchuk.milo.core.trip.AndroidAutoHoldGuard
import com.shawnkowalchuk.milo.core.trip.ParkedWatch
import com.shawnkowalchuk.milo.core.trip.TripEvent
import com.shawnkowalchuk.milo.core.trip.TripRules
import com.shawnkowalchuk.milo.core.trip.TripState
import com.shawnkowalchuk.milo.core.trip.TripStateMachine
import com.shawnkowalchuk.milo.core.trip.fixAt
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SECOND_MS = 1_000L
private const val MINUTE_MS = 60 * SECOND_MS
private const val HOUR_MS = 60 * MINUTE_MS

/**
 * The pure rules of `core/trip` and `core/schedule`, handed the times MilO's clock gives while
 * the phone's date is a day ahead for a few seconds. The rules themselves are not changed by
 * ADR-006: each of these tests began as the investigation's proof of what a rule makes of a
 * time that is a day ahead (a Friday trip sorted as Saturday's, a hold-off over at once), and
 * now shows that no rule is handed such a time any more.
 *
 * The phone is [JumpingPhone], started on Friday 2 October 2026 at 12:00 UTC.
 */
class ClockJumpTripRulesTest {
    private val utc = ZoneId.of("UTC")
    private val rules = TripRules(gracePeriodMs = 2 * MINUTE_MS, parkedLimitMs = 10 * MINUTE_MS)
    private val phone = JumpingPhone(FRIDAY_NOON_MS)

    /** What MilO's clock reads in the middle of a jump of the phone's date, nine seconds long. */
    private fun timeInsideAJump(): Long {
        phone.clock.now()
        phone.setAhead()
        phone.advance(4 * SECOND_MS)
        return phone.clock.now().also {
            phone.advance(5 * SECOND_MS)
            phone.setBack()
        }
    }

    // ---- Business or Personal: the weekday of the start -----------------------------------------

    @Test
    fun `a Friday trip that starts while the phone reads Saturday is Friday's, and Business`() {
        val startedAtMs = timeInsideAJump()
        phone.advance(HOUR_MS)

        val filed = classifyTrip(startedAtMs, phone.clock.now(), DEFAULT_WORK_SCHEDULE, utc)

        assertEquals(FRIDAY_NOON_MS + 4 * SECOND_MS, startedAtMs)
        assertEquals(TripCategory.BUSINESS, filed.category)
        assertFalse(filed.ranPastSchedule)
    }

    @Test
    fun `a Sunday trip that starts while the phone reads Monday is a Sunday trip, and Personal`() {
        phone.advance(2 * DAY_MS_OF_THE_SCENE)
        val startedAtMs = timeInsideAJump()
        phone.advance(HOUR_MS)

        val filed = classifyTrip(startedAtMs, phone.clock.now(), DEFAULT_WORK_SCHEDULE, utc)

        // Before the fix: a weekend drive on the accountant's Business list, under Monday.
        assertEquals(TripCategory.PERSONAL, filed.category)
    }

    // ---- The hold-off ---------------------------------------------------------------------------

    @Test
    fun `a hold-off of a few hours is not past its 12 hours for a reading inside the jump`() {
        val heldOff = TripState(truckConnected = true, autoStartHeldOffSinceMs = FRIDAY_NOON_MS)
        phone.advance(4 * HOUR_MS)

        val reading = TripEvent.TruckConnection(connected = true, atMs = timeInsideAJump())
        val step = TripStateMachine.step(heldOff, reading, rules)

        // Before the fix: the hold-off released by its time limit, and a trip started.
        assertTrue(step.effects.isEmpty())
        assertTrue(step.state.autoStartHeldOff)
    }

    @Test
    fun `End pressed inside the jump holds off from now, and a new link later releases it`() {
        // The press is stamped with MilO's clock (ButtonRules.endByHand keeps the event's time).
        val pressedAtMs = timeInsideAJump()
        val pressed =
            TripStateMachine.step(
                TripState(truckConnected = true),
                TripEvent.ManualEnd(truckConnected = true, atMs = pressedAtMs),
                rules,
            ).state
        assertEquals(FRIDAY_NOON_MS + 4 * SECOND_MS, pressed.autoStartHeldOffSinceMs)

        // An hour later the truck reconnects, its disconnect unseen. A new link releases a
        // hold-off that is more than 60 seconds old. Before the fix this one was "-23 hours"
        // old, stayed, and its 12 hours were 36.
        phone.advance(HOUR_MS)
        val newLink =
            TripStateMachine.step(
                pressed,
                TripEvent.TruckLinkConnected(atMs = phone.clock.now()),
                rules,
            )

        assertFalse(newLink.state.autoStartHeldOff)
    }

    // ---- The watch on the parked truck -----------------------------------------------------------

    @Test
    fun `the watch dates the trip that movement starts at the true time of its first fix`() {
        val place = fixAt(northMetres = 0.0, second = 0)
        // A first sighting away from the place, delivered while the date is a day ahead and
        // stamped with MilO's clock, as `LocationRecorder` stamps it; a second 30 seconds later.
        phone.advance(600 * SECOND_MS)
        val first = fixAt(northMetres = 150.0, second = 600).copy(wallClockMs = timeInsideAJump())
        phone.advance(21 * SECOND_MS)
        val second = fixAt(northMetres = 450.0, second = 630).copy(wallClockMs = phone.clock.now())

        val watch = ParkedWatch.at(place).plus(first).plus(second)

        // Two in a row and fast enough, which the watch always judged on time since boot.
        // The trip is dated at the first of the two, and that is now the true time.
        assertEquals(FRIDAY_NOON_MS + 604 * SECOND_MS, watch.movedAtMs)
    }

    // ---- Android Auto holding a trip or a wait alone --------------------------------------------

    @Test
    fun `Android Auto alone for a minute is still believed after a check inside the jump`() {
        val guard = AndroidAutoHoldGuard()
        fun believedAt(atMs: Long) =
            guard.believed(reported = true, truckConnected = false, atMs = atMs)
        assertTrue(believedAt(phone.clock.now()))
        phone.advance(MINUTE_MS)

        // Before the fix: "stuck for 12 hours", and unbelieved until it had reported
        // "not connected" once.
        assertTrue(believedAt(timeInsideAJump()))
        phone.advance(MINUTE_MS)
        assertTrue(believedAt(phone.clock.now()))
    }

    private companion object {
        const val DAY_MS_OF_THE_SCENE = 24 * HOUR_MS
    }
}
