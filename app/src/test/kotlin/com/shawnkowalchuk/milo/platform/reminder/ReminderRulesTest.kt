package com.shawnkowalchuk.milo.platform.reminder

import com.shawnkowalchuk.milo.data.settings.ReminderShown
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When the monthly reminder is shown: each of its conditions at its edge, the day a reminder
 * set to the 29th, 30th or 31st starts in a shorter month, the turn of the year, and the time
 * zone, which decides what "today" and "last month" are.
 */
class ReminderRulesTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private val utc = ZoneId.of("UTC")

    /** A local date and time in Edmonton, as stored time. */
    private fun at(text: String, zone: ZoneId = edmonton): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    /** Tuesday 6 October 2026, mid-morning: the 1st has come, September has trips, not sent. */
    private val due =
        ReminderMoment(
            nowMs = at("2026-10-06T10:00"),
            zone = edmonton,
            enabled = true,
            reminderDay = 1,
            submitted = false,
            businessTrips = 12,
            shown = null,
        )

    private val september = YearMonth.of(2026, 9)

    // ---- Each condition ---------------------------------------------------------------------------

    @Test
    fun `with every condition met the reminder is shown, for last month`() {
        val verdict = judgeReminder(due)

        assertEquals(ReminderStep.SHOW, verdict.step)
        assertEquals(ReminderReason.DUE, verdict.reason)
        assertEquals(september, verdict.month)
        assertEquals(LocalDate.of(2026, 10, 6), verdict.today)
        assertEquals(LocalDate.of(2026, 10, 1), verdict.startsOn)
    }

    @Test
    fun `switched off, nothing is shown and a reminder that is showing is withdrawn`() {
        val verdict = judgeReminder(due.copy(enabled = false))

        assertEquals(ReminderStep.WITHDRAW, verdict.step)
        assertEquals(ReminderReason.SWITCHED_OFF, verdict.reason)
    }

    @Test
    fun `a month whose report is recorded as sent brings no reminder, and withdraws one`() {
        val verdict = judgeReminder(due.copy(submitted = true))

        assertEquals(ReminderStep.WITHDRAW, verdict.step)
        assertEquals(ReminderReason.SUBMITTED, verdict.reason)
    }

    @Test
    fun `a month without a Business trip brings no reminder`() {
        val verdict = judgeReminder(due.copy(businessTrips = 0))

        assertEquals(ReminderStep.WITHDRAW, verdict.step)
        assertEquals(ReminderReason.NO_BUSINESS_TRIPS, verdict.reason)
        // One trip is enough.
        assertEquals(ReminderStep.SHOW, judgeReminder(due.copy(businessTrips = 1)).step)
    }

    @Test
    fun `it is shown once a day, and again the next day`() {
        val shownToday = ReminderShown(september, LocalDate.of(2026, 10, 6))

        val laterToday = judgeReminder(due.copy(nowMs = at("2026-10-06T17:30"), shown = shownToday))
        val tomorrow = judgeReminder(due.copy(nowMs = at("2026-10-07T09:00"), shown = shownToday))

        assertEquals(ReminderStep.LEAVE, laterToday.step)
        assertEquals(ReminderReason.SHOWN_TODAY, laterToday.reason)
        assertEquals(ReminderStep.SHOW, tomorrow.step)
    }

    @Test
    fun `a reminder shown today for another month does not stand in for this one`() {
        // The clock, or the time zone, moved across a month's end on the same calendar day.
        val forAugust = ReminderShown(YearMonth.of(2026, 8), LocalDate.of(2026, 10, 6))

        assertEquals(ReminderStep.SHOW, judgeReminder(due.copy(shown = forAugust)).step)
    }

    @Test
    fun `the conditions are looked at in order, so that the log names the first that fails`() {
        val nothingHolds =
            due.copy(
                enabled = false,
                reminderDay = 20,
                submitted = true,
                businessTrips = 0,
                shown = ReminderShown(september, LocalDate.of(2026, 10, 6)),
            )

        assertEquals(ReminderReason.SWITCHED_OFF, judgeReminder(nothingHolds).reason)
        val switchedOn = nothingHolds.copy(enabled = true)
        assertEquals(ReminderReason.DAY_NOT_REACHED, judgeReminder(switchedOn).reason)
        val dayReached = switchedOn.copy(reminderDay = 6)
        assertEquals(ReminderReason.SUBMITTED, judgeReminder(dayReached).reason)
        val notSubmitted = dayReached.copy(submitted = false)
        assertEquals(ReminderReason.NO_BUSINESS_TRIPS, judgeReminder(notSubmitted).reason)
        val withTrips = notSubmitted.copy(businessTrips = 3)
        assertEquals(ReminderReason.SHOWN_TODAY, judgeReminder(withTrips).reason)
    }

    // ---- The reminder day -------------------------------------------------------------------------

    @Test
    fun `before the reminder day nothing is shown, on it and after it the reminder is`() {
        val onThe15th = due.copy(reminderDay = 15)

        val dayBefore = judgeReminder(onThe15th.copy(nowMs = at("2026-10-14T23:59")))
        val firstMinute = judgeReminder(onThe15th.copy(nowMs = at("2026-10-15T00:00")))
        val lastDay = judgeReminder(onThe15th.copy(nowMs = at("2026-10-31T23:59")))

        assertEquals(ReminderReason.DAY_NOT_REACHED, dayBefore.reason)
        assertEquals(ReminderStep.WITHDRAW, dayBefore.step)
        assertEquals(LocalDate.of(2026, 10, 15), dayBefore.startsOn)
        assertEquals(ReminderStep.SHOW, firstMinute.step)
        assertEquals(ReminderStep.SHOW, lastDay.step)
        assertEquals(september, lastDay.month)
    }

    @Test
    fun `at the turn of the month the reminder moves on to the month that has just ended`() {
        val onThe15th = due.copy(reminderDay = 15)

        // 1 November: "last month" is October now, and its reminder day has not come.
        val november = judgeReminder(onThe15th.copy(nowMs = at("2026-11-01T00:00")))

        assertEquals(YearMonth.of(2026, 10), november.month)
        assertEquals(ReminderReason.DAY_NOT_REACHED, november.reason)
        assertEquals(LocalDate.of(2026, 11, 15), november.startsOn)
    }

    @Test
    fun `a reminder set to the 31st starts on the last day of a shorter month`() {
        assertEquals(LocalDate.of(2026, 10, 31), reminderStartsOn(YearMonth.of(2026, 10), 31))
        assertEquals(LocalDate.of(2026, 11, 30), reminderStartsOn(YearMonth.of(2026, 11), 31))
        assertEquals(LocalDate.of(2026, 4, 30), reminderStartsOn(YearMonth.of(2026, 4), 31))
        // The 30th is a day of April, so it is not moved.
        assertEquals(LocalDate.of(2026, 4, 30), reminderStartsOn(YearMonth.of(2026, 4), 30))
        assertEquals(LocalDate.of(2026, 4, 29), reminderStartsOn(YearMonth.of(2026, 4), 29))
    }

    @Test
    fun `in February the 29th, 30th and 31st start on the 28th, and on the 29th in a leap year`() {
        val plain = YearMonth.of(2027, 2)
        val leap = YearMonth.of(2028, 2)

        for (day in 29..31) {
            assertEquals(LocalDate.of(2027, 2, 28), reminderStartsOn(plain, day))
            assertEquals(LocalDate.of(2028, 2, 29), reminderStartsOn(leap, day))
        }
        assertEquals(LocalDate.of(2027, 2, 28), reminderStartsOn(plain, 28))
        assertEquals(LocalDate.of(2028, 2, 28), reminderStartsOn(leap, 28))
    }

    @Test
    fun `set to the 30th, February's reminder for January comes on its last day and not before`() {
        val onThe30th = due.copy(reminderDay = 30)

        val plainBefore = judgeReminder(onThe30th.copy(nowMs = at("2027-02-27T12:00")))
        val plainLast = judgeReminder(onThe30th.copy(nowMs = at("2027-02-28T12:00")))
        val leapBefore = judgeReminder(onThe30th.copy(nowMs = at("2028-02-28T12:00")))
        val leapLast = judgeReminder(onThe30th.copy(nowMs = at("2028-02-29T12:00")))

        assertEquals(ReminderReason.DAY_NOT_REACHED, plainBefore.reason)
        assertEquals(ReminderStep.SHOW, plainLast.step)
        assertEquals(YearMonth.of(2027, 1), plainLast.month)
        assertEquals(ReminderReason.DAY_NOT_REACHED, leapBefore.reason)
        assertEquals(ReminderStep.SHOW, leapLast.step)
        assertEquals(YearMonth.of(2028, 1), leapLast.month)
    }

    @Test
    fun `a stored day that is no day of a month is brought into the month`() {
        // No setter can store these; a file that holds one must not stop the reminder.
        assertEquals(LocalDate.of(2026, 10, 1), reminderStartsOn(YearMonth.of(2026, 10), 0))
        assertEquals(LocalDate.of(2026, 10, 31), reminderStartsOn(YearMonth.of(2026, 10), 99))
    }

    // ---- The turn of the year ---------------------------------------------------------------------

    @Test
    fun `on the first of January the reminder is about December of the year before`() {
        val newYear = judgeReminder(due.copy(nowMs = at("2027-01-01T08:00")))

        assertEquals(ReminderStep.SHOW, newYear.step)
        assertEquals(YearMonth.of(2026, 12), newYear.month)
        assertEquals(LocalDate.of(2027, 1, 1), newYear.startsOn)
        assertEquals(YearMonth.of(2026, 12), monthToRemindOf(at("2027-01-31T23:59"), edmonton))
        assertEquals(YearMonth.of(2026, 11), monthToRemindOf(at("2026-12-31T23:59"), edmonton))
    }

    // ---- The time zone ----------------------------------------------------------------------------

    @Test
    fun `the same moment is another day, and another month, in another time zone`() {
        // 05:30 UTC on 1 November is 23:30 on 31 October in Edmonton.
        val moment = at("2026-11-01T05:30", utc)

        val inEdmonton = judgeReminder(due.copy(nowMs = moment, zone = edmonton))
        val inUtc = judgeReminder(due.copy(nowMs = moment, zone = utc))

        assertEquals(LocalDate.of(2026, 10, 31), inEdmonton.today)
        assertEquals(september, inEdmonton.month)
        assertEquals(LocalDate.of(2026, 11, 1), inUtc.today)
        assertEquals(YearMonth.of(2026, 10), inUtc.month)
    }

    @Test
    fun `shown today is judged by the day in the phone's time zone`() {
        // Shown at 23:30 on the 6th in Edmonton. Half an hour later it is the 7th there, and
        // the next day's reminder is due; in Vancouver it is still the 6th.
        val shown = ReminderShown(september, LocalDate.of(2026, 10, 6))
        val vancouver = ZoneId.of("America/Vancouver")
        val moment = at("2026-10-07T00:00")

        val inEdmonton = judgeReminder(due.copy(nowMs = moment, shown = shown))
        val inVancouver = judgeReminder(due.copy(nowMs = moment, zone = vancouver, shown = shown))

        assertEquals(ReminderStep.SHOW, inEdmonton.step)
        assertEquals(ReminderStep.LEAVE, inVancouver.step)
    }

    // ---- The daily alarm --------------------------------------------------------------------------

    @Test
    fun `the daily look is asked for at nine, today if that is still to come, else tomorrow`() {
        assertEquals(at("2026-10-06T09:00"), nextDailyLookMs(at("2026-10-06T06:15"), edmonton))
        assertEquals(at("2026-10-07T09:00"), nextDailyLookMs(at("2026-10-06T09:00"), edmonton))
        assertEquals(at("2026-10-07T09:00"), nextDailyLookMs(at("2026-10-06T21:40"), edmonton))
        // Across a month's and a year's end.
        assertEquals(at("2027-01-01T09:00"), nextDailyLookMs(at("2026-12-31T09:00"), edmonton))
    }

    @Test
    fun `the daily look stays at nine when the clocks change`() {
        // The clocks go back on Sunday 1 November 2026: that day has 25 hours.
        val backTo = nextDailyLookMs(at("2026-10-31T09:00"), edmonton)
        // They go forward on Sunday 14 March 2027: that day has 23.
        val forwardTo = nextDailyLookMs(at("2027-03-13T09:00"), edmonton)

        assertEquals(at("2026-11-01T09:00"), backTo)
        assertEquals(25 * 3_600_000L, backTo - at("2026-10-31T09:00"))
        assertEquals(at("2027-03-14T09:00"), forwardTo)
        assertEquals(23 * 3_600_000L, forwardTo - at("2027-03-13T09:00"))
    }
}
