package com.shawnkowalchuk.milo.core.schedule

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

private const val MINUTE_MS = 60_000L

/**
 * What an edit of a trip's times does to its Business or Personal and to "ran past schedule".
 * Times are Edmonton wall-clock times; 5 October 2026 is a Monday, and the default schedule is
 * Monday to Friday, 08:00 to 16:30.
 */
class TripRefilingTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private val business = TripFiled(TripCategory.BUSINESS, false, ranPastSchedule = false)
    private val personal = TripFiled(TripCategory.PERSONAL, false, ranPastSchedule = false)

    private fun local(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(edmonton).toInstant().toEpochMilli()

    /** Files a trip again. By default nothing about it changed and nothing was chosen. */
    private fun refile(
        stored: TripFiled,
        start: String,
        end: String? = null,
        startChanged: Boolean = false,
        endChanged: Boolean = false,
        chosen: TripCategory? = null,
        schedule: WorkSchedule? = DEFAULT_WORK_SCHEDULE,
    ): TripFiled = refileTrip(
        stored = stored,
        startChanged = startChanged,
        endChanged = endChanged,
        startedAtMs = local(start),
        endedAtMs = end?.let(::local) ?: (local(start) + 20 * MINUTE_MS),
        chosen = chosen,
        schedule = schedule,
        zone = edmonton,
    )

    // ---- Business or Personal -------------------------------------------------------------------

    @Test
    fun `a start moved into the work hours makes a Personal trip Business`() {
        val filed = refile(personal, start = "2026-10-05T08:05", startChanged = true)

        assertEquals(business, filed)
    }

    @Test
    fun `a start moved out of the work hours makes a Business trip Personal`() {
        val filed = refile(business, start = "2026-10-05T07:55", startChanged = true)

        assertEquals(personal, filed)
    }

    @Test
    fun `a start moved to a day that is not tracked makes the trip Personal`() {
        // Saturday 10 October.
        val filed = refile(business, start = "2026-10-10T10:00", startChanged = true)

        assertEquals(personal, filed)
    }

    @Test
    fun `a trip set by hand keeps its category whatever its new start says`() {
        val byHand = TripFiled(TripCategory.PERSONAL, categorySetByHand = true, false)

        val filed = refile(byHand, start = "2026-10-05T10:00", startChanged = true)

        // Inside the hours, and still Personal: Shawn's choice is final for MilO.
        assertEquals(byHand, filed)
    }

    @Test
    fun `a category chosen in the form wins over the schedule and is marked as set by hand`() {
        val filed =
            refile(
                stored = business,
                start = "2026-10-05T10:00",
                startChanged = true,
                chosen = TripCategory.PERSONAL,
            )

        assertEquals(TripFiled(TripCategory.PERSONAL, categorySetByHand = true, false), filed)
    }

    @Test
    fun `a category chosen in the form is set by hand even if it is what the trip was`() {
        val filed = refile(business, start = "2026-10-05T10:00", chosen = TripCategory.BUSINESS)

        assertEquals(TripFiled(TripCategory.BUSINESS, categorySetByHand = true, false), filed)
    }

    @Test
    fun `a trip whose start did not change is not sorted again`() {
        // Saved as Personal at 10:00 on a Monday, as it would be if Monday was not tracked
        // then. The schedule has changed since; an edit of the end or of nothing must not
        // quietly turn it into Business.
        val untouched = refile(personal, start = "2026-10-05T10:00")
        val endMoved = refile(personal, start = "2026-10-05T10:00", endChanged = true)

        assertEquals(personal, untouched)
        assertEquals(TripCategory.PERSONAL, endMoved.category)
    }

    @Test
    fun `a trip that was not sorted is sorted when its start changes, and not before`() {
        val unsorted = NOT_FILED

        assertEquals(unsorted, refile(unsorted, start = "2026-10-05T10:00"))
        assertEquals(
            business,
            refile(unsorted, start = "2026-10-05T10:00", startChanged = true),
        )
    }

    // ---- Ran past schedule ----------------------------------------------------------------------

    @Test
    fun `an end moved past the day's hours flags a Business trip`() {
        val filed =
            refile(
                business,
                start = "2026-10-05T16:00",
                end = "2026-10-05T16:45",
                endChanged = true,
            )

        assertEquals(TripFiled(TripCategory.BUSINESS, false, ranPastSchedule = true), filed)
    }

    @Test
    fun `an end moved back inside the day's hours takes the flag away`() {
        val ranPast = TripFiled(TripCategory.BUSINESS, false, ranPastSchedule = true)

        val filed =
            refile(ranPast, start = "2026-10-05T16:00", end = "2026-10-05T16:30", endChanged = true)

        // Ending exactly at the end time is not past it.
        assertEquals(business, filed)
    }

    @Test
    fun `a start moved out of the hours takes the flag away with the category`() {
        val ranPast = TripFiled(TripCategory.BUSINESS, false, ranPastSchedule = true)

        val filed =
            refile(
                ranPast,
                start = "2026-10-05T16:35",
                end = "2026-10-05T17:00",
                startChanged = true,
            )

        assertEquals(personal, filed)
    }

    @Test
    fun `the flag is kept as stored while neither time changes`() {
        val ranPast = TripFiled(TripCategory.BUSINESS, false, ranPastSchedule = true)

        // The times given here would not earn the flag. Nothing about them changed, so the
        // stored note stands, as it does when a trip is marked by hand on the Trips screen.
        val untouched = refile(ranPast, start = "2026-10-05T10:00", end = "2026-10-05T10:20")
        val marked =
            refile(
                ranPast,
                start = "2026-10-05T10:00",
                end = "2026-10-05T10:20",
                chosen = TripCategory.PERSONAL,
            )

        assertEquals(ranPast, untouched)
        assertEquals(TripFiled(TripCategory.PERSONAL, categorySetByHand = true, true), marked)
    }

    @Test
    fun `the flag follows the times of a trip that is set by hand too`() {
        val personalByHand = TripFiled(TripCategory.PERSONAL, categorySetByHand = true, false)

        val filed =
            refile(
                personalByHand,
                start = "2026-10-05T16:00",
                end = "2026-10-05T17:00",
                endChanged = true,
            )

        // The schedule would make it Business and it ends past the hours, so the note is
        // stored. It is shown only if Shawn marks the trip Business again.
        assertEquals(TripFiled(TripCategory.PERSONAL, categorySetByHand = true, true), filed)
    }

    @Test
    fun `a trip marked Business by hand outside the hours is never flagged`() {
        val businessByHand = TripFiled(TripCategory.BUSINESS, categorySetByHand = true, false)

        val filed =
            refile(
                businessByHand,
                start = "2026-10-05T18:00",
                end = "2026-10-05T19:00",
                endChanged = true,
            )

        // The schedule calls it Personal, so there are no hours of its own to run past.
        assertEquals(businessByHand, filed)
    }

    @Test
    fun `a trip that runs past midnight is judged against the day it started on`() {
        val filed =
            refile(
                business,
                start = "2026-10-05T16:00",
                end = "2026-10-06T00:10",
                endChanged = true,
            )

        assertEquals(true, filed.ranPastSchedule)
    }

    // ---- Without a schedule ---------------------------------------------------------------------

    @Test
    fun `without a readable schedule a changed start leaves the trip unsorted`() {
        val filed =
            refile(business, start = "2026-10-05T10:00", startChanged = true, schedule = null)

        // Not sorted by a guess: the catch-up sorts it once the settings can be read.
        assertEquals(NOT_FILED, filed)
    }

    @Test
    fun `without a readable schedule a choice by hand and an unchanged trip still stand`() {
        val chosen =
            refile(
                business,
                start = "2026-10-05T10:00",
                startChanged = true,
                chosen = TripCategory.PERSONAL,
                schedule = null,
            )
        val untouched = refile(business, start = "2026-10-05T10:00", schedule = null)

        assertEquals(TripFiled(TripCategory.PERSONAL, categorySetByHand = true, false), chosen)
        assertEquals(business, untouched)
    }

    // ---- A trip typed in new --------------------------------------------------------------------

    @Test
    fun `a trip that is typed in is sorted by its start and flagged by its end`() {
        fun typed(start: String, end: String, chosen: TripCategory? = null): TripFiled = refile(
            NOT_FILED,
            start = start,
            end = end,
            startChanged = true,
            endChanged = true,
            chosen = chosen,
        )

        assertEquals(business, typed("2026-10-05T09:00", "2026-10-05T09:40"))
        assertEquals(personal, typed("2026-10-05T19:00", "2026-10-05T19:40"))
        assertEquals(
            TripFiled(TripCategory.BUSINESS, false, ranPastSchedule = true),
            typed("2026-10-05T16:00", "2026-10-05T17:00"),
        )
        assertEquals(
            TripFiled(TripCategory.BUSINESS, categorySetByHand = true, false),
            typed("2026-10-05T19:00", "2026-10-05T19:40", chosen = TripCategory.BUSINESS),
        )
    }
}
