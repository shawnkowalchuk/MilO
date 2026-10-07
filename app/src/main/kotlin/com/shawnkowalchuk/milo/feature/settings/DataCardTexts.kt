package com.shawnkowalchuk.milo.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.RowStatus
import com.shawnkowalchuk.milo.core.designsystem.component.rememberTwentyFourHourClock
import com.shawnkowalchuk.milo.core.util.formatDate
import com.shawnkowalchuk.milo.core.util.formatTimeOfDay
import com.shawnkowalchuk.milo.data.transfer.EXPORT_FORMAT_VERSION
import com.shawnkowalchuk.milo.data.transfer.MAX_IMPORT_BYTES
import com.shawnkowalchuk.milo.platform.transfer.CutOffImport
import com.shawnkowalchuk.milo.platform.transfer.ImportOffer
import com.shawnkowalchuk.milo.platform.transfer.PointsTaken
import com.shawnkowalchuk.milo.platform.transfer.TransferRefusal
import com.shawnkowalchuk.milo.platform.transfer.TransferWork
import java.time.ZoneId

// The words of the tile for backup, export and import: which sentence stands for which
// outcome, and the text of the question before an import.

private const val BYTES_PER_MEGABYTE = 1024 * 1024

/** A day as the rest of MilO writes one, in the phone's language. */
@Composable
internal fun day(epochMs: Long, zone: ZoneId): String =
    formatDate(epochMs, zone, LocalConfiguration.current.locales[0])

/** A time of day with 24 hours or with AM and PM, as the phone is set. */
@Composable
internal fun time(epochMs: Long, zone: ZoneId): String = formatTimeOfDay(
    epochMs,
    zone,
    LocalConfiguration.current.locales[0],
    rememberTwentyFourHourClock(),
)

// A plural form is chosen by a whole number. A million points is far inside one.
@Composable
private fun trips(count: Int): String =
    pluralStringResource(R.plurals.settings_data_trips, count, count)

@Composable
private fun sentReports(count: Int): String =
    pluralStringResource(R.plurals.settings_data_sent_reports, count, count)

@Composable
private fun points(count: Long): String = pluralStringResource(
    R.plurals.settings_data_points,
    count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    count,
)

/** When the last export was written, or that none was. */
@Composable
internal fun lastExportText(state: DataCardState): String {
    val last = state.lastExport ?: return stringResource(R.string.settings_data_never_exported)
    val words =
        if (last.withPoints) {
            R.string.settings_data_last_export
        } else {
            R.string.settings_data_last_export_without_points
        }
    return stringResource(words, day(last.atMs, state.zone), time(last.atMs, state.zone))
}

/** What is being done right now, in words. */
internal fun TransferWork.wordsRes(): Int = when (this) {
    TransferWork.EXPORTING -> R.string.settings_data_working_export
    TransferWork.READING_FILE -> R.string.settings_data_working_read
    TransferWork.IMPORTING -> R.string.settings_data_working_import
}

internal fun OutcomeTone.rowStatus(): RowStatus = when (this) {
    OutcomeTone.DONE -> RowStatus.OK
    OutcomeTone.PROBLEM -> RowStatus.PROBLEM
    OutcomeTone.TO_DO -> RowStatus.NEEDS_CONFIRMATION
}

/** The sentence a line of the tile stands for. */
@Composable
internal fun OutcomeLine.text(): String = when (this) {
    is OutcomeLine.Exported -> exportedText(this)

    OutcomeLine.ExportedWithoutSettings ->
        stringResource(R.string.settings_data_exported_no_settings)

    is OutcomeLine.PointsLeftOut ->
        stringResource(R.string.settings_data_exported_points_left_out, points(points))

    OutcomeLine.FinishedAfterCutOff -> stringResource(R.string.settings_data_cut_off_finished)

    is OutcomeLine.CutOff -> stringResource(what.wordsRes())

    is OutcomeLine.Imported -> importedText(this)

    OutcomeLine.SettingsKept -> stringResource(R.string.settings_data_imported_settings_kept)

    OutcomeLine.SettingsNotStored ->
        stringResource(R.string.settings_data_imported_settings_failed)

    OutcomeLine.PairTruckAgain -> stringResource(R.string.settings_data_imported_pair_again)

    OutcomeLine.SafetyCopyKept -> stringResource(R.string.settings_data_imported_safety)

    is OutcomeLine.Refused -> why.text()

    OutcomeLine.NoFilePicker -> stringResource(R.string.settings_data_no_picker)
}

