package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The check's rules on the two days on which the clocks change, and the moment its daily alarm
 * is asked for. The rest of the rules are in `NothingRecordedRulesTest`.
 */
class NothingRecordedClockTest : NothingRecordedRulesFixture() {
    // ---- The clocks change ------------------------------------------------------------------------

    @Test
    fun `on the day the clocks go forward a time in the missing hour is reached an hour on`() {
        // Sunday 8 March 2026: 02:00 becomes 03:00, so there is no 02:30.
        val inTheGap =
            due.copy(schedule = everyDay("01:00", "23:00"), checkAt = LocalTime.of(2, 30))
        val threeOClock = at("2026-03-08T03:00")

        val beforeTheJump = judgeNothingRecorded(inTheGap.copy(nowMs = threeOClock - 1))
        val afterTheJump = judgeNothingRecorded(inTheGap.copy(nowMs = threeOClock))
        val halfPastThree = judgeNothingRecorded(inTheGap.copy(nowMs = at("2026-03-08T03:30")))

        assertEquals(NothingRecordedReason.TOO_EARLY, beforeTheJump.reason)
        assertEquals(NothingRecordedReason.TOO_EARLY, afterTheJump.reason)
        assertEquals(at("2026-03-08T03:30"), afterTheJump.checkedFromMs)
        assertEquals(NothingRecordedStep.SHOW, halfPastThree.step)
    }

    @Test
    fun `on the day the clocks go back a time that comes twice counts from its first coming`() {
        // Sunday 1 November 2026: 02:00 becomes 01:00, so 01:30 happens twice.
        val twice = due.copy(schedule = everyDay("00:30", "23:00"), checkAt = LocalTime.of(1, 30))
        val firstHalfPastOne = at("2026-11-01T01:30")
        val anHour = 3_600_000L

        val before = judgeNothingRecorded(twice.copy(nowMs = firstHalfPastOne - 1))
        val first = judgeNothingRecorded(twice.copy(nowMs = firstHalfPastOne))
        // 45 minutes on the clocks read 01:15 again. The time has passed, and stays passed.
        val quarterPastOneAgain = judgeNothingRecorded(
            twice.copy(
                nowMs =
                    firstHalfPastOne + anHour * 3 / 4,
            ),
        )

        assertEquals(NothingRecordedReason.TOO_EARLY, before.reason)
        assertEquals(NothingRecordedStep.SHOW, first.step)
        assertEquals(NothingRecordedStep.SHOW, quarterPastOneAgain.step)
        assertEquals(LocalDate.of(2026, 11, 1), quarterPastOneAgain.today)
    }

    @Test
    fun `noon is noon on a day that is an hour short or an hour long`() {
        val everyDayAtNoon = due.copy(schedule = everyDay("08:00", "16:30"))

        for (day in listOf("2026-03-08", "2026-11-01")) {
            val before = judgeNothingRecorded(everyDayAtNoon.copy(nowMs = at("${day}T11:59")))
            val atNoon = judgeNothingRecorded(everyDayAtNoon.copy(nowMs = at("${day}T12:00")))

            assertEquals(day, NothingRecordedReason.TOO_EARLY, before.reason)
            assertEquals(day, NothingRecordedStep.SHOW, atNoon.step)
        }
    }

    // ---- The daily alarm --------------------------------------------------------------------------

    @Test
    fun `the alarm is asked for at the moment the next day is checked from`() {
        fun next(now: String, checkAt: LocalTime = noon) =
            nextNothingRecordedLookMs(at(now), zone, checkAt, DEFAULT_WORK_SCHEDULE)

        assertEquals(at("2026-10-06T12:00"), next("2026-10-06T09:00"))
        // On the stroke it has come: the next one is tomorrow's.
        assertEquals(at("2026-10-07T12:00"), next("2026-10-06T12:00"))
        assertEquals(at("2026-10-07T12:00"), next("2026-10-06T18:00"))
        // A set time before the day's start: the alarm goes by the start.
        assertEquals(at("2026-10-06T08:00"), next("2026-10-06T07:30", LocalTime.of(7, 0)))
        assertEquals(at("2026-10-07T08:00"), next("2026-10-06T08:00", LocalTime.of(7, 0)))
    }

    @Test
    fun `a day that is not a work day is looked at too, at the set time`() {
        val seven = LocalTime.of(7, 0)

        // Friday afternoon: Saturday is not tracked, so its own start does not come into it.
        assertEquals(
            at("2026-10-10T07:00"),
            nextNothingRecordedLookMs(at("2026-10-09T13:00"), zone, seven, DEFAULT_WORK_SCHEDULE),
        )
        // Sunday evening: Monday is a work day again, checked from its start.
        assertEquals(
            at("2026-10-12T08:00"),
            nextNothingRecordedLookMs(at("2026-10-11T19:00"), zone, seven, DEFAULT_WORK_SCHEDULE),
        )
    }

    @Test
    fun `the alarm stays at the same time of day when the clocks change`() {
        val schedule = everyDay("08:00", "16:30")
        val anHour = 3_600_000L

        val intoSummerTime = nextNothingRecordedLookMs(at("2026-03-07T12:00"), zone, noon, schedule)
        val intoWinterTime = nextNothingRecordedLookMs(at("2026-10-31T12:00"), zone, noon, schedule)

        assertEquals(at("2026-03-08T12:00"), intoSummerTime)
        assertEquals(23 * anHour, intoSummerTime - at("2026-03-07T12:00"))
        assertEquals(at("2026-11-01T12:00"), intoWinterTime)
        assertEquals(25 * anHour, intoWinterTime - at("2026-10-31T12:00"))
    }
}
