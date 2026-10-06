package com.shawnkowalchuk.milo.data.report

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.shawnkowalchuk.milo.core.report.ReportPeriod
import java.time.LocalDate
import java.time.YearMonth

/**
 * What kind of period a sent report covers. Stored by name, so a constant can be added but
 * never renamed without a migration.
 */
enum class SentReportKind {
    /** A whole calendar month. Only this kind marks a month as submitted. */
    MONTH,

    /** A run of days Shawn chose. It is listed, and marks no month, whatever days it covers. */
    RANGE,
}

/**
 * One report Shawn has said he sent: a line of the list of sent reports, and, for a report of a
 * whole month, what makes that month "submitted".
 *
 * A row is written when he answers "I sent it" after coming back from the email app, and at no
 * other moment: Android cannot tell an app whether an email was sent, so MilO takes his word.
 * A row is never changed afterwards. It is a record of what was sent, with the figures the
 * report had then, which later edits of the trips do not reach. It leaves the table in one way
 * only: he removes it himself on the Report screen, because it was recorded by mistake.
 *
 * @param firstDay the first day of the period, as days since 1970-01-01 (a calendar day, not a
 * moment in time, so no time zone can move it). Read it with `LocalDate.ofEpochDay`, or in
 * SQLite with `date(firstDay * 86400, 'unixepoch')`.
 * @param lastDay the last day of the period, on the same terms. It is inside the period. For a
 * [SentReportKind.MONTH] the two are the first and the last day of that month.
 * @param sentAtMs when the email app was opened with the report, wall-clock milliseconds since
 * 1970: the moment the report left MilO, which is the day he says he sent it, however much
 * later he answers the question.
 * @param tripCount how many Business trips the report listed.
 * @param distanceMetres the total the report printed, in metres like every stored distance. It
 * is a whole number of tenths of a kilometre, because the report adds up the figures it prints.
 * @param revision 0 for the first report sent for this kind and these days, 1 for the first
 * that replaced it, and so on.
 */
@Entity(tableName = "sent_reports")
data class SentReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: SentReportKind,
    val firstDay: Long,
    val lastDay: Long,
    val sentAtMs: Long,
    val tripCount: Int,
    val distanceMetres: Double,
    val revision: Int,
)

/**
 * A report that was removed from the list, and what the list still holds for its period.
 *
 * @param leftForPeriod the reports of the same kind and days that are still there, in any
 * order. Empty means the removed one was the period's only report.
 */
data class RemovedReport(val report: SentReport, val leftForPeriod: List<SentReport>)

/** The kind of row a report for this period is stored as. */
val ReportPeriod.kind: SentReportKind
    get() = when (this) {
        is ReportPeriod.Month -> SentReportKind.MONTH
        is ReportPeriod.Range -> SentReportKind.RANGE
    }

/**
 * The period the row was sent for.
 *
 * Every row is written from a [ReportPeriod], so its two days are in order and are real days.
 * A row that is not (which nothing in MilO can write) is still read, as the nearest period
 * that makes sense: this runs while a screen is drawn, and must not be what ends the process
 * the trip service runs in.
 */
val SentReport.period: ReportPeriod
    get() {
        val first = dayOf(firstDay)
        return when (kind) {
            SentReportKind.MONTH -> ReportPeriod.Month(YearMonth.from(first))
            SentReportKind.RANGE -> ReportPeriod.Range(first, maxOf(first, dayOf(lastDay)))
        }
    }

private fun dayOf(epochDay: Long): LocalDate =
    LocalDate.ofEpochDay(epochDay.coerceIn(LocalDate.MIN.toEpochDay(), LocalDate.MAX.toEpochDay()))
