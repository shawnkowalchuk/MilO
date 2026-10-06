package com.shawnkowalchuk.milo.core.schedule

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MINUTE_MS = 60_000L

/**
 * Business or Personal from when a trip started. Times are written as Edmonton wall-clock times
 * unless a test says otherwise; Edmonton is where the truck is, and its clocks change twice a
 * year. In 2026: 5 October is a Monday, clocks go back on Sunday 1 November and forward on
 * Sunday 8 March.
 */
class TripClassificationTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private val business = TripClassification(TripCategory.BUSINESS, ranPastSchedule = false)
    private val ranPast = TripClassification(TripCategory.BUSINESS, ranPastSchedule = true)
    private val personal = TripClassification(TripCategory.PERSONAL, ranPastSchedule = false)

    /** A local date and time in Edmonton, such as "2026-10-05T08:00", as a stored time. */
    private fun local(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(edmonton).toInstant().toEpochMilli()

    private fun utc(instant: String): Long = Instant.parse(instant).toEpochMilli()

    /** Sorts a trip from [start] to [end] by the default schedule unless another is given. */
    private fun sort(
        start: Long,
        end: Long? = start + 20 * MINUTE_MS,
        schedule: WorkSchedule = DEFAULT_WORK_SCHEDULE,
        zone: ZoneId = edmonton,
    ): TripClassification = classifyTrip(start, end, schedule, zone)

    private fun scheduleWith(day: DayOfWeek, start: LocalTime, end: LocalTime): WorkSchedule =
        DEFAULT_WORK_SCHEDULE.with(day, DayHours(tracked = true, start = start, end = end))

    // ---- The start decides ----------------------------------------------------------------------

    @Test
    fun `a trip that starts inside a tracked day's hours is Business`() {
        assertEquals(business, sort(local("2026-10-05T10:15")))
        assertEquals(business, sort(local("2026-10-09T16:00")))
    }

    @Test
    fun `exactly at the start time is Business, and a moment before it is Personal`() {
        val start = local("2026-10-05T08:00")

        assertEquals(business, sort(start))
        assertEquals(personal, sort(start - 1))
        assertEquals(personal, sort(local("2026-10-05T07:55")))
    }

    @Test
    fun `a moment before the end time is Business, and exactly at it is Personal`() {
        val end = local("2026-10-05T16:30")

        // It ends after the day's hours, so it is also past the schedule.
        assertEquals(ranPast, sort(end - 1))
        assertEquals(personal, sort(end))
        assertEquals(personal, sort(local("2026-10-05T19:00")))
    }

    @Test
    fun `a trip on a day that is not tracked is Personal at any hour`() {
        // Saturday 10 October and Sunday 11 October, in the middle of what would be work hours.
        assertEquals(personal, sort(local("2026-10-10T10:00")))
        assertEquals(personal, sort(local("2026-10-11T10:00")))
    }

    @Test
    fun `a day that is switched on counts like any other, with its own hours`() {
        val saturdayMornings =
            scheduleWith(DayOfWeek.SATURDAY, LocalTime.of(7, 0), LocalTime.of(12, 0))

        assertEquals(business, sort(local("2026-10-10T07:30"), schedule = saturdayMornings))
        assertEquals(personal, sort(local("2026-10-10T12:00"), schedule = saturdayMornings))
        // Monday still goes by Monday's hours: 07:30 is before them.
        assertEquals(personal, sort(local("2026-10-05T07:30"), schedule = saturdayMornings))
    }

    @Test
    fun `a day that is switched off is Personal even inside the hours it keeps`() {
        val noWednesdays = DEFAULT_WORK_SCHEDULE.withTracked(DayOfWeek.WEDNESDAY, false)

        assertEquals(personal, sort(local("2026-10-07T10:00"), schedule = noWednesdays))
        assertEquals(business, sort(local("2026-10-08T10:00"), schedule = noWednesdays))
    }

    @Test
    fun `only the start decides, so connecting five minutes early makes the trip Personal`() {
        // The truck connects at 07:55 for a drive at 08:05: one trip, which started at 07:55.
        val early = sort(local("2026-10-05T07:55"), end = local("2026-10-05T09:10"))

        assertEquals(personal, early)
    }

    @Test
    fun `the day and the time are read in the phone's time zone`() {
        // 14:30 UTC on Monday 5 October is 08:30 in Edmonton and 23:30 in Tokyo.
        val start = utc("2026-10-05T14:30:00Z")

        assertEquals(business, sort(start, zone = edmonton))
        assertEquals(business, sort(start, zone = ZoneId.of("UTC")))
        assertEquals(personal, sort(start, zone = ZoneId.of("Asia/Tokyo")))
        // 02:00 UTC on Tuesday is still Monday evening in Edmonton, and a work morning in Tokyo.
        val later = utc("2026-10-06T02:00:00Z")
        assertEquals(personal, sort(later, zone = edmonton))
        assertEquals(business, sort(later, zone = ZoneId.of("Asia/Tokyo")))
    }

    // ---- Ran past schedule ----------------------------------------------------------------------

    @Test
    fun `a Business trip that ends after its day's end time ran past the schedule`() {
        val start = local("2026-10-05T16:10")

        val hoursEnd = local("2026-10-05T16:30")

        assertEquals(business, sort(start, end = local("2026-10-05T16:25")))
        assertEquals("Ending on the stroke is not past it", business, sort(start, end = hoursEnd))
        assertEquals(ranPast, sort(start, end = hoursEnd + 1))
        assertEquals(ranPast, sort(start, end = local("2026-10-05T17:45")))
    }

    @Test
    fun `a Personal trip is never flagged, wherever it ends`() {
        // Starts before the hours and ends inside them; starts after them and ends later
        // still; a whole Saturday.
        val trips =
            listOf(
                local("2026-10-05T07:30") to local("2026-10-05T09:00"),
                local("2026-10-05T16:45") to local("2026-10-05T18:00"),
                local("2026-10-10T10:00") to local("2026-10-10T20:00"),
            )

        for ((start, end) in trips) assertEquals(personal, sort(start, end = end))
    }

    @Test
    fun `a trip without a stored end, or with an end before its start, is not flagged`() {
        val start = local("2026-10-05T16:10")

        assertEquals(business, sort(start, end = null))
        // The phone corrected its clock during the trip: the stored end is before the start.
        assertEquals(business, sort(start, end = start - 5 * MINUTE_MS))
    }

    // ---- Across midnight ------------------------------------------------------------------------

    @Test
    fun `a Business trip that runs past midnight is judged by the day it started on`() {
        // Friday 16:00 to Saturday 00:30. Saturday is not tracked; that changes nothing.
        val overMidnight = sort(local("2026-10-09T16:00"), end = local("2026-10-10T00:30"))

        assertEquals(ranPast, overMidnight)
    }

    @Test
    fun `ending inside the next day's hours does not un-flag a trip that ran past its own`() {
        // Monday 16:00 to Tuesday 09:00: a time of day inside Tuesday's hours, a day later.
        val untilNextMorning = sort(local("2026-10-05T16:00"), end = local("2026-10-06T09:00"))

        assertEquals(ranPast, untilNextMorning)
    }

    @Test
    fun `a trip that starts outside the hours and ends inside the next day's is Personal`() {
        // Thursday 23:30 to Friday 08:30, and Sunday 23:50 to Monday 08:10.
        assertEquals(personal, sort(local("2026-10-08T23:30"), end = local("2026-10-09T08:30")))
        assertEquals(personal, sort(local("2026-10-11T23:50"), end = local("2026-10-12T08:10")))
    }

    @Test
    fun `late hours end on their own day, so a trip past midnight ran past them`() {
        val late = scheduleWith(DayOfWeek.MONDAY, LocalTime.of(22, 0), LocalTime.of(23, 59))
        val start = local("2026-10-05T23:00")

        assertEquals(business, sort(start, end = local("2026-10-05T23:59"), schedule = late))
        assertEquals(ranPast, sort(start, end = local("2026-10-06T00:10"), schedule = late))
        // 23:59 itself is the end, so a trip starting in the day's last minute is Personal.
        assertEquals(personal, sort(local("2026-10-05T23:59"), schedule = late))
    }

    // ---- When the clocks change -----------------------------------------------------------------

    @Test
    fun `the same time of day is Business before and after the clocks go back`() {
        // Friday 30 October is summer time (UTC-6), Monday 2 November winter time (UTC-7).
        val fridayEight = utc("2026-10-30T14:00:00Z")
        val mondayEight = utc("2026-11-02T15:00:00Z")
        assertEquals(local("2026-10-30T08:00"), fridayEight)
        assertEquals(local("2026-11-02T08:00"), mondayEight)

        assertEquals(business, sort(fridayEight))
        assertEquals(business, sort(mondayEight))
        // 14:30 UTC was inside the hours on Friday. On Monday it is 07:30, and Personal: the
        // hours follow the clock on the wall, not a fixed distance from UTC.
        assertEquals(business, sort(utc("2026-10-30T14:30:00Z")))
        assertEquals(personal, sort(utc("2026-11-02T14:30:00Z")))
    }

    @Test
    fun `on the day the clocks go back the hours still end at the end time on the wall`() {
        // Sunday 1 November has 25 hours. 16:30 that day is 23:30 UTC, not 24 hours less
        // 7.5 after the day began.
        val sundays = DEFAULT_WORK_SCHEDULE.withTracked(DayOfWeek.SUNDAY, true)
        val start = local("2026-11-01T16:00")
        val wallEnd = utc("2026-11-01T23:30:00Z")
        assertEquals(local("2026-11-01T16:30"), wallEnd)

        assertEquals(business, sort(start, end = wallEnd, schedule = sundays))
        assertEquals(ranPast, sort(start, end = wallEnd + 1, schedule = sundays))
        // An hour before the end on the wall the trip has not run past, whatever a count of
        // hours since midnight would say.
        assertEquals(business, sort(start, end = wallEnd - 60 * MINUTE_MS, schedule = sundays))
    }

    @Test
    fun `both passes through the repeated hour are the same time of day`() {
        // 01:15 happens twice on 1 November: at 07:15 UTC and again at 08:15 UTC.
        val nights = scheduleWith(DayOfWeek.SUNDAY, LocalTime.of(0, 30), LocalTime.of(1, 30))
        val firstPass = utc("2026-11-01T07:15:00Z")
        val secondPass = utc("2026-11-01T08:15:00Z")

        assertEquals(business, sort(firstPass, end = null, schedule = nights))
        assertEquals(business, sort(secondPass, end = null, schedule = nights))
        // An end time that happens twice counts the first time: 01:30 summer time, 07:30 UTC.
        assertFalse(sort(firstPass, utc("2026-11-01T07:30:00Z"), nights).ranPastSchedule)
        assertTrue(sort(firstPass, utc("2026-11-01T07:31:00Z"), nights).ranPastSchedule)
    }

    @Test
    fun `on the day the clocks go forward a start time inside the missing hour still works`() {
        // Sunday 8 March: 02:00 to 03:00 does not exist. Hours 02:30 to 12:00.
        val early = scheduleWith(DayOfWeek.SUNDAY, LocalTime.of(2, 30), LocalTime.of(12, 0))

        // 01:59 winter time is before the hours; 03:00 summer time, a minute later, is inside.
        assertEquals(personal, sort(utc("2026-03-08T08:59:00Z"), schedule = early))
        assertEquals(business, sort(utc("2026-03-08T09:00:00Z"), schedule = early))
        // And 08:00 is Business on the Monday after as on the Friday before, an hour apart in
        // UTC.
        assertEquals(business, sort(utc("2026-03-06T15:00:00Z")))
        assertEquals(business, sort(utc("2026-03-09T14:00:00Z")))
    }
}
