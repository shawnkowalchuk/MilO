package com.shawnkowalchuk.milo.core.report

import com.shawnkowalchuk.milo.core.odometer.OdometerSpan
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.sumOfTenths
import com.shawnkowalchuk.milo.core.util.tenthsOf
import java.time.LocalDate
import java.time.ZoneId

// The report for the accountant as plain values: who it is from, what it covers, and its trips
// grouped by day with what they add up to. Pure Kotlin. The PDF and the CSV are both made from
// this one value, so the two can never show different trips or different figures.
//
// **The report adds up the figures it prints.** A trip is rounded to a tenth of a kilometre
// once, and a day's subtotal and the period's total are sums of those rounded figures, so that
// whoever adds a column up by hand gets the figure printed under it. The rule is `sumOfTenths`
// in `core/util/DistanceFormat.kt`, which the screens add up by as well.
//
// Since 2026-10-07 a report is in the unit MilO was set to when it was made
// ([MileageReport.unit]): in miles a trip is rounded once to a tenth of a mile, and the
// subtotals and the total are sums of those figures. The trips themselves are held in metres.

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
 * @param label what Shawn called the trip, its purpose ("Work", "Supplier"), or null (since
 * 2026-10-08). Printed under its addresses.
 * @param vehicle the name of the vehicle it was in, or null. Printed beside the label when the
 * report's trips were in more than one vehicle ([MileageReport.severalVehicles]).
 */
data class ReportTrip(
    val startedAtMs: Long,
    val endedAtMs: Long?,
    val from: String?,
    val to: String?,
    val distanceMetres: Double,
    val mark: ReportMark? = null,
    val label: String? = null,
    val vehicle: String? = null,
) {
    /** The distance as it is printed in [unit], and as it is added up. */
    fun tenths(unit: DistanceUnit): Long = tenthsOf(distanceMetres, unit)
}

/** The trips that started on one calendar day, in the order they started. */
data class ReportDay(val date: LocalDate, val trips: List<ReportTrip>) {
    /** The day's subtotal in [unit]: the sum of the figures printed for its trips. */
    fun tenths(unit: DistanceUnit): Long = sumOfTenths(trips.map { it.distanceMetres }, unit)
}

/**
 * Who the report is from.
 *
 * @param company and [vehicle] are left off the report when they are not set.
 */
data class ReportSender(val name: String, val company: String?, val vehicle: String?)

/**
 * One vehicle's odometer at the start and the end of the period.
 *
 * @param vehicle its name, or null where there is only one and nothing to tell apart.
 */
data class VehicleOdometer(val vehicle: String?, val span: OdometerSpan)

/**
 * That a report for the same period was sent before, so this one replaces it.
 *
 * @param number 1 for the first report that replaces the original, 2 for the next.
 * @param replacesSentOn the day the report it replaces was sent.
 */
data class ReportRevision(val number: Int, val replacesSentOn: LocalDate)

/**
 * The period's Personal driving. The report lists no Personal trip, but shows what they add up
 * to at its top, beside the Business total (Shawn's request of 2026-10-07: "at the top it
 * should show business and personal mileage separate").
 *
 * @param tripCount how many counted Personal trips started in the period.
 * @param tenths their distance in tenths of the report's unit, added up as the report adds up
 * its own (`sumOfTenths`), so the figure is the one the Trips screen shows for the month's
 * Personal trips.
 */
data class PersonalDriving(val tripCount: Int, val tenths: Long)

/**
 * A whole report.
 *
 * @param generatedOn the day the report was made, in [zone].
 * @param revision null for the first report of its period.
 * @param zone the time zone its days and times of day are worked out in: the phone's.
 * @param unit the unit every distance of the report is printed in: the one MilO was set to
 * when the report was made (Shawn's answer of 2026-10-07, "Follow the setting"). [personal]
 * and [odometers] are in it too.
 * @param days oldest first, and only days that have a trip.
 * @param personal what the period's Personal trips add up to. They are not listed.
 * @param odometers each vehicle's odometer at the start and the end of the period (Shawn's
 * decision of 2026-10-07; one for each vehicle since 2026-10-08), the first vehicle first. A
 * vehicle without a reading has none, and the list is empty while no reading has been typed.
 */
data class MileageReport(
    val sender: ReportSender,
    val period: ReportPeriod,
    val generatedOn: LocalDate,
    val revision: ReportRevision?,
    val zone: ZoneId,
    val unit: DistanceUnit,
    val days: List<ReportDay>,
    val personal: PersonalDriving,
    val odometers: List<VehicleOdometer> = emptyList(),
) {
    val tripCount: Int get() = days.sumOf { it.trips.size }

    /** Whether its trips were in more than one vehicle: each trip then names its own. */
    val severalVehicles: Boolean
        get() = days.flatMap { it.trips }.mapNotNull { it.vehicle }.distinct().size > 1

    /** The period's total in [unit]: the sum of the days' subtotals. */
    val totalTenths: Long get() = days.sumOf { it.tenths(unit) }

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
