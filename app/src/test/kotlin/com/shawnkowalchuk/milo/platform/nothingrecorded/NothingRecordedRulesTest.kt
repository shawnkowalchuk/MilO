package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.DayHours
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When MilO says that no trip has been recorded today: each condition at its edge, each kind
 * of trip, and midnight. The two days on which the clocks change, and the daily alarm, are in
 * `NothingRecordedClockTest`.
 */
class NothingRecordedRulesTest : NothingRecordedRulesFixture() {
    // ---- The time ---------------------------------------------------------------------------------

    @Test
    fun `a work day with no trip brings the notification from the stroke of the set time`() {
        val justBefore = judgeNothingRecorded(due.copy(nowMs = at("2026-10-06T12:00") - 1))
        val onTheStroke = judgeNothingRecorded(due)
        val inTheEvening = judgeNothingRecorded(due.copy(nowMs = at("2026-10-06T21:45")))

        assertEquals(NothingRecordedReason.TOO_EARLY, justBefore.reason)
        assertEquals(NothingRecordedStep.WITHDRAW, justBefore.step)
        assertEquals(NothingRecordedReason.DUE, onTheStroke.reason)
        assertEquals(NothingRecordedStep.SHOW, onTheStroke.step)
        assertEquals(tuesday, onTheStroke.today)
        assertEquals(at("2026-10-06T12:00"), onTheStroke.checkedFromMs)
        // The day's work hours ending does not end the check: a phone that was off until the
        // evening still says it, once.
        assertEquals(NothingRecordedStep.SHOW, inTheEvening.step)
    }

    @Test
    fun `a set time before the day's start waits until the start has passed`() {
        val earlyBird = due.copy(checkAt = LocalTime.of(7, 0))

        val afterTheSetTime = judgeNothingRecorded(earlyBird.copy(nowMs = at("2026-10-06T07:30")))
        val atTheStart = judgeNothingRecorded(earlyBird.copy(nowMs = at("2026-10-06T08:00")))

        assertEquals(NothingRecordedReason.TOO_EARLY, afterTheSetTime.reason)
        assertEquals(at("2026-10-06T08:00"), afterTheSetTime.checkedFromMs)
        assertEquals(NothingRecordedStep.SHOW, atTheStart.step)
    }

    @Test
    fun `each day is checked from its own start when that is later than the set time`() {
        val lateFriday =
            DEFAULT_WORK_SCHEDULE.with(
                DayOfWeek.FRIDAY,
                DayHours(tracked = true, start = LocalTime.of(10, 0), end = LocalTime.of(18, 0)),
            )
        val atNine = LocalTime.of(9, 0)

        assertEquals(atNine, checkedFrom(lateFriday.on(DayOfWeek.TUESDAY), atNine))
        assertEquals(LocalTime.of(10, 0), checkedFrom(lateFriday.on(DayOfWeek.FRIDAY), atNine))
        assertEquals(
            at("2026-10-09T10:00"),
            checkedFromMs(LocalDate.of(2026, 10, 9), zone, atNine, lateFriday),
        )
    }

    // ---- The day ----------------------------------------------------------------------------------

    @Test
    fun `a day the schedule does not track is never checked`() {
        val saturday = judgeNothingRecorded(due.copy(nowMs = at("2026-10-10T15:00")))
        val tuesdayOff =
            judgeNothingRecorded(
                due.copy(schedule = DEFAULT_WORK_SCHEDULE.withTracked(DayOfWeek.TUESDAY, false)),
            )

        for (verdict in listOf(saturday, tuesdayOff)) {
            assertEquals(NothingRecordedReason.NOT_A_WORK_DAY, verdict.reason)
            assertEquals(NothingRecordedStep.WITHDRAW, verdict.step)
            assertNull(verdict.checkedFromMs)
        }
    }

    @Test
    fun `switched off, nothing is said whatever else holds`() {
        val verdict = judgeNothingRecorded(due.copy(enabled = false))

        assertEquals(NothingRecordedReason.SWITCHED_OFF, verdict.reason)
        assertEquals(NothingRecordedStep.WITHDRAW, verdict.step)
    }

    // ---- The trips --------------------------------------------------------------------------------

    @Test
    fun `a trip MilO opened today counts, whatever became of it`() {
        for (status in TripStatus.entries) {
            val verdict =
                judgeNothingRecorded(due.copy(trips = listOf(trip("2026-10-06T09:10", status))))

            assertEquals("$status", NothingRecordedReason.TRIP_RECORDED, verdict.reason)
            assertEquals("$status", NothingRecordedStep.WITHDRAW, verdict.step)
            assertEquals("$status", 1, verdict.tripsCounted)
        }
    }

    @Test
    fun `a trip started with the button, and one left out by the schedule, count too`() {
        val byButton = trip("2026-10-06T09:10").copy(startedBy = TripStartCause.MANUAL)
        val leftOut =
            trip("2026-10-06T06:40", TripStatus.DISCARDED).copy(ignoredOutsideSchedule = true)

        for (one in listOf(byButton, leftOut)) {
            val verdict = judgeNothingRecorded(due.copy(trips = listOf(one)))
            assertEquals(NothingRecordedReason.TRIP_RECORDED, verdict.reason)
        }
    }

    @Test
    fun `a trip added by hand does not count, because it shows nothing about detection`() {
        val typedIn = trip("2026-10-06T09:10").copy(addedByHand = true)

        val alone = judgeNothingRecorded(due.copy(trips = listOf(typedIn)))
        val besideARecordedOne =
            judgeNothingRecorded(due.copy(trips = listOf(typedIn, trip("2026-10-06T10:30"))))

        assertEquals(NothingRecordedStep.SHOW, alone.step)
        assertEquals(0, alone.tripsCounted)
        assertEquals(1, alone.tripsByHand)
        assertEquals(NothingRecordedReason.TRIP_RECORDED, besideARecordedOne.reason)
        assertEquals(1, besideARecordedOne.tripsCounted)
        assertEquals(1, besideARecordedOne.tripsByHand)
    }

