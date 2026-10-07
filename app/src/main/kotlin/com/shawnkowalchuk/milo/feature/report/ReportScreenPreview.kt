package com.shawnkowalchuk.milo.feature.report

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.core.report.ReportMark
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.ReportTrip
import com.shawnkowalchuk.milo.core.util.metresOfTenths
import com.shawnkowalchuk.milo.data.report.ReportSelection
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.kind
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

// The Report screen as Android Studio draws it, in the states that are slow to reach by hand:
// the owner's drawing's own month, a month that was sent, a date range, and a report whose
// settings are missing. Each is put together by `reportUiState`, the function the screen
// itself uses, so a preview cannot show a state the screen cannot be in. In a file of its own
// to keep the screen's files under the size limit (ENGINEERING_STANDARDS section 3). Sample
// values are written inline because a preview is never shown to a user or shipped.

private val edmonton = ZoneId.of("America/Edmonton")

/** The day the previews are drawn on: the reminder for September is running. */
private val today = LocalDate.of(2026, 10, 7)
private val september = YearMonth.of(2026, 9)

private val settings =
    MiloSettings(
        reportName = "Sam Driver",
        reportCompany = "Northside Electric Ltd.",
        reportVehicle = "2021 Ford F-150, white, plate ABC-1234",
        accountantEmail = "accounts@example.ca",
    )

/** 21 trips that add up to the drawing's 231.4 km, two of them changed by hand. */
private val drawnMonth: ReportSelection =
    ReportSelection(
        trips = sampleTrips(count = 21, tenths = 2314, edmonton),
        personalLeftOut = 3,
        unsortedLeftOut = 0,
        withoutAddress = 0,
        tripInProgress = false,
    )

/**
 * [count] trips of September, a morning apart, that add up to exactly [tenths] tenths of a
 * kilometre. The second and the third were changed by hand.
 */
private fun sampleTrips(count: Int, tenths: Long, zone: ZoneId): List<ReportTrip> {
    val each = tenths / count
    val firstStart = LocalDate.of(2026, 9, 1).atTime(7, 42).atZone(zone).toInstant().toEpochMilli()
    return List(count) { index ->
        val startedAtMs = firstStart + index * MS_PER_DAY
        // The last trip takes what the others leave, so that the total is the one asked for.
        val ownTenths = if (index == count - 1) tenths - each * index else each
        ReportTrip(
            startedAtMs = startedAtMs,
            endedAtMs = startedAtMs + TRIP_MS,
            from = "Shop, 63 Ave NW",
            to = "Windermere site",
            distanceMetres = metresOfTenths(ownTenths),
            mark = ReportMark.EDITED.takeIf { index == 1 || index == 2 },
        )
    }
}

private const val MS_PER_DAY = 24 * 60 * 60_000L
private const val TRIP_MS = 24 * 60_000L

private fun sent(period: ReportPeriod, revision: Int, day: LocalDate, id: Long) = SentReport(
    id = id,
    kind = period.kind,
    firstDay = period.firstDay.toEpochDay(),
    lastDay = period.lastDay.toEpochDay(),
    sentAtMs = day.atTime(9, 30).atZone(edmonton).toInstant().toEpochMilli(),
    tripCount = 21,
    distanceMetres = 231_400.0,
    revision = revision,
)

@Composable
private fun ReportPreview(
    choice: ReportChoice = openingChoice(september, today),
    selection: ReportSelection? = drawnMonth,
    stored: MiloSettings = settings,
    sent: List<SentReport> = emptyList(),
    passing: ReportPassing = ReportPassing(pdfName = "Mileage-2026-09-Sam-Driver.pdf"),
) {
    val state = reportUiState(choice, today, edmonton, selection, stored, sent, passing)
    MiloTheme {
        Surface {
            ReportContent(
                state = state,
                actions = ReportActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
                onBack = {},
            )
        }
    }
}

/** The drawing's own state: last month, not sent, and the reminder running. */
@Preview
@Composable
private fun ReportNotSentPreview() {
    ReportPreview()
}

/** The same month once it was sent, and sent again as a revision. */
@Preview
@Composable
private fun ReportSentPreview() {
    val month = ReportPeriod.Month(september)
    ReportPreview(
        sent =
            listOf(
                sent(month, revision = 1, day = LocalDate.of(2026, 10, 5), id = 2),
                sent(month, revision = 0, day = LocalDate.of(2026, 10, 2), id = 1),
            ),
        passing =
            ReportPassing(pdfName = "Mileage-2026-09-Sam-Driver-rev2.pdf", pdfPages = 3),
    )
}

/** A date range, while its PDF is being made. */
@Preview
@Composable
private fun ReportRangePreview() {
    val first = LocalDate.of(2026, 9, 14)
    val last = LocalDate.of(2026, 9, 18)
    ReportPreview(
        choice = openingChoice(september, today).copy(PeriodKind.RANGE, september, first, last),
        selection =
            drawnMonth.copy(
                trips = sampleTrips(count = 6, tenths = 713, edmonton),
                withoutAddress = 1,
                tripInProgress = true,
            ),
        passing =
            ReportPassing(
                working = true,
                pdfName = "Mileage-2026-09-14-to-2026-09-18-Sam-Driver.pdf",
            ),
    )
}

/** Neither the name nor the address is set, and the phone has no PDF viewer. */
@Preview
@Composable
private fun ReportMissingPreview() {
    ReportPreview(
        stored = MiloSettings(reportVehicle = settings.reportVehicle),
        passing = ReportPassing(problem = ReportProblem.NO_PDF_VIEWER, refusals = 1),
    )
}

/** The trips are still being read. */
@Preview
@Composable
private fun ReportReadingPreview() {
    ReportPreview(selection = null, passing = ReportPassing())
}
