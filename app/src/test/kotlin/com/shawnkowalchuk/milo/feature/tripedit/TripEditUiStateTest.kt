package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.trip.RecordedValues
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripEdit
import com.shawnkowalchuk.milo.data.trip.editedTrip
import com.shawnkowalchuk.milo.data.trip.tripAddedByHand
import java.time.DayOfWeek
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
 * What the edit screen shows: the form's fields, the Business or Personal a save would store
 * and who chose it, and what is offered. The form was opened on Tuesday 6 October 2026 at
 * 14:32 in Edmonton.
 */
class TripEditUiStateTest {
    private val edmonton = ZoneId.of("America/Edmonton")

    private fun local(dateTime: String): Long =
        LocalDateTime.parse(dateTime).atZone(edmonton).toInstant().toEpochMilli()

    private val openedAtMs = local("2026-10-06T14:32:40")

    /** Recorded on Monday morning, inside the work hours. */
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

    private fun session(trip: Trip?, schedule: WorkSchedule? = DEFAULT_WORK_SCHEDULE) =
        EditSession(trip, schedule, edmonton, openedAtMs, DistanceUnit.KILOMETRES)

    private fun shown(
        form: TripForm,
        trip: Trip? = stored,
        schedule: WorkSchedule? = DEFAULT_WORK_SCHEDULE,
        check: FormCheck? = null,
    ): TripEditUiState.Ready = tripEditUiState(
        session = session(trip, schedule),
        form = form,
        check = check,
        saveFailed = false,
        refusals = 0,
        closing = false,
    )

    private val opened = formFor(stored, edmonton, DistanceUnit.KILOMETRES)
    private val blank = blankForm(openedAtMs, edmonton, DistanceUnit.KILOMETRES)

    // ---- The fields -----------------------------------------------------------------------------

    @Test
    fun `a stored trip's form shows the trip, and leaves the distance to the screen to write`() {
        val state = shown(opened)

        assertFalse(state.adding)
        assertEquals(LocalDate.of(2026, 10, 5), state.date)
        assertEquals(LocalTime.of(8, 14), state.start)
        assertEquals(LocalTime.of(8, 39), state.end)
        assertEquals("12 Shop Rd, Edmonton", state.from)
        assertEquals("", state.to)
        assertNull(state.kilometres)
        assertEquals(12_344.7, state.storedMetres)
        assertEquals(emptyList<FormProblem>(), state.problems)
    }

    @Test
    fun `what was typed is what a field starts with after the phone is turned`() {
        val state = shown(opened.copy(from = "Home", to = "Site", kilometres = "13"))

        assertEquals("Home", state.from)
        assertEquals("Site", state.to)
        assertEquals("13", state.kilometres)
    }

    @Test
    fun `the form for a missed trip is empty, and its dials open on the time it is now`() {
        val state = shown(blank, trip = null)

        assertTrue(state.adding)
        assertEquals(LocalDate.of(2026, 10, 6), state.date)
        assertNull(state.start)
        assertNull(state.end)
        assertEquals(LocalTime.of(14, 32), state.startDial)
        assertEquals(LocalTime.of(14, 32), state.endDial)
        assertEquals("", state.from)
        assertNull(state.kilometres)
        assertNull(state.storedMetres)
        assertNull(state.recorded)
    }

    @Test
    fun `the end's dial opens on the start once that is chosen, and each dial on its own time`() {
        val startChosen = shown(blank.copy(start = LocalTime.of(9, 0)), trip = null)

        assertEquals(LocalTime.of(9, 0), startChosen.endDial)
        assertEquals(LocalTime.of(8, 14), shown(opened).startDial)
        assertEquals(LocalTime.of(8, 39), shown(opened).endDial)
    }

    @Test
    fun `no day after the one the form was opened on can be picked`() {
        assertEquals(LocalDate.of(2026, 10, 6), shown(opened).latestDate)
    }

    @Test
    fun `problems are shown only after Save was pressed, and then follow the form`() {
        val check = FormCheck(openedAtMs, recordingSinceMs = null)
        val wrong = opened.copy(end = LocalTime.of(8, 0))

        assertEquals(emptyList<FormProblem>(), shown(wrong).problems)
        assertEquals(
            listOf(FormProblem.END_NOT_AFTER_START),
            shown(wrong, check = check).problems,
        )
        assertEquals(emptyList<FormProblem>(), shown(opened, check = check).problems)
    }

    // ---- Business or Personal: what is shown is what is stored ----------------------------------

    @Test
    fun `an untouched trip shows what it was saved as`() {
        val state = shown(opened)

        assertEquals(TripCategory.BUSINESS, state.category)
        assertEquals(KindSource.AS_SAVED, state.kindSource)
    }

    @Test
    fun `a trip keeps what it was saved as while its start is the same, whatever the schedule`() {
        // Monday is no longer a tracked day. The trip was Business when it was saved.
        val changed = DEFAULT_WORK_SCHEDULE.withTracked(DayOfWeek.MONDAY, false)

        val state = shown(opened.copy(end = LocalTime.of(9, 0)), schedule = changed)

        assertEquals(TripCategory.BUSINESS, state.category)
        assertEquals(KindSource.AS_SAVED, state.kindSource)
    }

