package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.NOT_FILED
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.schedule.refileTrip
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import java.time.ZoneId

// A trip MilO missed and Shawn types in afterwards: what its row is made of. A pure function,
// tested without a database; `TripRepository.addByHand` only inserts the answer.

/**
 * A trip MilO missed, as Shawn typed it in.
 *
 * @param startAddress and [endAddress] are what he typed, or null for a field left empty.
 * @param category the category he pressed in the form, or null if he left the choice to the
 * work schedule.
 */
data class TypedTrip(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceMetres: Double,
    val startAddress: String?,
    val endAddress: String?,
    val category: TripCategory?,
)

/**
 * The row for a trip that is added by hand.
 *
 * It is a finished trip from the start, so it is listed and counted like any other, and it can
 * be edited and deleted like any other. It is marked as added by hand. It has no positions and
 * no GPS points, and both its addresses are Shawn's, typed or left empty, so the address lookup
 * never touches it. Nothing of it is "recorded", so it has nothing to restore.
 *
 * It is sorted into Business or Personal by its start and [schedule], like a recorded trip,
 * unless Shawn chose himself. The setting that ignores trips outside the work hours and the
 * minimum trip distance are not applied: both are for trips MilO records by itself, and this
 * one Shawn typed in on purpose.
 */
fun tripAddedByHand(typed: TypedTrip, schedule: WorkSchedule?, zone: ZoneId): Trip {
    val filed =
        refileTrip(
            stored = NOT_FILED,
            startChanged = true,
            endChanged = true,
            startedAtMs = typed.startedAtMs,
            endedAtMs = typed.endedAtMs,
            chosen = typed.category,
            schedule = schedule,
            zone = zone,
        )
    return Trip(
        startedAtMs = typed.startedAtMs,
        endedAtMs = typed.endedAtMs,
        status = TripStatus.FINISHED,
        // Nothing started this trip. Of the two causes there are, "not the truck" is the true
        // one; what tells it from a trip started with the button is the mark below.
        startedBy = TripStartCause.MANUAL,
        truckSeen = false,
        distanceMetres = typed.distanceMetres,
        startAddress = typed.startAddress,
        endAddress = typed.endAddress,
        category = filed.category,
        categorySetByHand = filed.categorySetByHand,
        ranPastSchedule = filed.ranPastSchedule,
        addedByHand = true,
        startAddressByHand = true,
        endAddressByHand = true,
    )
}
