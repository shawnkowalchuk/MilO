package com.shawnkowalchuk.milo.data.point

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

/** The SQL for the raw points table. Only [RawPointRepository] calls it. */
@Dao
interface RawPointDao {
    @Insert
    suspend fun insert(point: RawPoint): Long

    @Query("SELECT * FROM raw_points WHERE tripId = :tripId ORDER BY id")
    suspend fun findForTrip(tripId: Long): List<RawPoint>
}
