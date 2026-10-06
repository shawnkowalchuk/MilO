package com.shawnkowalchuk.milo.data.trip

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus

/**
 * One trip, open or closed. Only what phase 1 needs: Business or Personal, addresses and the
 * edited flag arrive with the phases that build them, each as a migration.
 *
 * All times are wall-clock milliseconds since 1970, because they are shown to Shawn and must
 * still mean something after a reboot. Distances are metres; kilometres exist only on screen.
 *
 * @param endedAtMs null while the trip is open.
 * @param startedBy what started it: the truck, or a Start button.
 * @param truckSeen whether the truck was connected at any point during the trip. A manual trip
 * the truck never joined ends by different rules (ADR-002), and after a restart this column is
 * how the rules know which kind of trip they are resuming.
 * @param graceStartedAtMs when the truck was found gone, or null while it is connected. If the
 * grace period runs out, the trip is cut here.
 * @param graceDeadlineMs when the grace period runs out. Set and cleared together with
 * [graceStartedAtMs]. It is stored, not kept on a timer, so a restarted process can tell whether
 * the grace period ran out while it was dead.
 * @param distanceMetres written when the trip closes. 0 while it is open.
 * @param startLatitude null (with the other three coordinates) until the trip closes, and after
 * that if no usable GPS fix was recorded.
 */
@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val status: TripStatus,
    val startedBy: TripStartCause,
    val truckSeen: Boolean,
    val graceStartedAtMs: Long? = null,
    val graceDeadlineMs: Long? = null,
    val distanceMetres: Double = 0.0,
    val startLatitude: Double? = null,
    val startLongitude: Double? = null,
    val endLatitude: Double? = null,
    val endLongitude: Double? = null,
)
