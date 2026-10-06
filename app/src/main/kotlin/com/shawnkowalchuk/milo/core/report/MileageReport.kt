package com.shawnkowalchuk.milo.core.report

import com.shawnkowalchuk.milo.core.util.localDateOf
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

// The report for the accountant as plain values: who it is from, what it covers, and its trips
// grouped by day with what they add up to. Pure Kotlin. The PDF and the CSV are both made from
// this one value, so the two can never show different trips or different figures.

private const val METRES_PER_TENTH_OF_A_KILOMETRE = 100.0

/**
 * A distance as the report prints it: whole tenths of a kilometre, 12 349 m being 123.
 *
 * **The report adds up the figures it prints.** Every trip is rounded to a tenth of a kilometre
 * once, here, and a day's subtotal and the period's total are sums of those rounded figures.
 * Whoever adds a column of the report up by hand therefore gets the figure printed under it.
 * The screens round a sum of metres once instead, so the month's figure on the Trips screen can
 * differ from the report's total by a tenth or two.
 *
 * @throws IllegalArgumentException if [metres] is negative, infinite or not a number. A
 * distance like that means the stored trip is corrupt, and a mileage claim should fail loudly
 * rather than print a plausible-looking figure (the rule of `formatKilometres`).
 */
fun tenthsOfAKilometre(metres: Double): Long {
    require(metres.isFinite() && metres >= 0.0) {
        "A distance must be a finite, non-negative number of metres, but was $metres"
    }
    return Math.round(metres / METRES_PER_TENTH_OF_A_KILOMETRE)
}

/**
 * Tenths of a kilometre as the kilometre figure that is printed, with one decimal: 123 becomes
 * "12.3". Worked out in decimal arithmetic, so the figure is exactly the tenths it is given.
 *
 * @param locale decides the decimal separator: the phone's for the PDF, `Locale.ROOT` for the
 * CSV, which a spreadsheet reads.
 */
fun formatTenths(tenths: Long, locale: Locale): String =
    String.format(locale, "%.1f", BigDecimal.valueOf(tenths, 1))

/** The same distance in metres, the unit every stored distance has. */
fun metresOfTenths(tenths: Long): Double = tenths * METRES_PER_TENTH_OF_A_KILOMETRE

/** Why a trip's figures are Shawn's own and not what MilO recorded. The report marks both. */
enum class ReportMark {
    /** Typed in by hand: MilO recorded nothing of it. */
    ADDED,

    /** Recorded by MilO, and a time, an address or the distance was changed by hand since. */
    EDITED,
}

/**
 * One trip on the report.
 *
 * @param endedAtMs null only for a row storage should never produce: a finished trip without
 * an end.
 * @param from the start address, or null if there is none. The report then says so in words,
 * never as coordinates.
 * @param to the end address, on the same terms.
 * @param mark set for a trip that was added or edited by hand. It carries an asterisk.
 */
data class ReportTrip(
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val from: String?,
    val to: String?,
    val distanceMetres: Double,
    val mark: ReportMark? = null,
) {
    /** The distance as it is printed, and as it is added up. */
    val tenths: Long get() = tenthsOfAKilometre(distanceMetres)
}

/** The trips that started on one calendar day, in the order they started. */
data class ReportDay(val date: LocalDate, val trips: List<ReportTrip>) {
    /** The day's subtotal: the sum of the figures printed for its trips. */
    val tenths: Long get() = trips.sumOf { it.tenths }
}

/**
 * Who the report is from.
 *
 * @param company and [vehicle] are left off the report when they are not set.
 */
data class ReportSender(val name: String, val company: String?, val vehicle: String?)

/**
 * That a report for the same period was sent before, so this one replaces it.
 *
 * @param number 1 for the first report that replaces the original, 2 for the next.
 * @param replacesSentOn the day the report it replaces was sent.
 */
data class ReportRevision(val number: Int, val replacesSentOn: LocalDate)

/**
 * A whole report.
 *
 * @param generatedOn the day the report was made, in [zone].
 * @param revision null for the first report of its period.
 * @param zone the time zone its days and times of day are worked out in: the phone's.
 * @param days oldest first, and only days that have a trip.
 */
data class MileageReport(
    val sender: ReportSender,
    val period: ReportPeriod,
    val generatedOn: LocalDate,
    val revision: ReportRevision?,
    val zone: ZoneId,
    val days: List<ReportDay>,
) {
    val tripCount: Int get() = days.sumOf { it.trips.size }

    /** The period's total: the sum of the days' subtotals. */
    val totalTenths: Long get() = days.sumOf { it.tenths }

    /** How many of its trips carry an asterisk. */
    val markedCount: Int get() = days.sumOf { day -> day.trips.count { it.mark != null } }
}

/**
 * Groups [trips] into days, oldest first, each day's trips in the order they started.
 *
 * A trip belongs to the day it **started** on in [zone], like everywhere else in MilO: a trip
 * that runs past midnight is listed once, whole, under the day it began.
 */
fun reportDays(trips: List<ReportTrip>, zone: ZoneId): List<ReportDay> = trips
    .sortedBy { it.startedAtMs }
    .groupBy { localDateOf(it.startedAtMs, zone) }
    .map { (date, ofDay) -> ReportDay(date, ofDay) }
    .sortedBy { it.date }
