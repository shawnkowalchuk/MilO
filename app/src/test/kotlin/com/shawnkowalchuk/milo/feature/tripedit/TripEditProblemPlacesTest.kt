package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the edit screen says what a press on Save found wrong: beside what is to be put right,
 * and never in a list somewhere else. A missed trip is being added on Monday 5 October 2026 at
 * 14:00 in Edmonton.
 */
class TripEditProblemPlacesTest {
    private val edmonton = ZoneId.of("America/Edmonton")
    private val nowMs =
        LocalDateTime.parse("2026-10-05T14:00:00").atZone(edmonton).toInstant().toEpochMilli()

    private val filledIn =
        TripForm(
            date = LocalDate.of(2026, 10, 5),
            start = LocalTime.of(9, 0),
            end = LocalTime.of(9, 40),
            kilometres = "23.4",
            unit = DistanceUnit.KILOMETRES,
        )

    /** The screen after a press on Save at 14:00, with a trip recorded since [recordingSince]. */
    private fun afterSave(form: TripForm, recordingSince: Long? = null) = tripEditUiState(
        session =
            EditSession(
                stored = null,
                DEFAULT_WORK_SCHEDULE,
                edmonton,
                nowMs,
                DistanceUnit.KILOMETRES,
            ),
        form = form,
        check = FormCheck(nowMs, recordingSince),
        saveFailed = false,
        refusals = 1,
        closing = false,
    )

    @Test
    fun `every problem belongs to the card it is put right in`() {
        val inWhen =
            setOf(
                FormProblem.START_MISSING,
                FormProblem.END_MISSING,
                FormProblem.END_NOT_AFTER_START,
                FormProblem.IN_THE_FUTURE,
                FormProblem.OVERLAPS_RECORDING,
            )

        for (problem in FormProblem.entries) {
            val expected = if (problem in inWhen) FormPart.TIMES else FormPart.DISTANCE
            assertEquals("$problem", expected, problem.part)
        }
    }

    @Test
    fun `what is wrong with the times is said in the When card, all of it`() {
        // Tomorrow, while a trip has been recorded since noon: two things, both about the times.
        val state =
            afterSave(
                filledIn.copy(date = LocalDate.of(2026, 10, 6)),
                recordingSince = nowMs - 7_200_000,
            )

        assertEquals(
            listOf(FormProblem.IN_THE_FUTURE, FormProblem.OVERLAPS_RECORDING),
            state.timeProblems,
        )
        assertNull(state.distanceProblem)
        assertEquals(FormPart.TIMES, state.firstProblemPart)
    }

    @Test
    fun `what is wrong with the distance is said under its field`() {
        val state = afterSave(filledIn.copy(kilometres = "234"))

        assertEquals(emptyList<FormProblem>(), state.timeProblems)
        assertEquals(FormProblem.DISTANCE_TOO_FAST, state.distanceProblem)
        assertEquals(FormPart.DISTANCE, state.firstProblemPart)
    }

    @Test
    fun `with something wrong in both cards the screen moves to the first, the times`() {
        val state =
            afterSave(TripForm(date = LocalDate.of(2026, 10, 5), unit = DistanceUnit.KILOMETRES))

        assertEquals(
            listOf(FormProblem.START_MISSING, FormProblem.END_MISSING),
            state.timeProblems,
        )
        assertEquals(FormProblem.DISTANCE_MISSING, state.distanceProblem)
        // Every problem has its place: none is left over for a list of its own.
        assertEquals(
            state.problems,
            state.timeProblems + listOfNotNull(state.distanceProblem),
        )
        assertEquals(FormPart.TIMES, state.firstProblemPart)
    }

    @Test
    fun `a form with nothing wrong has no place to move to but the Save button`() {
        val state = afterSave(filledIn)

        assertEquals(emptyList<FormProblem>(), state.problems)
        assertNull(state.firstProblemPart)
    }

    @Test
    fun `an end that is not after the start names the switch for a trip past midnight`() {
        assertEquals(
            Sentence(
                R.string.trip_edit_problem_end_not_after_start,
                names = R.string.trip_edit_ends_next_day,
            ),
            FormProblem.END_NOT_AFTER_START.sentence(DistanceUnit.KILOMETRES),
        )
        // A sentence quotes a number or a name, never both: the screen fills in one argument.
        for (problem in FormProblem.entries) {
            val sentence = problem.sentence(DistanceUnit.KILOMETRES)
            assertTrue("$problem", sentence.number == null || sentence.names == null)
        }
    }
}
