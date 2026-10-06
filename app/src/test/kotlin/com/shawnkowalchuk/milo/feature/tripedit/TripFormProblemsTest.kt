package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What stops the edit form from being saved, rule by rule. The form is filled in as on Monday
 * 5 October 2026 in Edmonton, and checked at 14:00 that day unless a test says otherwise.
 */
class TripFormProblemsTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private val monday = LocalDate.of(2026, 10, 5)

    private fun local(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(edmonton).toInstant().toEpochMilli()

    private val twoInTheAfternoon = FormCheck(local("2026-10-05T14:00:00"), recordingSinceMs = null)

    /** A missed trip that is filled in properly: forty minutes and 23.4 km in the morning. */
    private val filledIn =
        TripForm(
            date = monday,
            start = LocalTime.of(9, 0),
            end = LocalTime.of(9, 40),
            kilometres = "23.4",
        )

    private fun problems(
        form: TripForm,
        check: FormCheck = twoInTheAfternoon,
        stored: Trip? = null,
    ): List<FormProblem> = formProblems(form, stored, check, edmonton)

    @Test
    fun `a form that is filled in properly has no problem`() {
        assertEquals(emptyList<FormProblem>(), problems(filledIn))
    }

    @Test
    fun `an empty form lacks its two times and its distance, and nothing else is said of it`() {
        assertEquals(
            listOf(
                FormProblem.START_MISSING,
                FormProblem.END_MISSING,
                FormProblem.DISTANCE_MISSING,
            ),
            problems(TripForm(date = monday)),
        )
    }

    // ---- The end is after the start -------------------------------------------------------------

    @Test
    fun `an end before the start, or on it, is refused`() {
        val before = filledIn.copy(end = LocalTime.of(8, 59))
        val same = filledIn.copy(end = LocalTime.of(9, 0))

        assertEquals(listOf(FormProblem.END_NOT_AFTER_START), problems(before))
        // A trip of no length at all is a slip of the dial too.
        assertEquals(listOf(FormProblem.END_NOT_AFTER_START), problems(same))
    }

    @Test
    fun `an end one minute after the start is taken`() {
        val form = filledIn.copy(end = LocalTime.of(9, 1), kilometres = "1")

        assertEquals(emptyList<FormProblem>(), problems(form))
    }

    // ---- Not in the future ----------------------------------------------------------------------

    @Test
    fun `a trip that ends after now is refused, and one that ends now is taken`() {
        val endsLater = filledIn.copy(start = LocalTime.of(13, 30), end = LocalTime.of(14, 1))
        val endsNow = filledIn.copy(start = LocalTime.of(13, 30), end = LocalTime.of(14, 0))

        assertEquals(listOf(FormProblem.IN_THE_FUTURE), problems(endsLater))
        assertEquals(emptyList<FormProblem>(), problems(endsNow))
    }

    @Test
    fun `a trip on a day that has not come is refused`() {
        val tomorrow = filledIn.copy(date = monday.plusDays(1))

        assertEquals(listOf(FormProblem.IN_THE_FUTURE), problems(tomorrow))
    }

    @Test
    fun `a start in the future is refused even before an end is chosen`() {
        val form = TripForm(date = monday, start = LocalTime.of(15, 0), kilometres = "5")

        assertEquals(
            listOf(FormProblem.END_MISSING, FormProblem.IN_THE_FUTURE),
            problems(form),
        )
    }

    // ---- Not over the trip that is being recorded -----------------------------------------------

    @Test
    fun `a trip that ends after the recording started overlaps it`() {
        val recordingSinceHalfPastNine =
            twoInTheAfternoon.copy(recordingSinceMs = local("2026-10-05T09:30:00"))

        assertEquals(
            listOf(FormProblem.OVERLAPS_RECORDING),
            problems(filledIn, recordingSinceHalfPastNine),
        )
    }

    @Test
    fun `a trip that ends as the recording starts, or earlier, does not overlap it`() {
        val recordingSinceEndOfTrip =
            twoInTheAfternoon.copy(recordingSinceMs = local("2026-10-05T09:40:00"))
        val recordingSinceNoon =
            twoInTheAfternoon.copy(recordingSinceMs = local("2026-10-05T12:00:00"))

        assertEquals(emptyList<FormProblem>(), problems(filledIn, recordingSinceEndOfTrip))
        assertEquals(emptyList<FormProblem>(), problems(filledIn, recordingSinceNoon))
    }

    @Test
    fun `a trip that starts after the recording started overlaps it`() {
        // The recording runs on until some moment still to come, so anything after its start
        // lies inside it.
        val recordingSinceEight =
            twoInTheAfternoon.copy(recordingSinceMs = local("2026-10-05T08:00:00"))

        assertEquals(
            listOf(FormProblem.OVERLAPS_RECORDING),
            problems(filledIn, recordingSinceEight),
        )
    }

    // ---- The distance ---------------------------------------------------------------------------

    @Test
    fun `a distance that is no number, or less than zero, is refused`() {
        assertEquals(
            listOf(FormProblem.DISTANCE_NOT_A_NUMBER),
            problems(filledIn.copy(kilometres = "far")),
        )
        assertEquals(
            listOf(FormProblem.DISTANCE_NEGATIVE),
            problems(filledIn.copy(kilometres = "-5")),
        )
        assertEquals(
            listOf(FormProblem.DISTANCE_MISSING),
            problems(filledIn.copy(kilometres = " ")),
        )
    }

    @Test
    fun `a distance of zero is taken`() {
        assertEquals(emptyList<FormProblem>(), problems(filledIn.copy(kilometres = "0")))
    }

    @Test
    fun `a distance the truck could not cover in the time is refused`() {
        // Forty minutes. 120 km in that time is exactly 180 km/h, which is the limit and not
        // over it; "234" for "23.4" is far over.
        assertEquals(emptyList<FormProblem>(), problems(filledIn.copy(kilometres = "120")))
        assertEquals(
            listOf(FormProblem.DISTANCE_TOO_FAST),
            problems(filledIn.copy(kilometres = "120.1")),
        )
        assertEquals(
            listOf(FormProblem.DISTANCE_TOO_FAST),
            problems(filledIn.copy(kilometres = "234")),
        )
    }

    @Test
    fun `more than the longest trip is refused however long it took`() {
        val allDay = filledIn.copy(start = LocalTime.of(0, 0), end = LocalTime.of(13, 0))

        assertEquals(emptyList<FormProblem>(), problems(allDay.copy(kilometres = "2000")))
        assertEquals(
            listOf(FormProblem.DISTANCE_TOO_LONG),
            problems(allDay.copy(kilometres = "2000.1")),
        )
    }

    @Test
    fun `the speed is not judged while the times are not in order`() {
        val form = filledIn.copy(end = LocalTime.of(8, 0), kilometres = "500")

        assertEquals(listOf(FormProblem.END_NOT_AFTER_START), problems(form))
    }

    // ---- A stored trip --------------------------------------------------------------------------

    @Test
    fun `a stored trip that is opened and not touched has no problem`() {
        val stored =
            Trip(
                id = 12,
                startedAtMs = local("2026-10-05T08:14:27"),
                endedAtMs = local("2026-10-05T08:39:02"),
                status = TripStatus.FINISHED,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                distanceMetres = 12_344.7,
            )

        assertEquals(emptyList<FormProblem>(), problems(formFor(stored, edmonton), stored = stored))
    }

    @Test
    fun `an edit that makes a recorded trip too fast for its distance is refused`() {
        val stored =
            Trip(
                id = 12,
                startedAtMs = local("2026-10-05T08:14:27"),
                endedAtMs = local("2026-10-05T08:39:02"),
                status = TripStatus.FINISHED,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                distanceMetres = 30_000.0,
            )
        // The distance is untouched, and the end is moved to five minutes after the start.
        val form = formFor(stored, edmonton).copy(end = LocalTime.of(8, 19))

        assertEquals(listOf(FormProblem.DISTANCE_TOO_FAST), problems(form, stored = stored))
    }

    @Test
    fun `a stored trip whose clock was wrong has to be put right before it is saved again`() {
        val stored =
            Trip(
                id = 12,
                startedAtMs = local("2026-10-05T08:40:00"),
                endedAtMs = local("2026-10-05T08:39:02"),
                status = TripStatus.FINISHED,
                startedBy = TripStartCause.TRUCK,
                truckSeen = true,
                distanceMetres = 12_344.7,
            )

        assertEquals(
            listOf(FormProblem.END_NOT_AFTER_START),
            problems(formFor(stored, edmonton), stored = stored),
        )
    }

    // ---- The words ------------------------------------------------------------------------------

    @Test
    fun `every problem has a sentence, and the two limits are quoted from the rule`() {
        val sentences = FormProblem.entries.associateWith { it.sentence() }

        assertEquals(FormProblem.entries.size, sentences.values.map { it.text }.toSet().size)
        assertEquals(
            Sentence(R.string.trip_edit_problem_distance_too_long, MAX_TRIP_KILOMETRES),
            sentences[FormProblem.DISTANCE_TOO_LONG],
        )
        assertEquals(
            Sentence(R.string.trip_edit_problem_distance_too_fast, 180),
            sentences[FormProblem.DISTANCE_TOO_FAST],
        )
    }

    @Test
    fun `every source of Business or Personal has a line of its own`() {
        assertEquals(KindSource.entries.size, KindSource.entries.map { it.noteRes() }.toSet().size)
    }
}
