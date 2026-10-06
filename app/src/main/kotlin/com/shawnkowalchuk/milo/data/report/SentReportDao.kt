package com.shawnkowalchuk.milo.data.report

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The SQL for the table of sent reports. Only [SentReportRepository] calls it.
 *
 * There is an insert, there are reads, and there is one delete, for a report that was recorded
 * by mistake ([remove]). A sent report is never updated.
 */
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

    @Query("SELECT * FROM sent_reports WHERE id = :id")
    suspend fun findById(id: Long): SentReport?

    @Query("DELETE FROM sent_reports WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /**
     * Removes the report with this [id] from the list, and no other row: the reports that stay
     * keep their revision numbers. The read, the delete and the look at what is left share a
     * transaction, so what is returned is what was true at the moment of the delete.
     *
     * @return the row that was removed with what is left for its period, or null if there is
     * no such row, in which case nothing was changed.
     */
    @Transaction
    suspend fun remove(id: Long): RemovedReport? {
        val report = findById(id) ?: return null
        deleteById(id)
        return RemovedReport(report, findFor(report.kind, report.firstDay, report.lastDay))
    }

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
