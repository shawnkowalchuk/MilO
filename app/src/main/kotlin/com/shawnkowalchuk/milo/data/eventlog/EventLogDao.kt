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
}
