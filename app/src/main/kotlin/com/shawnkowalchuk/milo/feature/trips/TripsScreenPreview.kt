package com.shawnkowalchuk.milo.feature.trips

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.data.trip.ByHandMark
import com.shawnkowalchuk.milo.data.trip.CategoryTotals
import com.shawnkowalchuk.milo.data.trip.Tally
import com.shawnkowalchuk.milo.platform.address.TripPlace
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// The Trips screen as Android Studio draws it. In a file of its own because the screen's file
// is at the size limit (ENGINEERING_STANDARDS section 3).

// Sample values are written inline because a preview is never shown to a user or shipped.
@PreviewLightDark
@Composable
private fun TripsPreview() {
    val morning = 1_791_028_800_000
    val shop = TripPlace.Known("12 Shop Rd, Edmonton")
    val site = TripPlace.Known("48 Main St, Leduc")
    val trips =
        listOf(
            TripLine(
                id = 3,
                startedAtMs = morning + 3_600_000,
                endedAtMs = morning + 5_400_000,
                distanceMetres = 24_900.0,
                kind = TripKind.COUNTED,
                from = site,
                to = TripPlace.LookingUp,
                category = TripCategory.BUSINESS,
                ranPastSchedule = true,
                markableAs = listOf(TripCategory.PERSONAL),
            ),
            TripLine(
                id = 2,
                startedAtMs = morning + 1_800_000,
                endedAtMs = morning + 1_860_000,
                distanceMetres = 9_120.0,
                kind = TripKind.DISCARDED,
                category = TripCategory.PERSONAL,
                ignored = true,
            ),
            TripLine(
                id = 1,
                startedAtMs = morning,
                endedAtMs = morning + 1_500_000,
                distanceMetres = 23_400.0,
                kind = TripKind.COUNTED,
                from = shop,
                to = site,
                category = TripCategory.PERSONAL,
                markableAs = listOf(TripCategory.BUSINESS),
                mark = ByHandMark.EDITED,
                editable = true,
            ),
        )
    val summary =
        MonthSummary(
            totals = CategoryTotals(Tally(1, 249), Tally(1, 234), Tally(0, 0)),
            inProgress =
                TripLine(4, morning + 9_000_000, null, 3_200.0, TripKind.IN_PROGRESS, from = shop),
            days = listOf(TripDay(LocalDate.of(2026, 10, 3), trips, 2, 249)),
            hiddenLeftOut = 0,
        )
    val state =
        TripsUiState(
            month = YearMonth.of(2026, 10),
            zone = ZoneId.of("UTC"),
            canStepForward = false,
            showLeftOut = true,
            changeFailed = false,
            summary = summary,
        )
    MiloTheme {
        Surface {
            TripsContent(
                state = state,
                actions = TripsActions({}, {}, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}),
            )
        }
    }
}
