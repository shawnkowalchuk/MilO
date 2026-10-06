package com.shawnkowalchuk.milo.data.eventlog

import kotlinx.coroutines.flow.Flow

/** The only way the rest of the app reads or writes the event log. */
class EventLogRepository(private val dao: EventLogDao) {
    // TODO(debt): nothing prunes this table yet. It shares a database file with the trips, and
    //  that file must stay far below the 25 MB backup cap, so old entries have to be trimmed
    //  before phase 4 switches backup on. See docs/FINDINGS_LOG.md.
    suspend fun add(atMs: Long, category: EventCategory, message: String, detail: String? = null) {
        dao.insert(
            EventLogEntry(atMs = atMs, category = category, message = message, detail = detail),
        )
    }

    /**
     * The newest [limit] entries, newest first, and again each time the log changes. The limit
     * is what keeps the screen fast when the log holds thousands of lines: the query stops at it
     * and reads them through the index on the time.
     */
    fun observeNewest(limit: Int): Flow<List<EventLogEntry>> {
        require(limit > 0) { "The number of entries to read must be positive: $limit" }
        return dao.observeNewest(limit)
    }
}
