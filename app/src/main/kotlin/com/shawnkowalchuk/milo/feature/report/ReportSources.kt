package com.shawnkowalchuk.milo.feature.report

import com.shawnkowalchuk.milo.core.odometer.DrivenTrip
import com.shawnkowalchuk.milo.core.report.MileageReport
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.report.span
import com.shawnkowalchuk.milo.data.report.ReportSelection
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.report.SentReportRepository
import com.shawnkowalchuk.milo.data.report.selectForReport
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.drivenTrips
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** What Shawn has chosen on the Report screen, and the day and zone it is measured against. */
data class ReportChosen(val choice: ReportChoice, val today: LocalDate, val zone: ZoneId)

/**
 * Everything a report is made from, as it is stored right now.
 *
 * @param selection the trips of the chosen period, or null while they are being read.
 * @param settings null once the settings file has turned out to be unreadable.
 * @param sent every report that was sent, newest first.
 * @param truckTrips every trip that moved the truck's odometer, whenever it started: the
 * odometer at the start and the end of the period is worked out from them.
 */
data class ReportSources(
    val chosen: ReportChosen,
    val selection: ReportSelection?,
    val settings: MiloSettings?,
    val sent: List<SentReport>,
    val truckTrips: List<DrivenTrip> = emptyList(),
) {
    /**
     * The report as it would be made now, or null while its trips are being read, while the
     * settings cannot be read, or while the name [need] asks for is not set.
     *
     * A PDF of a period that was sent before is the next revision of it. A CSV never is one.
     */
    fun reportFor(need: ReportNeed): MileageReport? {
        val stored = settings ?: return null
        val trips = selection ?: return null
        val forCsv = need == ReportNeed.NOTHING
        // A CSV prints no name, so it is made without one; its file is then named without it.
        val name = stored.reportName ?: "".takeIf { forCsv } ?: return null
        val report =
            mileageReport(
                period = chosen.choice.period,
                selection = trips,
                name = name,
                settings = stored,
                sent = sent,
                today = chosen.today,
                zone = chosen.zone,
                truckTrips = truckTrips,
            )
        // A CSV is the period's trips as they are now, for a spreadsheet. It replaces no report
        // that was sent, and exporting one sends nothing. With the PDF's next number in its
        // file name and its share title it would announce a revision that was never made.
        return if (forCsv) report.copy(revision = null) else report
    }
}

/**
 * Reads what a report is made from, and again each time any of it changes: the trips of the
 * chosen period, the settings, the list of sent reports, and the trips that moved the truck's
 * odometer. The chosen period's trips are read from storage by its span of time, like a month
 * on the Trips screen; the odometer needs every finished trip, because the reading it starts
 * from can lie outside the period.
 *
 * @param records where a settings file that cannot be read is written to the event log.
 */
class ReportReading(
    private val trips: TripRepository,
    private val sentReports: SentReportRepository,
    private val settings: SettingsStore,
    private val records: ReportRecords,
) {
    /** The trips of one period, labelled with the period and zone they were read for. */
    private data class PeriodTrips(
        val period: ReportPeriod,
        val zone: ZoneId,
        val selection: ReportSelection,
    )

    /** The sources for whatever [chosen] holds, following it as it changes. */
    fun sources(chosen: Flow<ReportChosen>): Flow<ReportSources> = combine(
        chosen,
        periodTrips(chosen),
        storedSettings(),
        sentReports.observeSent(),
        trips.observeFinishedTrips().map(::drivenTrips),
    ) { now, read, stored, sent, driven ->
        // Right after a change of period the trips in hand are still the last period's.
        // They are not shown under the new one: the screen says it is reading.
        val current = read.period == now.choice.period && read.zone == now.zone
        ReportSources(now, read.selection.takeIf { current }, stored, sent, driven)
    }

    // flatMapLatest is how a Flow switches to a new query when the period changes. It is marked
    // experimental by the coroutines library and has no stable equivalent.
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun periodTrips(chosen: Flow<ReportChosen>): Flow<PeriodTrips> = chosen
        .map { it.choice.period to it.zone }
        .distinctUntilChanged()
        .flatMapLatest { (period, zone) ->
            val span = period.span(zone)
            trips
                .observeTripsStartedBetween(span.fromMs, span.untilMs)
                .map { PeriodTrips(period, zone, selectForReport(it, period, zone)) }
        }

    /** The stored settings, or null once the file has turned out to be unreadable. */
    private fun storedSettings(): Flow<MiloSettings?> = settings.settings
        .map<MiloSettings, MiloSettings?> { it }
        .catch { unreadable ->
            if (unreadable !is IOException) throw unreadable
            records.failed("Reading the settings for the Report screen", unreadable)
            emit(null)
        }
}