    @Test
    fun `a trip is judged by the start MilO recorded, not by one typed over it`() {
        val movedIntoToday =
            trip("2026-10-06T09:00").copy(
                editedByHand = true,
                recordedStartedAtMs = at("2026-10-05T09:00"),
            )
        val movedOutOfToday =
            trip("2026-10-05T09:00").copy(
                editedByHand = true,
                recordedStartedAtMs = at("2026-10-06T09:00"),
            )

        assertEquals(
            NothingRecordedStep.SHOW,
            judgeNothingRecorded(due.copy(trips = listOf(movedIntoToday))).step,
        )
        assertEquals(
            NothingRecordedReason.TRIP_RECORDED,
            judgeNothingRecorded(due.copy(trips = listOf(movedOutOfToday))).reason,
        )
    }

    // ---- One a day --------------------------------------------------------------------------------

    @Test
    fun `shown today it is left alone, and the next work day brings its own`() {
        val shown = due.copy(shownOn = tuesday)

        val laterToday = judgeNothingRecorded(shown.copy(nowMs = at("2026-10-06T15:00")))
        val nextDay = judgeNothingRecorded(shown.copy(nowMs = at("2026-10-07T12:00")))

        assertEquals(NothingRecordedReason.SHOWN_TODAY, laterToday.reason)
        assertEquals(NothingRecordedStep.LEAVE, laterToday.step)
        assertEquals(NothingRecordedStep.SHOW, nextDay.step)
    }

    @Test
    fun `a trip that starts after the notification takes it away`() {
        val afterwards =
            due.copy(
                nowMs = at("2026-10-06T13:05"),
                shownOn = tuesday,
                trips = listOf(trip("2026-10-06T13:00", TripStatus.OPEN)),
            )

        val verdict = judgeNothingRecorded(afterwards)

        assertEquals(NothingRecordedReason.TRIP_RECORDED, verdict.reason)
        assertEquals(NothingRecordedStep.WITHDRAW, verdict.step)
    }

    @Test
    fun `the conditions are looked at in order, so that the log names the first that fails`() {
        val nothingHolds =
            due.copy(
                nowMs = at("2026-10-10T07:00"),
                enabled = false,
                trips = listOf(trip("2026-10-10T06:00")),
                shownOn = LocalDate.of(2026, 10, 10),
            )

        assertEquals(NothingRecordedReason.SWITCHED_OFF, judgeNothingRecorded(nothingHolds).reason)
        val switchedOn = nothingHolds.copy(enabled = true)
        assertEquals(NothingRecordedReason.NOT_A_WORK_DAY, judgeNothingRecorded(switchedOn).reason)
        val aWorkDay = switchedOn.copy(schedule = everyDay("05:00", "16:30"))
        assertEquals(NothingRecordedReason.TOO_EARLY, judgeNothingRecorded(aWorkDay).reason)
        val pastTheTime = aWorkDay.copy(nowMs = at("2026-10-10T12:30"))
        assertEquals(NothingRecordedReason.TRIP_RECORDED, judgeNothingRecorded(pastTheTime).reason)
        val noTrip = pastTheTime.copy(trips = emptyList())
        assertEquals(NothingRecordedReason.SHOWN_TODAY, judgeNothingRecorded(noTrip).reason)
    }

    // ---- Midnight ---------------------------------------------------------------------------------

    @Test
    fun `at midnight a new day begins, and yesterday's notification is taken away`() {
        val shownOnTuesday = due.copy(shownOn = tuesday)

        val lastMoment =
            judgeNothingRecorded(shownOnTuesday.copy(nowMs = at("2026-10-07T00:00") - 1))
        val firstMoment = judgeNothingRecorded(shownOnTuesday.copy(nowMs = at("2026-10-07T00:00")))

        assertEquals(NothingRecordedStep.LEAVE, lastMoment.step)
        assertEquals(tuesday, lastMoment.today)
        // Wednesday has not reached its time yet: what is still showing is Tuesday's.
        assertEquals(NothingRecordedReason.TOO_EARLY, firstMoment.reason)
        assertEquals(NothingRecordedStep.WITHDRAW, firstMoment.step)
        assertEquals(LocalDate.of(2026, 10, 7), firstMoment.today)
    }

    @Test
    fun `yesterday's trip is not today's, but a trip still being recorded is`() {
        val wednesdayNoon = due.copy(nowMs = at("2026-10-07T12:00"))
        val endedAfterMidnight = trip("2026-10-06T23:50")
        val stillRecording = trip("2026-10-06T23:50", TripStatus.OPEN)
        val onTheStrokeOfMidnight = trip("2026-10-07T00:00")

        val yesterdays =
            judgeNothingRecorded(wednesdayNoon.copy(trips = listOf(endedAfterMidnight)))
        val recording = judgeNothingRecorded(wednesdayNoon.copy(trips = listOf(stillRecording)))
        val todays = judgeNothingRecorded(wednesdayNoon.copy(trips = listOf(onTheStrokeOfMidnight)))

        assertEquals(NothingRecordedStep.SHOW, yesterdays.step)
        assertEquals(0, yesterdays.tripsCounted)
        assertEquals(NothingRecordedReason.TRIP_RECORDED, recording.reason)
        assertEquals(NothingRecordedReason.TRIP_RECORDED, todays.reason)
    }
}
