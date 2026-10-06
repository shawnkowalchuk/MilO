package com.shawnkowalchuk.milo.data.report

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The SQL for the table of sent reports. Only [SentReportRepository] calls it.
 *
 * There is an insert and there are reads, and nothing else: a sent report is never updated and
 * never deleted.
 */
// TODO(debt): a report that was recorded by mistake ("I sent it" pressed for an email that was
//  discarded) cannot be taken back, and one answered "Not sent" by mistake can only be recorded
//  by sending it again. Neither has a button yet. See docs/FINDINGS_LOG.md (2026-10-06).
@Dao
interface SentReportDao {
    @Insert
    suspend fun insert(report: SentReport): Long

    /** Every sent report, newest first, and again each time one is added. */
    @Query("SELECT * FROM sent_reports ORDER BY sentAtMs DESC, id DESC")
    fun observeAll(): Flow<List<SentReport>>

    /** The reports already sent for exactly this kind and these days, in any order. */
    @Query(
        "SELECT * FROM sent_reports WHERE kind = :kind AND firstDay = :firstDay " +
            "AND lastDay = :lastDay",
    )
    suspend fun findFor(kind: SentReportKind, firstDay: Long, lastDay: Long): List<SentReport>

    /**
     * Stores [report] with the revision number that is next for its kind and days
     * ([nextRevision]). The read and the insert share a transaction, so two reports for one
     * period can never be given the same number.
     *
     * @return the row as stored, with its id and its revision.
     */
    @Transaction
    suspend fun insertAsNextRevision(report: SentReport): SentReport {
        val sentBefore = findFor(report.kind, report.firstDay, report.lastDay)
        val numbered = report.copy(revision = nextRevision(sentBefore))
        return numbered.copy(id = insert(numbered))
    }
}
