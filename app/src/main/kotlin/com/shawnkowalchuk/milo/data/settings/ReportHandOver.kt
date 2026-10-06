package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import java.time.LocalDate
import java.time.YearMonth

/**
 * A report that was handed to the email app and that Shawn has not answered for yet.
 *
 * Android tells an app nothing about what became of an email, so MilO asks "Did you send it?"
 * when he is back. This is what the question is about, kept in the settings file so that it
 * outlives everything that can happen while the email app is open: MilO being ended by Android,
 * the phone being restarted, or the email app closing MilO's screen along with its own (seen on
 * an emulator, 2026-10-06). It is not a setting he chooses. Like the hold-off, it is a small
 * piece of state that must outlive the process.
 *
 * The figures are the ones the PDF printed, taken when it was made, so a trip that ends while
 * he is in the email app changes nothing about what is recorded as sent.
 *
 * @param tenths the report's total, in tenths of a kilometre.
 * @param atMs when the email app was opened with the report. If he says he sent it, this is
 * the day it is recorded as sent, however much later he answers.
 */
data class ReportHandOver(
    val period: ReportPeriod,
    val tripCount: Int,
    val tenths: Long,
    val atMs: Long,
)

// How it is kept in the settings file: six values, written and removed together. The key names
// and the two words for the kind of period are what is written to the file; they are spelled
// here and never taken from a constant's own name, so nothing that is renamed in code can lose
// a question that is waiting.

private val KIND = stringPreferencesKey("report_handed_over_kind")
private val FIRST_DAY = longPreferencesKey("report_handed_over_first_day")
private val LAST_DAY = longPreferencesKey("report_handed_over_last_day")
private val TRIP_COUNT = intPreferencesKey("report_handed_over_trip_count")
private val TENTHS = longPreferencesKey("report_handed_over_tenths")
private val AT_MS = longPreferencesKey("report_handed_over_at_ms")

private const val KIND_MONTH = "MONTH"
private const val KIND_RANGE = "RANGE"

/**
 * The stored hand-over, or null if none is waiting.
 *
 * All six values are written together, so a file that holds only some of them, or values that
 * make no report, was written by something else. It reads as "nothing is waiting", which is
 * the cautious reading: nothing is then recorded as sent that Shawn did not say he sent.
 */
internal fun Preferences.readReportHandOver(): ReportHandOver? {
    val first = dayOrNull(this[FIRST_DAY]) ?: return null
    val last = dayOrNull(this[LAST_DAY]) ?: return null
    val period =
        when (this[KIND]) {
            KIND_MONTH -> ReportPeriod.Month(YearMonth.from(first))
            KIND_RANGE -> if (last.isBefore(first)) return null else ReportPeriod.Range(first, last)
            else -> return null
        }
    val tripCount = this[TRIP_COUNT]?.takeIf { it >= 0 } ?: return null
    val tenths = this[TENTHS]?.takeIf { it >= 0 } ?: return null
    val atMs = this[AT_MS]?.takeIf { it >= 0 } ?: return null
    return ReportHandOver(period, tripCount, tenths, atMs)
}

/** Writes the hand-over, or removes it when null. Called inside one edit. */
internal fun MutablePreferences.writeReportHandOver(handOver: ReportHandOver?) {
    if (handOver == null) {
        listOf(KIND, FIRST_DAY, LAST_DAY, TRIP_COUNT, TENTHS, AT_MS).forEach { remove(it) }
        return
    }
    this[KIND] =
        when (handOver.period) {
            is ReportPeriod.Month -> KIND_MONTH
            is ReportPeriod.Range -> KIND_RANGE
        }
    this[FIRST_DAY] = handOver.period.firstDay.toEpochDay()
    this[LAST_DAY] = handOver.period.lastDay.toEpochDay()
    this[TRIP_COUNT] = handOver.tripCount
    this[TENTHS] = handOver.tenths
    this[AT_MS] = handOver.atMs
}

/** The calendar day [epochDay] days after 1970-01-01, or null for a number that is no day. */
private fun dayOrNull(epochDay: Long?): LocalDate? = epochDay
    ?.takeIf { it in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay() }
    ?.let(LocalDate::ofEpochDay)
