package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.DayHours
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * What the two tests of the check's rules share: Edmonton's clock, a moment at which a
 * notification is due, and the trips and schedules the cases are made of.
 */
abstract class NothingRecordedRulesFixture {
    protected val zone: ZoneId = ZoneId.of("America/Edmonton")
    protected val noon: LocalTime = LocalTime.NOON
    protected val tuesday: LocalDate = LocalDate.of(2026, 10, 6)

    /** A local date and time in Edmonton, as stored time. Where it happens twice, the first. */
    protected fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    /**
     * Tuesday 6 October 2026 at 12:00 in Edmonton: a work day (08:00 to 16:30 out of the box),
     * the check set to noon, no trip, nothing shown yet. A notification is due.
     */
    protected val due =
        NothingRecordedMoment(
            nowMs = at("2026-10-06T12:00"),
            zone = zone,
            enabled = true,
            checkAt = noon,
            schedule = DEFAULT_WORK_SCHEDULE,
            trips = emptyList(),
            shownOn = null,
        )

    /** A trip MilO recorded: started by the truck at [start], twenty minutes long. */
    protected fun trip(start: String, status: TripStatus = TripStatus.FINISHED): Trip {
        val startedAtMs = at(start)
        return Trip(
            id = startedAtMs,
            startedAtMs = startedAtMs,
            endedAtMs = if (status == TripStatus.OPEN) null else startedAtMs + 1_200_000,
            status = status,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
        )
    }

    /** A schedule on which every day of the week is a work day, with the same hours. */
    protected fun everyDay(start: String, end: String): WorkSchedule = WorkSchedule(
        DayOfWeek.entries.associateWith {
            DayHours(tracked = true, start = LocalTime.parse(start), end = LocalTime.parse(end))
        },
    )
}
