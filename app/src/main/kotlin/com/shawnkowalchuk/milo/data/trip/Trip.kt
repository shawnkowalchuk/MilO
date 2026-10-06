package com.shawnkowalchuk.milo.data.trip

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus

/**
 * One trip, open or closed: recorded by MilO, or typed in by Shawn afterwards.
 *
 * A row is never removed. A trip under the minimum distance is kept as discarded, and one that
 * Shawn deletes is kept as deleted; both are a matter of [status] alone, so each can be undone.
 *
 * All times are wall-clock milliseconds since 1970, because they are shown to Shawn and must
 * still mean something after a reboot. Distances are metres; kilometres exist only on screen.
 *
 * @param endedAtMs null while the trip is open.
 * @param startedBy what started it: the truck, or a Start button. A trip that was typed in by
 * hand ([addedByHand]) has the second: nothing started it, and it was Shawn, not the truck.
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
 * @param startAddress where the trip started, as one short line such as "12 Shop Rd, Edmonton".
 * Null until the phone's geocoder has been asked and has answered: the address is looked up
 * after the trip closes, needs a network connection, and some places have none. Or what Shawn
 * typed ([startAddressByHand]).
 * @param endAddress where it ended. Null on the same terms.
 * @param addressAttempts how many lookups have left an address of this trip missing. At
 * `MAX_ADDRESS_ATTEMPTS` (`platform/address/AddressRetry.kt`) the trip is no longer asked about.
 * @param addressLastAttemptAtMs when the addresses were last looked up, or null if never. The
 * next attempt waits a while after it.
 * @param category Business or Personal. Null while the trip is open, and for a closed trip
 * that has not been sorted yet: one recorded before MilO had a work schedule, until the
 * catch-up at the next process start reaches it. Written when the trip is finalised, from the
 * schedule as it is then, and afterwards only by Shawn's own hand.
 * @param categorySetByHand true once Shawn has chosen [category] himself on the Trips screen.
 * Nothing but another choice of his changes the category of such a trip.
 * @param ranPastSchedule true if the schedule made this a Business trip and it ended after the
 * end time of the day it started on. It is a fact about the trip and the schedule at the moment
 * it was sorted, and is kept when Shawn changes the category; the screens show it only while the
 * trip is Business.
 * @param ignoredOutsideSchedule true if the trip was stored as discarded for one reason only:
 * it turned out Personal while trips outside the schedule were set to be ignored. It says why a
 * discarded trip was discarded, and means nothing once the trip is counted after all.
 * @param addedByHand true for a trip Shawn typed in on the phone because MilO missed it. It
 * has no GPS points and no positions, and both its addresses are his. The monthly report marks
 * such a trip.
 * @param editedByHand true while a time, an address or the distance of a recorded trip is
 * Shawn's and not what MilO recorded. Set by a save of the edit form that changed one of them,
 * and taken away again only by "Restore recorded values". Never set on a trip that was added
 * by hand, which has nothing recorded to differ from. The monthly report marks such a trip.
 * @param startAddressByHand true once Shawn has typed, changed or emptied [startAddress]
 * himself. The address lookup then leaves that address alone for good, empty or not.
 * @param endAddressByHand the same for [endAddress].
 * @param recordedStartedAtMs what [startedAtMs] was when the trip was first edited, which is
 * what MilO recorded. Null on a trip that has never been edited. Written once and never
 * changed, so that "Restore recorded values" can always put it back. With the two columns
 * below it is the only place the recorded figures survive an edit: the start of a trip is the
 * moment it was opened and its end depends on a cut-off that is not stored, so neither can be
 * worked out again from the GPS points.
 * @param recordedEndedAtMs the same for [endedAtMs].
 * @param recordedDistanceMetres the same for [distanceMetres].
 */
// The index is for the Trips screen, which reads the trips that started in a span of time.
@Entity(tableName = "trips", indices = [Index("startedAtMs")])
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
    val startAddress: String? = null,
    val endAddress: String? = null,
    // The default is also declared to SQLite, so a table made by the migration from version 1
    // (ALTER TABLE needs one for a NOT NULL column) is the same as a table made new.
    @ColumnInfo(defaultValue = "0") val addressAttempts: Int = 0,
    val addressLastAttemptAtMs: Long? = null,
    val category: TripCategory? = null,
    // Declared to SQLite for the same reason as the attempts above: the migration from
    // version 2 adds these three with ALTER TABLE, which needs a default for the stored rows.
    @ColumnInfo(defaultValue = "0") val categorySetByHand: Boolean = false,
    @ColumnInfo(defaultValue = "0") val ranPastSchedule: Boolean = false,
    @ColumnInfo(defaultValue = "0") val ignoredOutsideSchedule: Boolean = false,
    // And these four for the same reason again: the migration from version 3 adds them.
    @ColumnInfo(defaultValue = "0") val addedByHand: Boolean = false,
    @ColumnInfo(defaultValue = "0") val editedByHand: Boolean = false,
    @ColumnInfo(defaultValue = "0") val startAddressByHand: Boolean = false,
    @ColumnInfo(defaultValue = "0") val endAddressByHand: Boolean = false,
    val recordedStartedAtMs: Long? = null,
    val recordedEndedAtMs: Long? = null,
    val recordedDistanceMetres: Double? = null,
)
