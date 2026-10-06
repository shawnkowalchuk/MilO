package com.shawnkowalchuk.milo.core.report

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

// What the tests of the report share: a time zone like the phone's, the report's words as plain
// English, and a way to measure text without Android.

/** Seven hours behind UTC in winter, six in summer; the clocks change on 8 March and 1 November. */
internal val EDMONTON: ZoneId = ZoneId.of("America/Edmonton")

internal val OCTOBER: YearMonth = YearMonth.of(2026, 10)

/** A moment written as UTC, as stored time. */
internal fun utc(text: String): Long = Instant.parse(text).toEpochMilli()

/** A moment written as Edmonton wall-clock time: "2026-10-05T08:14". */
internal fun edmonton(text: String): Long =
    LocalDateTime.parse(text).atZone(EDMONTON).toInstant().toEpochMilli()

/** The words the string resources hold, written out here so the tests need no Android. */
internal val WORDS =
    ReportWords(
        title = "Mileage report",
        name = "Name",
        company = "Company",
        vehicle = "Vehicle",
        period = "Period",
        generated = "Generated",
        businessOnly = "Business trips only. Distances are in kilometres.",
        revisionNote = "Revision %1\$d. It replaces the report sent on %2\$s.",
        periodRange = "%1\$s to %2\$s",
        columnStart = "Start",
        columnEnd = "End",
        columnFrom = "From",
        columnTo = "To",
        columnKm = "km",
        dayContinued = "%1\$s (continued)",
        subtotal = "Subtotal",
        total = "Total kilometres for %1\$s",
        tripCount = "3 business trips",
        noTrips = "No business trips in this period.",
        noAddress = "No address recorded",
        legend = "* This trip was added by hand, or changed by hand after it was recorded.",
        signature = "Signature",
        signatureDate = "Date",
        footer = "%1\$s · %2\$s",
        page = "Page %1\$d of %2\$d",
    )

/** Canadian English on a 24-hour phone: "08:14", "Monday, October 5, 2026". */
internal val FORMAT = ReportFormat(locale = Locale.CANADA, twentyFourHour = true)

internal val SENDER = ReportSender("Sam Driver", "Northside Electric Ltd.", "Ford F-150, ABC-123")

/**
 * Text as wide as its letters are many: half its font size for each. Plain arithmetic, so a
 * test can say exactly how many letters fit in a column.
 */
internal val MEASURE = TextMeasure { text, style -> text.length * style.size / 2 }

/** A trip on [day] from [start] ("08:14") for [minutes], [metres] long. */
internal fun trip(
    day: LocalDate,
    start: String,
    minutes: Long = 20,
    metres: Double = 12_340.0,
    from: String? = "12 Shop Rd, Edmonton",
    to: String? = "48 Main St, Leduc",
    mark: ReportMark? = null,
): ReportTrip {
    val startedAtMs = edmonton("${day}T$start")
    return ReportTrip(startedAtMs, startedAtMs + minutes * 60_000, from, to, metres, mark)
}

/** A report of [trips] for [period], made on 6 October 2026. */
internal fun report(
    trips: List<ReportTrip>,
    period: ReportPeriod = ReportPeriod.Month(OCTOBER),
    sender: ReportSender = SENDER,
    revision: ReportRevision? = null,
): MileageReport = MileageReport(
    sender = sender,
    period = period,
    generatedOn = LocalDate.of(2026, 10, 6),
    revision = revision,
    zone = EDMONTON,
    days = reportDays(trips, EDMONTON),
)
