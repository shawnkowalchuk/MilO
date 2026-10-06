package com.shawnkowalchuk.milo.data.report

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The table of sent reports, held in memory. It stands in for the database in the tests of the
 * repository and of the Report screen's records, and answers the two queries as the real ones
 * are written. `insertAsNextRevision` is the interface's own code, so the numbering that is
 * tested is the numbering that runs on the phone.
 */
class FakeSentReportDao : SentReportDao {
    private val stored = MutableStateFlow<List<SentReport>>(emptyList())

    val rows: List<SentReport> get() = stored.value

    /** Set to make the next insert fail once, as a full disk would. */
    var failNextInsert: Exception? = null

    override suspend fun insert(report: SentReport): Long {
        failNextInsert?.let { failure ->
            failNextInsert = null
            throw failure
        }
        val id = stored.value.size + 1L
        stored.value += report.copy(id = id)
        return id
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
