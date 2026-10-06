package com.shawnkowalchuk.milo.data.report

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The table of sent reports, held in memory. It stands in for the database in the tests of the
 * repository and of the Report screen's records, and answers the two queries as the real ones
 * are written. `insertAsNextRevision` and `remove` are the interface's own code, so the
 * numbering and the removal that are tested are the ones that run on the phone.
 */
class FakeSentReportDao : SentReportDao {
    private val stored = MutableStateFlow<List<SentReport>>(emptyList())

    val rows: List<SentReport> get() = stored.value

    /** Set to make the next insert fail once, as a full disk would. */
    var failNextInsert: Exception? = null

    /** Set to make the next delete fail once. */
    var failNextDelete: Exception? = null

    /** Ids are never given twice, as in the table: a removed row's id is not used again. */
    private var inserted = 0L

    override suspend fun insert(report: SentReport): Long {
        failNextInsert?.let { failure ->
            failNextInsert = null
            throw failure
        }
        val id = ++inserted
        stored.value += report.copy(id = id)
        return id
    }

    override suspend fun findById(id: Long): SentReport? = stored.value.firstOrNull { it.id == id }

    override suspend fun deleteById(id: Long): Int {
        failNextDelete?.let { failure ->
            failNextDelete = null
            throw failure
        }
        val left = stored.value.filter { it.id != id }
        val removed = stored.value.size - left.size
        stored.value = left
        return removed
    }

    override fun observeAll(): Flow<List<SentReport>> = stored.map { rows ->
        rows.sortedWith(compareByDescending<SentReport> { it.sentAtMs }.thenByDescending { it.id })
    }

    override suspend fun findFor(
        kind: SentReportKind,
        firstDay: Long,
        lastDay: Long,
    ): List<SentReport> = stored.value.filter {
        it.kind == kind && it.firstDay == firstDay && it.lastDay == lastDay
    }
}
