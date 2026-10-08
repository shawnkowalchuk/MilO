package com.shawnkowalchuk.milo.feature.tripedit

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.trip.RecordedValues
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// The edit screen as Android Studio draws it, in the states that are slow to reach by hand:
// the drawing's own trip, a form that Save refused, and the empty form for a missed trip. In a
// file of its own to keep the screen's file under the size limit (ENGINEERING_STANDARDS
// section 3). Sample values are written inline because a preview is never shown to a user or
// shipped.

/** 2026-10-06 13:42 UTC: the drawing's trip starts at 7:42 in the morning, in Edmonton. */
private const val DRAWN_START_MS = 1_791_294_120_000

private val edmonton = ZoneId.of("America/Edmonton")

/** The trip of the owner's drawing, opened after its distance was edited once. */
private val drawn =
    TripEditUiState.Ready(
        adding = false,
        addedByHand = false,
        zone = edmonton,
        date = LocalDate.of(2026, 10, 6),
        latestDate = LocalDate.of(2026, 10, 7),
        start = LocalTime.of(7, 42),
        end = LocalTime.of(8, 6),
        startDial = LocalTime.of(7, 42),
        endDial = LocalTime.of(8, 6),
        endsNextDay = false,
        laterEndDate = null,
        from = "Shop, 63 Ave NW",
        to = "Windermere site",
        kilometres = null,
        storedMetres = 18_600.0,
        unit = DistanceUnit.KILOMETRES,
        category = TripCategory.BUSINESS,
        kindSource = KindSource.AS_SAVED,
        problems = emptyList(),
        saveFailed = false,
        refusals = 0,
        recorded = RecordedValues(DRAWN_START_MS, DRAWN_START_MS + 1_440_000, 18_400.0),
        unsaved = false,
        closing = false,
        savedStartMs = null,
    )

@Composable
private fun EditPreview(state: TripEditUiState) {
    MiloTheme {
        Surface {
            TripEditContent(
                state = state,
                actions =
                    TripEditActions({}, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}),
                onBack = {},
            )
        }
    }
}

@Preview
@Composable
private fun TripEditPreview() {
    EditPreview(drawn)
}

/** Save was pressed on a form with an end before its start and a distance that cannot be. */
@Preview
@Composable
private fun TripEditRefusedPreview() {
    EditPreview(
        drawn.copy(
            end = LocalTime.of(7, 30),
            endDial = LocalTime.of(7, 30),
            kilometres = "186",
            category = TripCategory.PERSONAL,
            kindSource = KindSource.BY_YOU,
            problems = listOf(FormProblem.END_NOT_AFTER_START, FormProblem.DISTANCE_TOO_FAST),
            saveFailed = true,
            refusals = 1,
            unsaved = true,
        ),
    )
}

/** A trip that ran past midnight, with its date changed since it was recorded. */
@Preview
@Composable
private fun TripEditNextDayPreview() {
    EditPreview(
        drawn.copy(
            date = LocalDate.of(2026, 10, 5),
            start = LocalTime.of(23, 40),
            end = LocalTime.of(0, 25),
            endsNextDay = true,
            laterEndDate = LocalDate.of(2026, 10, 6),
            kindSource = KindSource.BY_SCHEDULE,
            unsaved = true,
        ),
    )
}

/** The empty form for a trip MilO missed. */
@Preview
@Composable
private fun TripAddPreview() {
    EditPreview(
        drawn.copy(
            adding = true,
            date = LocalDate.of(2026, 10, 7),
            start = null,
            end = null,
            from = "",
            to = "",
            storedMetres = null,
            category = null,
            kindSource = KindSource.NEEDS_START,
            recorded = null,
        ),
    )
}
