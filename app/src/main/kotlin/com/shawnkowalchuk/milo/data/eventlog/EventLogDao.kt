package com.shawnkowalchuk.milo.data.eventlog

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/** The SQL for the event log table. Only [EventLogRepository] calls it. */
@Dao
interface EventLogDao {
    @Insert
    suspend fun insert(entry: EventLogEntry): Long

    @Query("SELECT * FROM event_log ORDER BY atMs DESC, id DESC LIMIT :limit")
    fun observeNewest(limit: Int): Flow<List<EventLogEntry>>

    /** The same, for the lines of one category only. */
    @Query(
        "SELECT * FROM event_log WHERE category = :category " +
            "ORDER BY atMs DESC, id DESC LIMIT :limit",
    )
    fun observeNewestOf(category: EventCategory, limit: Int): Flow<List<EventLogEntry>>

    /**
     * The next [limit] entries after the one at ([afterAtMs], [afterId]), oldest first: how the
     * whole log is read in pieces. The place is named by the time and the id together, the
     * order the log has, so a line that is written while the log is being read moves nothing.
     */
    @Query(
        "SELECT * FROM event_log " +
            "WHERE atMs > :afterAtMs OR (atMs = :afterAtMs AND id > :afterId) " +
            "ORDER BY atMs, id LIMIT :limit",
    )
    suspend fun readAfter(afterAtMs: Long, afterId: Long, limit: Int): List<EventLogEntry>

    @Query("SELECT COUNT(*) FROM event_log")
    suspend fun count(): Int

    /**
     * The time of the entry that has exactly [newerEntries] entries newer than itself, or null
     * if the log is not that long. With it the trimming can leave the newest entries alone.
     */
    @Query("SELECT atMs FROM event_log ORDER BY atMs DESC, id DESC LIMIT 1 OFFSET :newerEntries")
    suspend fun atMsOfEntryBehind(newerEntries: Int): Long?

    /** Removes the entries dated before [beforeMs] and answers how many they were. */
    @Query("DELETE FROM event_log WHERE atMs < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long): Int
}
