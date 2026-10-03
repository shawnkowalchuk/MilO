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

    /** The newest [limit] entries, newest first. */
    fun observeNewest(limit: Int): Flow<List<EventLogEntry>> = dao.observeNewest(limit)
}
