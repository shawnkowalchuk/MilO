package com.shawnkowalchuk.milo.core.schedule

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.temporal.ChronoUnit

// The work schedule: for each day of the week, whether trips on it can be Business and between
// which two times. Pure Kotlin, so the rules are tested without a phone. The schedule only ever
// sorts a trip that has already been recorded; it has no say in whether a trip starts.

/** The brief's default hours: 08:00 to 16:30, Monday to Friday. */
val DEFAULT_WORK_START: LocalTime = LocalTime.of(8, 0)
val DEFAULT_WORK_END: LocalTime = LocalTime.of(16, 30)

/**
 * Whether [start] and [end] can be the hours of one day: both on a whole minute, and the end
 * later than the start. Equal times are refused too, because such a day has no moment at which a
 * trip could start inside it. There are no overnight spans: a day's hours begin and end on that
 * day, so the latest possible end is 23:59.
 */
fun areValidHours(start: LocalTime, end: LocalTime): Boolean =
    end > start && start.isWholeMinute() && end.isWholeMinute()

private fun LocalTime.isWholeMinute(): Boolean = this == truncatedTo(ChronoUnit.MINUTES)

/**
 * The work hours of one day of the week.
 *
 * @param tracked whether a trip on this day can be Business at all. The hours of a day that is
 * not tracked are kept, so that switching the day on again brings them back.
 * @param start when the work day begins. A trip that starts exactly then is Business.
 * @param end when it is over. A trip that starts exactly then is Personal.
 * @throws IllegalArgumentException for hours that [areValidHours] refuses, so that no code ever
 * holds a day whose end is not after its start.
 */
data class DayHours(val tracked: Boolean, val start: LocalTime, val end: LocalTime) {
    init {
        require(areValidHours(start, end)) {
            "A day's hours must end after they start, on whole minutes, but were $start to $end"
        }
    }

    /** True if the two days have the same start and end, tracked or not. */
    fun hasHoursOf(other: DayHours): Boolean = start == other.start && end == other.end
}

/**
 * The hours of all seven days.
 *
 * @throws IllegalArgumentException if a day is missing: every day has hours, tracked or not.
 */
data class WorkSchedule(private val days: Map<DayOfWeek, DayHours>) {
    init {
        require(days.keys.containsAll(DayOfWeek.entries)) {
            "A work schedule has hours for all seven days, but had them for ${days.keys}"
        }
    }

    /** The hours of [day]. */
    fun on(day: DayOfWeek): DayHours = days.getValue(day)

    /** This schedule with [day] changed to [hours]. */
    fun with(day: DayOfWeek, hours: DayHours): WorkSchedule = WorkSchedule(days + (day to hours))

    /** This schedule with [day] tracked or not. The day keeps its hours either way. */
    fun withTracked(day: DayOfWeek, tracked: Boolean): WorkSchedule =
        with(day, on(day).copy(tracked = tracked))

    /**
     * This schedule with [day] starting at [start], or null if the day would then not end
     * after it starts ([areValidHours]). The Settings screen says so and stores nothing.
     */
    fun withStart(day: DayOfWeek, start: LocalTime): WorkSchedule? =
        withHours(day, start, on(day).end)

    /** This schedule with [day] ending at [end], or null on the same terms as [withStart]. */
    fun withEnd(day: DayOfWeek, end: LocalTime): WorkSchedule? = withHours(day, on(day).start, end)

    private fun withHours(day: DayOfWeek, start: LocalTime, end: LocalTime): WorkSchedule? =
        if (areValidHours(start, end)) with(day, on(day).copy(start = start, end = end)) else null

    /**
     * Whether copying [day]'s hours to every tracked day would change anything: the day is
     * tracked itself, and another tracked day has different hours. The Settings screen offers
     * the copy only then, so a schedule whose days all agree shows no button at all.
     */
    fun canCopyHoursOf(day: DayOfWeek): Boolean {
        val source = on(day)
        return source.tracked && days.values.any { it.tracked && !it.hasHoursOf(source) }
    }

    /**
     * This schedule with [day]'s start and end on every tracked day. Which days are tracked
     * does not change, and a day that is not tracked keeps its own hours.
     */
    fun withHoursOfOnTrackedDays(day: DayOfWeek): WorkSchedule {
        val source = on(day)
        return WorkSchedule(
            days.mapValues { (_, hours) ->
                if (hours.tracked) hours.copy(start = source.start, end = source.end) else hours
            },
        )
    }
}

/**
 * Monday to Friday, 08:00 to 16:30. Saturday and Sunday are not tracked and carry the same
 * hours, so that switching one of them on starts from something sensible.
 */
val DEFAULT_WORK_SCHEDULE: WorkSchedule =
    WorkSchedule(
        DayOfWeek.entries.associateWith { day ->
            DayHours(
                tracked = day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY,
                start = DEFAULT_WORK_START,
                end = DEFAULT_WORK_END,
            )
        },
    )
