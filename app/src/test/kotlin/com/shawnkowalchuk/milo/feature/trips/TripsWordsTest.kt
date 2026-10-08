package com.shawnkowalchuk.milo.feature.trips

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.report.MonthSubmission
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.trip.ByHandMark
import com.shawnkowalchuk.milo.data.trip.CategoryTotals
import com.shawnkowalchuk.milo.data.trip.Tally
import com.shawnkowalchuk.milo.data.trip.TripCorrection
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words the Trips screen chooses since it is laid out as the owner's design draws it: a
 * day's heading, the pill on the month's tile, the sentence of a month with nothing to list,
 * a trip's grey line, and the buttons under a trip.
 */
class TripsWordsTest {
    private val monday = LocalDate.of(2026, 10, 5)

    private fun line(
        id: Long,
        kind: TripKind = TripKind.COUNTED,
        category: TripCategory? = TripCategory.BUSINESS,
        mark: ByHandMark? = null,
        ignored: Boolean = false,
        ranPastSchedule: Boolean = false,
    ) = TripLine(
        id = id,
        startedAtMs = 0,
        endedAtMs = if (kind == TripKind.IN_PROGRESS) null else 60_000,
        distanceMetres = 5_000.0,
        kind = kind,
        category = category.takeIf { kind != TripKind.IN_PROGRESS },
        ranPastSchedule = ranPastSchedule,
        ignored = ignored,
        // As `monthSummary` fills them in: only a counted trip can be marked or edited.
        markableAs =
            if (kind ==
                TripKind.COUNTED
            ) {
                TripCategory.entries - setOfNotNull(category)
            } else {
                emptyList()
            },
        mark = mark,
        editable = kind == TripKind.COUNTED,
    )

    private fun month(days: List<TripDay>, hidden: Int, inProgress: TripLine? = null) =
        MonthSummary(
            totals = CategoryTotals(Tally(0, 0), Tally(0, 0), Tally(0, 0)),
            inProgress = inProgress,
            days = days,
            hiddenLeftOut = hidden,
            unit = DistanceUnit.KILOMETRES,
        )

    // ---- A day's heading ------------------------------------------------------------------------

    @Test
    fun `only today's heading says Today`() {
        val day = TripDay(monday, listOf(line(1)), sessionCount = 1, businessTenths = 50)

        assertTrue(day.heading(today = monday).isToday)
        assertFalse(day.heading(today = monday.plusDays(1)).isToday)
        assertFalse(day.heading(today = monday.minusDays(1)).isToday)
    }

    @Test
    fun `a heading carries the day's own count and Business figure`() {
        val trips = listOf(line(1), line(2, category = TripCategory.PERSONAL))
        val day = TripDay(monday, trips, sessionCount = 2, businessTenths = 50)

        val heading = day.heading(today = monday)

        assertEquals(2, heading.sessionCount)
        assertEquals(50, heading.businessTenths)
        assertEquals(0, heading.notCounted)
    }

    @Test
    fun `a heading says how many listed trips are not counted, and counts them nowhere else`() {
        val trips =
            listOf(
                line(1),
                line(2, kind = TripKind.DELETED),
                line(3, kind = TripKind.DISCARDED),
            )
        // The count and the figure are of the counted trip alone, as `monthSummary` gives them.
        val day = TripDay(monday, trips, sessionCount = 1, businessTenths = 50)

        val heading = day.heading(today = monday)

        assertEquals(2, heading.notCounted)
        assertEquals(1, heading.sessionCount)
        assertEquals(50, heading.businessTenths)
    }

    // ---- The pill, and a month with nothing to list ---------------------------------------------

    @Test
    fun `the pill says whether the month's report has been sent`() {
        val sent =
            SentReport(
                id = 1,
                kind = SentReportKind.MONTH,
                firstDay = LocalDate.of(2026, 10, 1).toEpochDay(),
                lastDay = LocalDate.of(2026, 10, 31).toEpochDay(),
                sentAtMs = 1,
                tripCount = 1,
                distanceMetres = 5_000.0,
                revision = 0,
            )

        assertEquals(R.string.report_not_submitted, pillWordsRes(null))
        assertEquals(R.string.trips_pill_submitted, pillWordsRes(MonthSubmission(sent, sent)))
        // Sent again as a revision, it is still submitted: the line under the figures says when.
        val revision = sent.copy(id = 2, revision = 1)
        assertEquals(R.string.trips_pill_submitted, pillWordsRes(MonthSubmission(sent, revision)))
    }

    @Test
    fun `a month that lists a trip has no sentence in its place`() {
        val day = TripDay(monday, listOf(line(1)), sessionCount = 1, businessTenths = 50)

        assertNull(month(listOf(day), hidden = 0).emptyWordsRes())
        // The trip being recorded is something to list too.
        val open = line(9, kind = TripKind.IN_PROGRESS)
        assertNull(month(emptyList(), hidden = 0, inProgress = open).emptyWordsRes())
    }

    @Test
    fun `a month without any trip says so`() {
        assertEquals(R.string.trips_empty, month(emptyList(), hidden = 0).emptyWordsRes())
    }