    @Test
    fun `a changed start is sorted again by the schedule, and the form says so`() {
        val state = shown(opened.copy(start = LocalTime.of(7, 50)))

        assertEquals(TripCategory.PERSONAL, state.category)
        assertEquals(KindSource.BY_SCHEDULE, state.kindSource)
    }

    @Test
    fun `a choice in the form, or one made before, is shown as Shawn's own`() {
        val pressed = shown(opened.copy(chosenCategory = TripCategory.PERSONAL))
        val before =
            shown(
                opened.copy(start = LocalTime.of(7, 50)),
                trip = stored.copy(categorySetByHand = true),
            )

        assertEquals(
            TripCategory.PERSONAL to KindSource.BY_YOU,
            pressed.category to pressed.kindSource,
        )
        // Moved out of the work hours, and still Business: it was set by hand.
        assertEquals(
            TripCategory.BUSINESS to KindSource.BY_YOU,
            before.category to before.kindSource,
        )
    }

    @Test
    fun `a missed trip has no category until its start is chosen, then the schedule's`() {
        val noStart = shown(blank, trip = null)
        val morning = shown(blank.copy(start = LocalTime.of(9, 0)), trip = null)
        val evening = shown(blank.copy(start = LocalTime.of(19, 0)), trip = null)

        assertEquals(null to KindSource.NEEDS_START, noStart.category to noStart.kindSource)
        assertEquals(
            TripCategory.BUSINESS to KindSource.BY_SCHEDULE,
            morning.category to morning.kindSource,
        )
        assertEquals(
            TripCategory.PERSONAL to KindSource.BY_SCHEDULE,
            evening.category to evening.kindSource,
        )
    }

    @Test
    fun `a missed trip's category can be chosen before its start, and stays chosen`() {
        val chosen = blank.copy(chosenCategory = TripCategory.PERSONAL)

        val early = shown(chosen, trip = null)
        val later = shown(chosen.copy(start = LocalTime.of(9, 0)), trip = null)

        assertEquals(TripCategory.PERSONAL to KindSource.BY_YOU, early.category to early.kindSource)
        assertEquals(TripCategory.PERSONAL to KindSource.BY_YOU, later.category to later.kindSource)
    }

    @Test
    fun `without a readable schedule the form says it cannot tell`() {
        val added = shown(blank.copy(start = LocalTime.of(9, 0)), trip = null, schedule = null)
        val moved = shown(opened.copy(start = LocalTime.of(7, 50)), schedule = null)

        assertEquals(null to KindSource.NOT_KNOWN, added.category to added.kindSource)
        assertEquals(null to KindSource.NOT_KNOWN, moved.category to moved.kindSource)
    }

    @Test
    fun `the category the form shows is the one a save stores`() {
        val forms =
            listOf(
                opened,
                opened.copy(start = LocalTime.of(7, 50)),
                opened.copy(end = LocalTime.of(16, 45)),
                opened.copy(date = LocalDate.of(2026, 10, 3)),
                opened.copy(chosenCategory = TripCategory.PERSONAL),
                opened.copy(start = LocalTime.of(7, 50), chosenCategory = TripCategory.BUSINESS),
            )
        for (form in forms) {
            val saved =
                editedTrip(stored, form.toEdit(stored, edmonton), DEFAULT_WORK_SCHEDULE, edmonton)

            assertEquals("$form", saved.category, shown(form).category)
        }

        val missed =
            blank.copy(
                date = LocalDate.of(2026, 10, 5),
                start = LocalTime.of(16, 0),
                end = LocalTime.of(17, 0),
                kilometres = "40",
            )
        for (form in listOf(missed, missed.copy(chosenCategory = TripCategory.PERSONAL))) {
            val typed = checkNotNull(form.toTypedTrip(edmonton))
            val added = tripAddedByHand(typed, DEFAULT_WORK_SCHEDULE, edmonton)

            assertEquals("$form", added.category, shown(form, trip = null).category)
        }
    }

    // ---- What is offered ------------------------------------------------------------------------

    @Test
    fun `the recorded values are offered for an edited trip, and for no other`() {
        val edited =
            editedTrip(
                stored,
                TripEdit(distanceMetres = 20_000.0),
                DEFAULT_WORK_SCHEDULE,
                edmonton,
            )
        val addedByHand = stored.copy(addedByHand = true, startAddressByHand = true)

        assertNull(shown(opened).recorded)
        assertEquals(
            RecordedValues(stored.startedAtMs, local("2026-10-05T08:39:02"), 12_344.7),
            shown(formFor(edited, edmonton, DistanceUnit.KILOMETRES), trip = edited).recorded,
        )
        assertNull(
            shown(
                formFor(addedByHand, edmonton, DistanceUnit.KILOMETRES),
                trip = addedByHand,
            ).recorded,
        )
        assertTrue(
            shown(
                formFor(addedByHand, edmonton, DistanceUnit.KILOMETRES),
                trip = addedByHand,
            ).addedByHand,
        )
    }

    @Test
    fun `a stored trip that ran past midnight says so`() {
        val overnight = stored.copy(endedAtMs = local("2026-10-06T00:10:00"))

        assertTrue(
            shown(
                formFor(overnight, edmonton, DistanceUnit.KILOMETRES),
                trip = overnight,
            ).endsNextDay,
        )
        assertFalse(shown(opened).endsNextDay)
    }
}
