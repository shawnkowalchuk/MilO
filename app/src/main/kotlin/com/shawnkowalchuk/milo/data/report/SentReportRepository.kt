package com.shawnkowalchuk.milo.data.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * The only way the rest of the app reads or writes the list of sent reports.
 *
 * It can be added to and read, and a report that was recorded by mistake can be removed from
 * it. Whether a month is submitted is not stored anywhere: it is worked out from this list
 * (`monthSubmission`), so the two can never disagree, and a month whose only report is removed
 * is "not submitted" again by that alone.
 */
class SentReportRepository(private val dao: SentReportDao) {
    /** Every sent report, newest first, and again each time one is added or removed. */
    fun observeSent(): Flow<List<SentReport>> = dao.observeAll()

    /** Every sent report as the list stands right now, newest first. */
    suspend fun currentSent(): List<SentReport> = dao.observeAll().first()

    /**
     * Removes one report from the list, for an "I sent it" that was a mistake. Nothing else is
     * written: the reports that stay keep their revision numbers, and the next report for the
     * period takes the number after the highest one still listed (`nextRevision`).
     *
     * Safe to repeat: a second call finds no such row and changes nothing.
     *
     * @return what was removed and what is left for its period, or null if the list holds no
     * report with this [id].
     */
    suspend fun remove(id: Long): RemovedReport? = dao.remove(id)

    /**
     * Records that Shawn sent a report. Call it once, when he has said so: it is **not** safe
     * to repeat, because a second call records a second report, as a revision of the first.
     *
     * @param sentAtMs when the email app was opened with the report: the day it counts as
     * sent.
     * @param tripCount and [distanceMetres] are what the report listed and added up to.
     * @param unit the unit the report was printed in, which the total is a whole number of
     * tenths of.
     * @return the row as stored, with the revision number it was given.
     */
    suspend fun recordSent(
        period: ReportPeriod,
        sentAtMs: Long,
        tripCount: Int,
        distanceMetres: Double,
        unit: DistanceUnit,
    ): SentReport {
        require(sentAtMs >= 0) { "A timestamp cannot be negative: $sentAtMs ms" }
        require(tripCount >= 0) { "A report cannot list a negative number of trips: $tripCount" }
        require(distanceMetres.isFinite() && distanceMetres >= 0.0) {
            "A report's total must be a finite, non-negative distance, but was $distanceMetres"
        }
        return dao.insertAsNextRevision(
            SentReport(
                kind = period.kind,
                firstDay = period.firstDay.toEpochDay(),
                lastDay = period.lastDay.toEpochDay(),
                sentAtMs = sentAtMs,
                tripCount = tripCount,
                distanceMetres = distanceMetres,
                // Given its number inside the insert's transaction.
                revision = 0,
                distanceUnit = unit,
            ),
        )
    }
}
