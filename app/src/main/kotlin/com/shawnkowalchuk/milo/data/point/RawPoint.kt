package com.shawnkowalchuk.milo.data.point

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.shawnkowalchuk.milo.core.trip.TrackPoint

/**
 * One GPS fix exactly as the phone reported it. Every fix of a trip is stored, including the
 * ones the distance calculation rejects, so a trip can be recalculated later with better rules.
 *
 * The table lives in its own database file (see `PointsDatabase`), so [tripId] cannot be a
 * foreign key: SQLite cannot enforce one across files. It is a plain indexed number, and any
 * code that ever deletes a trip must delete its points itself.
 *
 * [id] grows in the order fixes arrive and is the order points are read back in. Neither clock
 * can be used for that: the wall clock can jump, and elapsed realtime restarts on a reboot.
 *
 * @param wallClockMs time of day of the fix, milliseconds since 1970.
 * @param elapsedRealtimeMs time since boot. Used for the speed between fixes.
 * @param accuracyMetres null if the phone gave none. Such a fix is stored but never counted.
 * @param speedMetresPerSecond the phone's own speed reading, null if it gave none. Since
 * 2026-10-07 the watch on a parked truck goes by it (`ParkedWatch`); a trip's distance does not.
 */
@Entity(tableName = "raw_points", indices = [Index("tripId")])
data class RawPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val wallClockMs: Long,
    val elapsedRealtimeMs: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMetres: Float?,
    val speedMetresPerSecond: Float?,
) {
    /** The fix in the form the trip rules take. A missing accuracy becomes "infinitely bad". */
    fun toTrackPoint(): TrackPoint = TrackPoint(
        wallClockMs = wallClockMs,
        elapsedRealtimeMs = elapsedRealtimeMs,
        latitude = latitude,
        longitude = longitude,
        accuracyMetres = accuracyMetres ?: Float.POSITIVE_INFINITY,
        speedMetresPerSecond = speedMetresPerSecond,
    )
}
