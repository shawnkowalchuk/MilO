package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.odometer.DrivenTrip
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.odometer.drivenIn
import com.shawnkowalchuk.milo.core.odometer.odometerOver
import com.shawnkowalchuk.milo.core.odometer.ofVehicle
import com.shawnkowalchuk.milo.core.report.MileageReport
import com.shawnkowalchuk.milo.core.report.PersonalDriving
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.ReportRevision
import com.shawnkowalchuk.milo.core.report.ReportSender
import com.shawnkowalchuk.milo.core.report.VehicleOdometer
import com.shawnkowalchuk.milo.core.report.reportDays
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.report.ReportSelection
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.nextRevision
import com.shawnkowalchuk.milo.data.report.period
import com.shawnkowalchuk.milo.data.report.removalEffect
import com.shawnkowalchuk.milo.data.report.sentFor
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.MissingDetail
import com.shawnkowalchuk.milo.data.settings.missingForPdf
import com.shawnkowalchuk.milo.data.settings.missingForSending
import com.shawnkowalchuk.milo.data.settings.pairedVehicles
import com.shawnkowalchuk.milo.data.settings.vehicleNamed
import java.time.LocalDate
import java.time.ZoneId

// The report a press on the Report screen would make, and what each kind of press needs of the
// settings first. Pure functions, so they are tested without a phone. They were part of
// ReportUiState.kt until that file reached its size limit (ENGINEERING_STANDARDS section 3).

/**
 * The report for [period] as it would be made now.
 *
 * A period that was sent before makes this one a revision: it carries the next number and
 * names the day the newest report before it was sent. That holds for a PDF that is only made to
 * be looked at too, so that what he looks at is what would be sent.
 *
 * @param name printed as the sender. The caller decides what a report may be made without.
 * @param sent every report sent so far.
 * @param today the day the report is generated on, in [zone].
 * @param truckTrips every trip that moved the truck's odometer, for its figures at the start
 * and the end of the period (`odometerOver`).
 *
 * **The report is in the unit [settings] hold at this moment** (Shawn's answer of 2026-10-07,
 * "Follow the setting"): its trips, its totals, the Personal figure and the odometer.
 */
fun mileageReport(
    period: ReportPeriod,
    selection: ReportSelection,
    name: String,
    settings: MiloSettings,
    sent: List<SentReport>,
    today: LocalDate,
    zone: ZoneId,
    truckTrips: List<DrivenTrip> = emptyList(),
): MileageReport {
    val sentBefore = sentFor(period, sent)
    val unit = settings.distanceUnit
    return MileageReport(
        sender = ReportSender(name, settings.reportCompany, settings.reportVehicle),
        period = period,
        generatedOn = today,
        revision =
            sentBefore.lastOrNull()?.let { last ->
                ReportRevision(nextRevision(sentBefore), localDateOf(last.sentAtMs, zone))
            },
        zone = zone,
        unit = unit,
        days =
            reportDays(
                selection.trips.map { it.copy(vehicle = it.vehicle?.let(settings::vehicleNamed)) },
                zone,
            ),
        personal = PersonalDriving(selection.personalLeftOut, selection.personalTenths(unit)),
        odometers = odometersOver(period, zone, settings, truckTrips),
    )
}

/**
 * Each paired vehicle's odometer at the start and the end of [period] (one for each vehicle
 * since 2026-10-08), from its own readings and trips; with no vehicle paired, the one odometer
 * of every reading and trip, as it was before.
 */
private fun odometersOver(
    period: ReportPeriod,
    zone: ZoneId,
    settings: MiloSettings,
    truckTrips: List<DrivenTrip>,
): List<VehicleOdometer> {
    val unit = settings.distanceUnit
    val readings = settings.odometerReadings
    fun over(ofVehicle: List<OdometerReading>, driven: List<DrivenTrip>) =
        odometerOver(period.firstDay, period.lastDay, zone, ofVehicle, driven, unit)
    val vehicles = settings.pairedVehicles()
    if (vehicles.isEmpty()) {
        return listOfNotNull(over(readings, truckTrips)?.let { VehicleOdometer(null, it) })
    }
    return vehicles.mapIndexedNotNull { index, vehicle ->
        val first = index == 0
        over(
            readings.ofVehicle(vehicle.address, first),
            truckTrips.drivenIn(vehicle.address, first),
        )
            ?.let { VehicleOdometer(vehicle.name ?: vehicle.address, it) }
    }
}

/** The question "Email the report" asks first, or null for a period never sent before. */
fun resendOf(period: ReportPeriod, sent: List<SentReport>): Resend? {
    val sentBefore = sentFor(period, sent)
    val last = sentBefore.lastOrNull() ?: return null
    return Resend(last = last.asLine(sent), revision = nextRevision(sentBefore))
}

/** What a press needs of the settings before it may make a file. */
enum class ReportNeed {
    /** A CSV: nothing. It prints no name and goes wherever Shawn shares it. */
    NOTHING,

    /** A PDF to look at: the name that is printed on it. */
    PDF,

    /** A PDF to send: the name, and the address it is sent to. */
    SENDING,
    ;

    /** What of it [settings] lack. */
    fun missingIn(settings: MiloSettings): List<MissingDetail> = when (this) {
        NOTHING -> emptyList()
        PDF -> settings.missingForPdf()
        SENDING -> settings.missingForSending()
    }
}

internal fun SentReport.asLine(sent: List<SentReport>): SentLine = SentLine(
    id = id,
    period = period,
    sentAtMs = sentAtMs,
    tripCount = tripCount,
    distanceMetres = distanceMetres,
    unit = distanceUnit,
    revision = revision,
    removal = removalEffect(this, sent),
)
