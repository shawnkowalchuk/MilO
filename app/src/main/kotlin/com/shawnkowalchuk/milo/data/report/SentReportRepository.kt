package com.shawnkowalchuk.milo.data.report

import com.shawnkowalchuk.milo.core.report.ReportPeriod
import kotlinx.coroutines.flow.Flow

/**
 * The only way the rest of the app reads or writes the list of sent reports.
 *
 * It can be added to and read, and that is all. Whether a month is submitted is not stored
 * anywhere: it is worked out from this list (`monthSubmission`), so the two can never disagree.
 */
class SentReportRepository(private val dao: SentReportDao) {
    /** Every sent report, newest first, and again each time one is added. */
    fun observeSent(): Flow<List<SentReport>> = dao.observeAll()

    /**
     * Records that Shawn sent a report. Call it once, when he has said so: it is **not** safe
     * to repeat, because a second call records a second report, as a revision of the first.
     *
     * @param sentAtMs when the email app was opened with the report: the day it counts as
     * sent.
     * @param tripCount and [distanceMetres] are what the report listed and added up to.
     * @return the row as stored, with the revision number it was given.
     */
    suspend fun recordSent(
        period: ReportPeriod,
        sentAtMs: Long,
        tripCount: Int,
        distanceMetres: Double,
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
            ),
        )
    }
}
