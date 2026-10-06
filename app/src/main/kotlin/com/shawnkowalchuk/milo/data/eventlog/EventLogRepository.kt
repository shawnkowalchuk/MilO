package com.shawnkowalchuk.milo.data.eventlog

import kotlinx.coroutines.flow.Flow

private const val DAY_MS = 24 * 60 * 60 * 1000L

/**
 * How long a line of the event log is kept. The log is evidence for a trip that did not start
 * or ended oddly, and a question about a trip is asked within weeks, not years. It shares its
 * database file with the trips, and that file must stay far below the 25 MB backup cap.
 */
const val EVENT_LOG_KEEP_DAYS = 90

/**
 * How many of the newest lines are kept whatever their age. The age of a line is judged by the
 * phone's clock, and one start with the clock set wrong must not empty the log; nor should a
 * MilO that was not used for a few months come back with no trace of its last days.
 */
const val EVENT_LOG_KEEP_NEWEST = 1_000

/** How many entries are read from the table at a time when the whole log is read. */
private const val READ_PAGE_SIZE = 500

/**
 * The time before which entries are removed, or null if nothing is to be removed.
 *
 * An entry goes when it is older than [EVENT_LOG_KEEP_DAYS] days **and** is not among the
 * newest [EVENT_LOG_KEEP_NEWEST]: the earlier of the two limits is the one that counts.
 *
 * @param oldestKeptAtMs the time of the oldest of the entries that are kept for being among
 * the newest, or null if the log holds no more than those.
 */
fun trimBeforeMs(nowMs: Long, oldestKeptAtMs: Long?): Long? =
    oldestKeptAtMs?.let { minOf(it, nowMs - EVENT_LOG_KEEP_DAYS * DAY_MS) }

/** The only way the rest of the app reads or writes the event log. */
class EventLogRepository(private val dao: EventLogDao) {
    suspend fun add(atMs: Long, category: EventCategory, message: String, detail: String? = null) {
        dao.insert(
            EventLogEntry(atMs = atMs, category = category, message = message, detail = detail),
        )
    }

    /**
     * The newest [limit] entries, newest first, and again each time the log changes. The limit
     * is what keeps the screen fast when the log holds thousands of lines: the query stops at it
     * and reads them through the index on the time.
     *
     * @param category only the lines of this category, or null for every line.
     */
    fun observeNewest(limit: Int, category: EventCategory? = null): Flow<List<EventLogEntry>> {
        require(limit > 0) { "The number of entries to read must be positive: $limit" }
        return if (category == null) {
            dao.observeNewest(limit)
        } else {
            dao.observeNewestOf(category, limit)
        }
    }

    /** How many lines the log holds. */
    suspend fun count(): Int = dao.count()

    /**
     * Hands the whole log to [each], oldest first, a few hundred entries at a time, so that a
     * log of tens of thousands of lines is never held in memory at once.
     */
    suspend fun readAll(each: suspend (List<EventLogEntry>) -> Unit) {
        var afterAtMs = Long.MIN_VALUE
        var afterId = Long.MIN_VALUE
        while (true) {
            val page = dao.readAfter(afterAtMs, afterId, READ_PAGE_SIZE)
            if (page.isEmpty()) return
            each(page)
            afterAtMs = page.last().atMs
            afterId = page.last().id
        }
    }

    /**
     * Removes the entries that are too old to keep ([trimBeforeMs]). Called once at every
     * process start. Safe to repeat: a second call finds nothing left to remove.
     *
     * @return how many entries were removed.
     */
    suspend fun trim(nowMs: Long): Int {
        val beforeMs = trimBeforeMs(nowMs, dao.atMsOfEntryBehind(EVENT_LOG_KEEP_NEWEST - 1))
        return if (beforeMs == null) 0 else dao.deleteOlderThan(beforeMs)
    }
}
