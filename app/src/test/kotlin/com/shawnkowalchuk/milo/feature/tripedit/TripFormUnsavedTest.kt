package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether the form holds something that would be lost if the screen were left: the question
 * "Leave without saving?" is asked exactly then. The form was opened on Tuesday 6 October 2026
 * at 14:32 in Edmonton.
 */
class TripFormUnsavedTest {
    private val edmonton = ZoneId.of("America/Edmonton")

    private fun local(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(edmonton).toInstant().toEpochMilli()

    private val openedAtMs = local("2026-10-06T14:32:40")

    private val stored =
        Trip(
            id = 12,
            startedAtMs = local("2026-10-05T08:14:27"),
            endedAtMs = local("2026-10-05T08:39:02"),
            status = TripStatus.FINISHED,
            startedBy = TripStartCause.TRUCK,
            truckSeen = true,
            distanceMetres = 12_344.7,
            startAddress = "12 Shop Rd, Edmonton",
            endAddress = null,
            category = TripCategory.BUSINESS,
        )

    private val opened = formFor(stored, edmonton, DistanceUnit.KILOMETRES)
    private val blank = blankForm(openedAtMs, edmonton, DistanceUnit.KILOMETRES)

    private fun unsaved(form: TripForm): Boolean = form.holdsUnsavedWork(opened, stored)

    private fun unsavedWhenAdding(form: TripForm): Boolean =
        form.holdsUnsavedWork(blank, stored = null)

    @Test
    fun `a form that was opened and not touched has nothing to lose`() {
        assertFalse(unsaved(opened))
        assertFalse(unsavedWhenAdding(blank))
    }

    @Test
    fun `anything picked or typed is something to lose`() {
        val changes =
            listOf(
                opened.copy(date = LocalDate.of(2026, 10, 2)),
                opened.copy(start = LocalTime.of(8, 0)),
                opened.copy(end = LocalTime.of(9, 0)),
                opened.endingNextDay(true),
                opened.copy(from = "Home"),
                opened.copy(from = ""),
                opened.copy(to = "Site 7"),
                opened.copy(kilometres = "13"),
                opened.copy(chosenCategory = TripCategory.PERSONAL),
            )

        for (form in changes) assertTrue("$form", unsaved(form))
    }

    @Test
    fun `a missed trip that has been begun is something to lose`() {
        val begun =
            listOf(
                blank.copy(date = LocalDate.of(2026, 10, 5)),
                blank.copy(start = LocalTime.of(9, 0)),
                blank.copy(end = LocalTime.of(9, 40)),
                blank.endingNextDay(true),
                blank.copy(from = "Shop"),
                blank.copy(to = "Site 7"),
                blank.copy(kilometres = "23.4"),
                blank.copy(chosenCategory = TripCategory.BUSINESS),
            )

        for (form in begun) assertTrue("$form", unsavedWhenAdding(form))
    }

    @Test
    fun `what is changed and put back is nothing to lose`() {
        val putBack =
            listOf(
                opened.copy(start = LocalTime.of(8, 0)).copy(start = LocalTime.of(8, 14)),
                opened.copy(date = LocalDate.of(2026, 10, 2)).copy(date = opened.date),
                opened.endingNextDay(true).endingNextDay(false),
                // The address as it stood, and the distance as the form showed it.
                opened.copy(from = " 12 Shop Rd, Edmonton "),
                opened.copy(to = "  "),
                opened.copy(kilometres = "12.3"),
            )

        for (form in putBack) assertFalse("$form", unsaved(form))
        assertFalse(unsavedWhenAdding(blank.copy(from = " ", to = "", kilometres = " ")))
    }

    @Test
    fun `a distance field that a save would refuse is still something typed`() {
        assertTrue(unsaved(opened.copy(kilometres = "")))
        assertTrue(unsaved(opened.copy(kilometres = "abc")))
        assertTrue(unsavedWhenAdding(blank.copy(kilometres = "abc")))
    }

    // ---- As the screen is told ------------------------------------------------------------------

    private fun shown(form: TripForm, closing: Boolean = false, savedStartMs: Long? = null) =
        tripEditUiState(
            session = EditSession(stored, DEFAULT_WORK_SCHEDULE, edmonton, openedAtMs),
            form = form,
            check = null,
            saveFailed = false,
            refusals = 0,
            closing = closing,
            savedStartMs = savedStartMs,
        )

    @Test
    fun `the screen says it holds unsaved work exactly while the form differs from the trip`() {
        assertFalse(shown(opened).unsaved)
        assertTrue(shown(opened.copy(kilometres = "13")).unsaved)
        assertFalse(shown(opened.copy(kilometres = "13").copy(kilometres = "12.3")).unsaved)
    }

    @Test
    fun `a form that has been stored holds nothing unsaved, and says where the trip is now`() {
        val moved = opened.copy(date = LocalDate.of(2026, 9, 29))
        val savedStartMs = moved.startedAtMs(stored, edmonton)

        val state = shown(moved, closing = true, savedStartMs = savedStartMs)

        // No question on the way out after a save.
        assertFalse(state.unsaved)
        assertTrue(state.closing)
        assertEquals(local("2026-09-29T08:14:00"), state.savedStartMs)
        assertNull(shown(moved).savedStartMs)
    }

    @Test
    fun `the session's opening form is the stored trip's, or the empty one on that day`() {
        val editing = EditSession(stored, DEFAULT_WORK_SCHEDULE, edmonton, openedAtMs)
        val adding = EditSession(stored = null, DEFAULT_WORK_SCHEDULE, edmonton, openedAtMs)

        assertEquals(opened, editing.openedForm())
        assertEquals(TripForm(date = LocalDate.of(2026, 10, 6)), adding.openedForm())
    }
}