    @Test
    fun `a month whose only trips are hidden does not claim to have none`() {
        assertEquals(R.string.trips_empty_counted, month(emptyList(), hidden = 2).emptyWordsRes())
    }

    // ---- A trip's grey line ---------------------------------------------------------------------

    @Test
    fun `a recorded trip's line says what it is saved as, and nothing else`() {
        assertEquals(listOf(R.string.trip_business), line(1).notesRes())
        assertEquals(
            listOf(R.string.trip_personal),
            line(1, category = TripCategory.PERSONAL).notesRes(),
        )
        assertEquals(
            listOf(R.string.trip_business_ran_past),
            line(1, ranPastSchedule = true).notesRes(),
        )
    }

    @Test
    fun `figures that are typed are said after what the trip is saved as`() {
        assertEquals(
            listOf(R.string.trip_business, R.string.trips_note_edited),
            line(1, mark = ByHandMark.EDITED).notesRes(),
        )
        assertEquals(
            listOf(R.string.trip_personal, R.string.trips_note_added),
            line(1, category = TripCategory.PERSONAL, mark = ByHandMark.ADDED).notesRes(),
        )
    }

    @Test
    fun `why a trip is not counted comes last, after its other notes`() {
        assertEquals(
            listOf(R.string.trip_business, R.string.trips_note_added, R.string.trips_deleted_note),
            line(1, kind = TripKind.DELETED, mark = ByHandMark.ADDED).notesRes(),
        )
        assertEquals(
            listOf(R.string.trip_personal, R.string.trips_discarded_note),
            line(1, kind = TripKind.DISCARDED, category = TripCategory.PERSONAL).notesRes(),
        )
        assertEquals(
            listOf(R.string.trip_personal, R.string.trips_ignored_note),
            line(1, TripKind.DISCARDED, TripCategory.PERSONAL, ignored = true).notesRes(),
        )
    }

    @Test
    fun `the trip in progress has no notes`() {
        val open = line(1, kind = TripKind.IN_PROGRESS, mark = ByHandMark.EDITED)

        assertEquals(emptyList<Int>(), open.notesRes())
    }

    // ---- The buttons under a trip ---------------------------------------------------------------

    @Test
    fun `a finished trip shows no button until it is pressed`() {
        assertEquals(emptyList<TripAction>(), line(1).actions(pressed = false))
    }

    @Test
    fun `pressed, a finished trip offers Edit, Label, the other category and Delete, in order`() {
        assertEquals(
            listOf(
                TripAction.Edit,
                TripAction.Label,
                TripAction.Mark(TripCategory.PERSONAL),
                TripAction.Correct(TripCorrection.DELETE),
            ),
            line(1).actions(pressed = true),
        )
        assertEquals(
            TripAction.Mark(TripCategory.BUSINESS),
            line(1, category = TripCategory.PERSONAL).actions(pressed = true)[2],
        )
    }

    @Test
    fun `a trip that is not sorted yet offers both categories`() {
        assertEquals(
            listOf(
                TripAction.Edit,
                TripAction.Label,
                TripAction.Mark(TripCategory.BUSINESS),
                TripAction.Mark(TripCategory.PERSONAL),
                TripAction.Correct(TripCorrection.DELETE),
            ),
            line(1, category = null).actions(pressed = true),
        )
    }

    @Test
    fun `a deleted trip shows Restore straight away, and nothing else`() {
        val deleted = line(1, kind = TripKind.DELETED)

        for (pressed in listOf(false, true)) {
            assertEquals(
                listOf(TripAction.Correct(TripCorrection.RESTORE)),
                deleted.actions(pressed),
            )
        }
    }

    @Test
    fun `a discarded trip shows Count this trip straight away, and nothing else`() {
        val discarded = line(1, kind = TripKind.DISCARDED)

        for (pressed in listOf(false, true)) {
            assertEquals(
                listOf(TripAction.Correct(TripCorrection.COUNT)),
                discarded.actions(pressed),
            )
        }
    }

    @Test
    fun `the trip in progress has no button at all`() {
        val open = line(1, kind = TripKind.IN_PROGRESS)

        assertEquals(emptyList<TripAction>(), open.actions(pressed = false))
        assertEquals(emptyList<TripAction>(), open.actions(pressed = true))
    }

    @Test
    fun `a button is drawn with a short word and read out with what it does`() {
        val words =
            listOf(
                TripAction.Edit,
                TripAction.Label,
                TripAction.Mark(TripCategory.BUSINESS),
                TripAction.Mark(TripCategory.PERSONAL),
                TripAction.Correct(TripCorrection.DELETE),
                TripAction.Correct(TripCorrection.RESTORE),
                TripAction.Correct(TripCorrection.COUNT),
            ).map { it.wordsRes() to it.spokenRes() }

        assertEquals(
            listOf(
                R.string.trips_button_edit to R.string.trips_action_edit,
                R.string.trips_button_label to R.string.trips_action_label,
                R.string.trip_business to R.string.trips_action_mark_business,
                R.string.trip_personal to R.string.trips_action_mark_personal,
                R.string.trips_button_delete to R.string.trips_action_delete,
                R.string.trips_action_restore to R.string.trips_action_restore,
                R.string.trips_action_count to R.string.trips_action_count,
            ),
            words,
        )
    }
}