private fun CutOffImport.wordsRes(): Int = when (this) {
    CutOffImport.NOTHING_REPLACED -> R.string.settings_data_cut_off_nothing
    CutOffImport.POINTS_REMOVED -> R.string.settings_data_cut_off_points
    CutOffImport.NOT_FINISHED -> R.string.settings_data_cut_off_not_finished
}

@Composable
private fun exportedText(line: OutcomeLine.Exported): String {
    val trips = trips(line.trips)
    val reports = sentReports(line.sentReports)
    return if (line.points == null) {
        stringResource(R.string.settings_data_exported_without_points, trips, reports)
    } else {
        stringResource(R.string.settings_data_exported, trips, reports, points(line.points))
    }
}

@Composable
private fun importedText(line: OutcomeLine.Imported): String {
    val trips = trips(line.trips)
    val reports = sentReports(line.sentReports)
    return when (line.pointsTaken) {
        PointsTaken.TAKEN ->
            stringResource(R.string.settings_data_imported, trips, reports, points(line.points))

        PointsTaken.NONE_IN_FILE ->
            stringResource(R.string.settings_data_imported_no_points, trips, reports)

        PointsTaken.NOT_STORED ->
            stringResource(R.string.settings_data_imported_points_failed, trips, reports)
    }
}

@Composable
private fun TransferRefusal.text(): String = when (this) {
    TransferRefusal.TripInProgress -> stringResource(R.string.settings_data_refused_trip)

    TransferRefusal.ExportNotWritten -> stringResource(R.string.settings_data_refused_export)

    TransferRefusal.ExportLeftUnfinished ->
        stringResource(R.string.settings_data_refused_export_left)

    TransferRefusal.FileNotRead -> stringResource(R.string.settings_data_refused_read)

    TransferRefusal.FileTooLarge ->
        stringResource(
            R.string.settings_data_refused_large,
            MAX_IMPORT_BYTES / BYTES_PER_MEGABYTE,
        )

    TransferRefusal.NotAnExport -> stringResource(R.string.settings_data_refused_not_export)

    is TransferRefusal.NewerVersion ->
        stringResource(R.string.settings_data_refused_newer, version, EXPORT_FORMAT_VERSION)

    TransferRefusal.Damaged -> stringResource(R.string.settings_data_refused_damaged)

    TransferRefusal.NoSafetyCopy -> stringResource(R.string.settings_data_refused_no_safety_copy)

    TransferRefusal.SafetyCopyNotWritten -> stringResource(R.string.settings_data_refused_safety)

    TransferRefusal.NothingReplaced ->
        stringResource(R.string.settings_data_refused_nothing_replaced)
}

/**
 * The question before an import, in four paragraphs: what the file is and holds, what
 * importing it replaces on this phone, what becomes of the settings, and what MilO keeps.
 */
@Composable
internal fun importQuestion(offer: ImportOffer, zone: ZoneId): String {
    val day = day(offer.exportedAtMs, zone)
    val time = time(offer.exportedAtMs, zone)
    val trips = trips(offer.trips)
    val reports = sentReports(offer.sentReports)
    val file =
        when {
            offer.points == null ->
                stringResource(
                    R.string.settings_data_question_file_no_points,
                    day,
                    time,
                    trips,
                    reports,
                )

            else -> {
                val words =
                    if (offer.fromSafetyCopy) {
                        R.string.settings_data_question_safety_file
                    } else {
                        R.string.settings_data_question_file
                    }
                stringResource(words, day, time, trips, reports, points(offer.points))
            }
        }
    val replaces =
        stringResource(
            R.string.settings_data_question_replaces,
            trips(offer.storedTrips),
            sentReports(offer.storedSentReports),
        )
    val settings =
        stringResource(
            if (offer.hasSettings) {
                R.string.settings_data_question_settings
            } else {
                R.string.settings_data_question_no_settings
            },
        )
    val kept =
        stringResource(
            if (offer.fromSafetyCopy) {
                R.string.settings_data_question_safety_put_back
            } else {
                R.string.settings_data_question_safety
            },
        )
    return listOf(file, replaces, settings, kept).joinToString("\n\n")
}
