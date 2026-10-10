package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.core.clock.JumpingPhone
import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

private const val SECOND_MS = 1_000L

/**
 * The check's own rules, handed the time MilO's clock gives while the phone's date is one day
 * ahead (ADR-006). The rules are not changed: these were the three things an emulator showed on
 * 2026-10-07 when the date was set ahead for ten seconds with the check's time passed and trips
 * recorded (run 1 of that investigation), as the rules then decided them. Each now shows that
 * the rules are no longer handed tomorrow.
 *
 * The phone is [JumpingPhone]; its clock is read in the middle of the jump.
 */
class NothingRecordedClockJumpRulesTest : NothingRecordedRulesFixture() {
    private val wednesday: LocalDate = LocalDate.of(2026, 10, 7)
    private val phone = JumpingPhone(at("2026-10-07T18:32:42"))

    /** What MilO's clock reads four seconds into a jump of the phone's date. */
    private fun timeInsideTheJump(): Long {
        phone.clock.now()
        phone.setAhead()
        phone.advance(4 * SECOND_MS)
        return phone.clock.now()
    }

    @Test
    fun `during the jump today is still today, and its trips count`() {
        // Trips on the real day, as on the phone on 2026-10-07. Before the fix the look asked
        // storage for the trips of "today", which was Thursday, found none, and notified.
        val wednesdaysTrips = listOf(trip("2026-10-07T07:18"), trip("2026-10-07T17:40"))
        val inTheJump = due.copy(nowMs = timeInsideTheJump(), trips = wednesdaysTrips)

        val verdict = judgeNothingRecorded(inTheJump)

        assertEquals(wednesday, verdict.today)
        assertEquals(2, verdict.tripsCounted)
        assertEquals(NothingRecordedReason.TRIP_RECORDED, verdict.reason)
        assertEquals(NothingRecordedStep.WITHDRAW, verdict.step)
    }

    @Test
    fun `during the jump a day without a trip is judged as the day it is`() {
        // Wednesday evening, a work day with no trip and nothing shown yet: due, for Wednesday.
        val verdict = judgeNothingRecorded(due.copy(nowMs = timeInsideTheJump()))

        assertEquals(wednesday, verdict.today)
        assertEquals(NothingRecordedStep.SHOW, verdict.step)
    }

    @Test
    fun `the alarm worked out during the jump is for the real next noon`() {
        val next =
            nextNothingRecordedLookMs(timeInsideTheJump(), zone, noon, DEFAULT_WORK_SCHEDULE)

        // Before the fix: Friday's noon, and no daily look on the real next day. (While the
        // clocks disagree Android is not asked at all: see NothingRecordedClockJumpTest.)
        assertEquals(at("2026-10-08T12:00"), next)
    }
}
