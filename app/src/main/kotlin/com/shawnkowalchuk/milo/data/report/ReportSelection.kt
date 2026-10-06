package com.shawnkowalchuk.milo.data.report

import com.shawnkowalchuk.milo.core.report.ReportMark
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.ReportTrip
import com.shawnkowalchuk.milo.core.report.span
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.trip.ByHandMark
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.byHandMark
import com.shawnkowalchuk.milo.data.trip.isCounted
import java.time.ZoneId

// Which stored trips a report lists. One pure function, so the PDF, the CSV and the summary on
// the Report screen are made from the same trips, and the rule is tested without a database.

/**
 * The trips of a period, sorted into what goes on the report and what is left off it.
 *
 * @param trips the trips the report lists, oldest first.
 * @param personalLeftOut how many counted trips of the period are Personal. They are never on
 * the report.
 * @param unsortedLeftOut how many counted trips of the period are neither Business nor Personal
 * yet. They are left off too, and the screen says so: marking one Business puts it on.
 * @param withoutAddress how many of [trips] lack a start or an end address. The report says so
 * in words where the address would stand.
 * @param tripInProgress true if a trip that started in the period is still being recorded. It
 * is not on the report until it has ended.
 */
data class ReportSelection(
    val trips: List<ReportTrip>,
    val personalLeftOut: Int,
    val unsortedLeftOut: Int,
    val withoutAddress: Int,
    val tripInProgress: Boolean,
)

/**
 * Picks the trips of [period] that go on a report.
 *
 * **The rule.** A trip is on the report if it is counted (`isCounted`: a finished trip, so not
 * one that is being recorded, was discarded or was deleted), is saved as Business, and
 * **started** in the period, in [zone]. Only the start decides, as it decides the day and the
 * month a trip is listed under on the Trips screen: a trip that began at 23:50 on the last day
 * of the period is on the report whole, and one that began before the first day and ran into
 * it is not.
 *
 * @param trips stored trips in any order. Trips that started outside the period are ignored,
 * so the caller may pass more than the period's own.
 */
fun selectForReport(trips: List<Trip>, period: ReportPeriod, zone: ZoneId): ReportSelection {
    val span = period.span(zone)
    val inPeriod = trips.filter { it.startedAtMs >= span.fromMs && it.startedAtMs < span.untilMs }
    val counted = inPeriod.filter { it.isCounted }
    val listed =
        counted
            .filter { it.category == TripCategory.BUSINESS }
            .sortedWith(compareBy<Trip> { it.startedAtMs }.thenBy { it.id })
    return ReportSelection(
        trips = listed.map { it.onReport() },
        personalLeftOut = counted.count { it.category == TripCategory.PERSONAL },
        unsortedLeftOut = counted.count { it.category == null },
        withoutAddress = listed.count { it.startAddress == null || it.endAddress == null },
        tripInProgress = inPeriod.any { it.status == TripStatus.OPEN },
    )
}

/**
 * The trip as the report shows it. Its positions are deliberately not carried along: where an
 * address is missing the report says so in words, and never prints coordinates.
 */
private fun Trip.onReport(): ReportTrip = ReportTrip(
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    from = startAddress,
    to = endAddress,
    distanceMetres = distanceMetres,
    mark =
        when (byHandMark) {
            ByHandMark.ADDED -> ReportMark.ADDED
            ByHandMark.EDITED -> ReportMark.EDITED
            null -> null
        },
)
