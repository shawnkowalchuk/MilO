package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripEdit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS

/**
 * Which day a trip ends on by the form, and which of the two a time means in the hour that
 * happens twice when the clocks go back. Times are Edmonton wall-clock times.
 */
class TripFormEndDayTest {
    private val edmonton = ZoneId.of("America/Edmonton")

    private fun local(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(edmonton).toInstant().toEpochMilli()

    private fun trip(startedAtMs: Long, endedAtMs: Long) = Trip(
        id = 12,
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        status = TripStatus.FINISHED,
        startedBy = TripStartCause.TRUCK,
        truckSeen = true,
        distanceMetres = 30_000.0,
    )

    /** Recorded from 17:00 on Monday 5 October until half past midnight. */
    private val overnight = trip(local("2026-10-05T17:00:12"), local("2026-10-06T00:30:41"))

    private fun problems(form: TripForm, stored: Trip?, now: String): List<FormProblem> =
        formProblems(form, stored, FormCheck(local(now), recordingSinceMs = null), edmonton)

    // ---- Past midnight --------------------------------------------------------------------------

    @Test
    fun `a trip that ran past midnight can be brought back onto the day it started`() {
        val opened = formFor(overnight, edmonton)
        val sameDay = opened.copy(end = LocalTime.of(17, 40)).endingNextDay(false)

        // Left as it opened, 17:40 would be on the 6th: a trip of more than a day.
        assertEquals(
            local("2026-10-06T17:40:00"),
            opened.copy(end = LocalTime.of(17, 40)).endedAtMs(overnight, edmonton),
        )
        assertEquals(local("2026-10-05T17:40:00"), sameDay.endedAtMs(overnight, edmonton))
        assertNull(sameDay.laterEndDate)
        // It can be saved the next morning, when 17:40 on the 6th has not come yet.
        assertEquals(
            emptyList<FormProblem>(),
            problems(sameDay, overnight, now = "2026-10-06T08:00:00"),
        )
    }

    @Test
    fun `the end day switched off and on again is the stored end, to the millisecond`() {
        val opened = formFor(overnight, edmonton)

        val putBack = opened.endingNextDay(false).endingNextDay(true)

        assertEquals(opened, putBack)
        assertEquals(overnight.endedAtMs, putBack.endedAtMs(overnight, edmonton))
        assertEquals(TripEdit(), putBack.toEdit(overnight, edmonton))
    }

    @Test
    fun `a missed trip across midnight is typed in with the switch, and refused without it`() {
        val form =
            TripForm(
                date = LocalDate.of(2026, 10, 5),
                start = LocalTime.of(23, 30),
                end = LocalTime.of(0, 15),
                kilometres = "30",
            )
        val nextDay = form.endingNextDay(true)

        // The form never moves an end to the next day by itself: an end before the start is
        // far more often a slip of the dial.
        assertEquals(
            listOf(FormProblem.END_NOT_AFTER_START),
            problems(form, stored = null, now = "2026-10-06T08:00:00"),
        )
        assertEquals(
            emptyList<FormProblem>(),
            problems(nextDay, stored = null, now = "2026-10-06T08:00:00"),
        )
        assertEquals(local("2026-10-06T00:15:00"), nextDay.endedAtMs(stored = null, edmonton))
        assertEquals(LocalDate.of(2026, 10, 6), nextDay.laterEndDate)
        assertEquals(
            local("2026-10-06T00:15:00"),
            nextDay.toTypedTrip(edmonton)?.endedAtMs,
        )
    }

    @Test
    fun `a trip of today cannot be made to end tomorrow`() {
        val form =
            TripForm(
                date = LocalDate.of(2026, 10, 5),
                start = LocalTime.of(9, 0),
                end = LocalTime.of(9, 40),
                kilometres = "30",
            ).endingNextDay(true)

        assertEquals(
            listOf(FormProblem.IN_THE_FUTURE),
            problems(form, stored = null, now = "2026-10-05T14:00:00"),
        )
    }

    @Test
    fun `a new date moves the end day with it`() {
        val moved = formFor(overnight, edmonton).copy(date = LocalDate.of(2026, 10, 1))

        assertEquals(LocalDate.of(2026, 10, 2), moved.laterEndDate)
        assertEquals(local("2026-10-02T00:30:00"), moved.endedAtMs(overnight, edmonton))
    }

    @Test
    fun `a stored trip that ran over more than one midnight opens as it is stored`() {
        // Never seen, and not something the switch can make: it says "the next day" or not.
        val twoNights = trip(local("2026-10-03T22:00:00"), local("2026-10-05T01:00:00"))

        val opened = formFor(twoNights, edmonton)

        assertEquals(LocalDate.of(2026, 10, 5), opened.laterEndDate)
        assertEquals(TripEdit(), opened.toEdit(twoNights, edmonton))
        assertEquals(LocalDate.of(2026, 10, 4), opened.endingNextDay(true).laterEndDate)
    }

    // ---- The hour that happens twice ------------------------------------------------------------

    // Sunday 1 November 2026: at 02:00 the clocks go back to 01:00, so every time from 01:00
    // to 01:59 happens twice, an hour apart.
    private val firstHalfPastOne = local("2026-11-01T01:30:00")
    private val secondHalfPastOne = firstHalfPastOne + HOUR_MS

    @Test
    fun `a time typed over one in the second of the two hours stays in the second`() {
        // 00:50 before the change until the second 01:30: a trip of 100 minutes.
        val stored = trip(local("2026-11-01T00:50:00"), secondHalfPastOne)

        val later = formFor(stored, edmonton).copy(end = LocalTime.of(1, 40))

        // Ten minutes later than it was, not fifty minutes earlier.
        assertEquals(secondHalfPastOne + 10 * MINUTE_MS, later.endedAtMs(stored, edmonton))
        assertEquals(
            110 * MINUTE_MS,
            checkNotNull(later.endedAtMs(stored, edmonton)) - stored.startedAtMs,
        )
    }

    @Test
    fun `a time typed over one in the first of the two hours stays in the first`() {
        val stored = trip(local("2026-11-01T00:50:00"), firstHalfPastOne)

        val later = formFor(stored, edmonton).copy(end = LocalTime.of(1, 40))

        assertEquals(firstHalfPastOne + 10 * MINUTE_MS, later.endedAtMs(stored, edmonton))
    }

    @Test
    fun `a time moved into the doubled hour is read on the side the stored time is on`() {
        // Stored after the change (02:10 is winter time), and before it (00:55, summer time).
        val after = trip(local("2026-11-01T00:50:00"), local("2026-11-01T02:10:00"))
        val before = trip(local("2026-11-01T00:20:00"), local("2026-11-01T00:55:00"))

        assertEquals(
            secondHalfPastOne,
            formFor(after, edmonton).copy(end = LocalTime.of(1, 30)).endedAtMs(after, edmonton),
        )
        assertEquals(
            firstHalfPastOne,
            formFor(before, edmonton).copy(end = LocalTime.of(1, 30)).endedAtMs(before, edmonton),
        )
    }

    @Test
    fun `a trip that is added in the doubled hour gets the first of the two`() {
        val form =
            TripForm(
                date = LocalDate.of(2026, 11, 1),
                start = LocalTime.of(1, 30),
                end = LocalTime.of(1, 50),
            )

        assertEquals(firstHalfPastOne, form.startedAtMs(stored = null, edmonton))
        assertEquals(firstHalfPastOne + 20 * MINUTE_MS, form.endedAtMs(stored = null, edmonton))
    }

    @Test
    fun `away from a clock change a typed time is not moved by the stored one`() {
        // The stored end is in winter time; the trip is moved to a day in summer time.
        val stored = trip(local("2026-11-02T08:00:00"), local("2026-11-02T08:30:00"))

        val moved = formFor(stored, edmonton).copy(date = LocalDate.of(2026, 10, 5))

        assertEquals(local("2026-10-05T08:30:00"), moved.endedAtMs(stored, edmonton))
    }
}
