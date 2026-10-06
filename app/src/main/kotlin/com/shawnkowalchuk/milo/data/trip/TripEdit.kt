package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.TripFiled
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.schedule.refileTrip
import com.shawnkowalchuk.milo.core.trip.TripStatus
import java.time.ZoneId

// What Shawn's own hand may do to the times, addresses and distance of a finished trip: edit
// them, and put back what MilO recorded. (A trip he types in new is `TripAddedByHand.kt`.) Pure
// functions: each takes the stored row and answers the row as it is to be stored, so the rules
// are tested without a database, and the one write in `TripDao` only carries the answer out.

/** How the Trips screen and the monthly report mark a trip whose figures are Shawn's own. */
enum class ByHandMark {
    /** Typed in by Shawn: MilO recorded nothing of it. */
    ADDED,

    /** Recorded by MilO, and a time, an address or the distance was changed by Shawn since. */
    EDITED,
}

/** The mark this trip carries, or null for a trip that is as MilO recorded it. */
val Trip.byHandMark: ByHandMark?
    get() = when {
        addedByHand -> ByHandMark.ADDED
        editedByHand -> ByHandMark.EDITED
        else -> null
    }

/**
 * Whether the trip can be edited: a finished trip, and no other. A trip that is being recorded
 * belongs to the trip rules; a discarded or a deleted one is counted or restored first. It is
 * the rule the write in storage matches on (`TripDao.writeByHand`), so the Trips screen, which
 * takes its button from here, cannot offer an edit that storage would refuse.
 */
val Trip.canBeEdited: Boolean get() = status == TripStatus.FINISHED

/** What MilO recorded of a trip that has been edited since. */
data class RecordedValues(val startedAtMs: Long, val endedAtMs: Long, val distanceMetres: Double)

/**
 * What "Restore recorded values" would put back, or null for a trip that has nothing to put
 * back: one that is not edited, one that was added by hand (nothing of it was ever recorded),
 * and one that is not a finished trip.
 */
val Trip.recordedValues: RecordedValues?
    get() {
        if (!canBeEdited || !editedByHand || addedByHand) return null
        return RecordedValues(
            startedAtMs = recordedStartedAtMs ?: return null,
            endedAtMs = recordedEndedAtMs ?: return null,
            distanceMetres = recordedDistanceMetres ?: return null,
        )
    }

/**
 * An address field as Shawn left it.
 *
 * @param text what he typed, or null if he left the field empty. An empty field is a choice
 * too: MilO then stops looking that address up.
 */
data class TypedAddress(val text: String?)

/**
 * What one save of the edit form asks for. It names only what Shawn changed in the form: a null
 * leaves that part of the trip exactly as it is stored, to the millisecond and the metre.
 *
 * @param category the category he pressed in the form, or null if he left the choice alone.
 */
data class TripEdit(
    val startedAtMs: Long? = null,
    val endedAtMs: Long? = null,
    val distanceMetres: Double? = null,
    val startAddress: TypedAddress? = null,
    val endAddress: TypedAddress? = null,
    val category: TripCategory? = null,
)

/**
 * The trip as it is stored after [edit].
 *
 * **What counts as an edit.** The trip is marked as edited when its start, its end, its
 * distance or one of its addresses is different afterwards from what is stored now. Asking for
 * the value a trip already has changes nothing and marks nothing. Business or Personal alone is
 * not an edit in this sense: it is written with the note that Shawn set it by hand, as on the
 * Trips screen, and the trip stays unmarked.
 *
 * **What is kept.** The first edit copies the trip's start, end and distance, which are then
 * still what MilO recorded, into the three "recorded" columns. They are never written again.
 *
 * **Addresses.** An address Shawn changed, or emptied, is his from then on: the lookup never
 * writes to it again.
 *
 * **Business or Personal** follows `refileTrip`: his own choice wins, a category set by hand
 * stays, and otherwise a changed start is sorted again by [schedule].
 *
 * A trip that was added by hand is changed the same way and is never marked as edited: all of
 * it is Shawn's already, and it has nothing recorded to go back to.
 *
 * @return the row to store. Equal to [stored] if the edit changes nothing.
 */
fun editedTrip(stored: Trip, edit: TripEdit, schedule: WorkSchedule?, zone: ZoneId): Trip {
    val startedAtMs = edit.startedAtMs ?: stored.startedAtMs
    val endedAtMs = edit.endedAtMs ?: stored.endedAtMs
    val distanceMetres = edit.distanceMetres ?: stored.distanceMetres
    val startAddressChanged =
        edit.startAddress != null && edit.startAddress.text != stored.startAddress
    val endAddressChanged = edit.endAddress != null && edit.endAddress.text != stored.endAddress
    val startChanged = startedAtMs != stored.startedAtMs
    val endChanged = endedAtMs != stored.endedAtMs
    val valuesChanged =
        startChanged ||
            endChanged ||
            distanceMetres != stored.distanceMetres ||
            startAddressChanged ||
            endAddressChanged
    val filed =
        refileTrip(
            stored = stored.filed,
            startChanged = startChanged,
            endChanged = endChanged,
            startedAtMs = startedAtMs,
            endedAtMs = endedAtMs,
            chosen = edit.category,
            schedule = schedule,
            zone = zone,
        )
    val nowEdited = valuesChanged && !stored.addedByHand
    return stored.copy(
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        distanceMetres = distanceMetres,
        startAddress = if (startAddressChanged) edit.startAddress.text else stored.startAddress,
        endAddress = if (endAddressChanged) edit.endAddress.text else stored.endAddress,
        startAddressByHand = stored.startAddressByHand || startAddressChanged,
        endAddressByHand = stored.endAddressByHand || endAddressChanged,
        category = filed.category,
        categorySetByHand = filed.categorySetByHand,
        ranPastSchedule = filed.ranPastSchedule,
        editedByHand = stored.editedByHand || nowEdited,
        recordedStartedAtMs = stored.recordedStartedAtMs.orIf(nowEdited) { stored.startedAtMs },
        recordedEndedAtMs = stored.recordedEndedAtMs.orIf(nowEdited) { stored.endedAtMs },
        recordedDistanceMetres =
            stored.recordedDistanceMetres.orIf(nowEdited) { stored.distanceMetres },
    )
}

/** This value if there is one. Otherwise, and only if [keep] is true, [recorded]. */
private fun <T : Any> T?.orIf(keep: Boolean, recorded: () -> T?): T? =
    this ?: if (keep) recorded() else null

/**
 * The trip as it is stored after "Restore recorded values", or null if it has nothing to put
 * back ([recordedValues]).
 *
 * **The rule.** The start, the end and the distance become what MilO recorded: the figures the
 * first edit kept. The distance is not worked out again from the GPS points. It would come out
 * the same today, but only while the points file is there (it is kept out of the backup) and
 * the distance thresholds are the ones the trip was measured with; the kept figure depends on
 * neither.
 *
 * An address Shawn typed or emptied is removed and handed back to the lookup, which starts its
 * attempts for this trip afresh; an address the lookup itself had found is kept. The edited
 * mark goes. Business or Personal follows `refileTrip` for the times that come back: a
 * category set by hand stays, otherwise a start that changes is sorted again by [schedule].
 *
 * The three "recorded" columns are left as they are: they are written once.
 */
fun restoredTrip(stored: Trip, schedule: WorkSchedule?, zone: ZoneId): Trip? {
    val recorded = stored.recordedValues ?: return null
    val lookedUpAgain = stored.startAddressByHand || stored.endAddressByHand
    val filed =
        refileTrip(
            stored = stored.filed,
            startChanged = recorded.startedAtMs != stored.startedAtMs,
            endChanged = recorded.endedAtMs != stored.endedAtMs,
            startedAtMs = recorded.startedAtMs,
            endedAtMs = recorded.endedAtMs,
            chosen = null,
            schedule = schedule,
            zone = zone,
        )
    return stored.copy(
        startedAtMs = recorded.startedAtMs,
        endedAtMs = recorded.endedAtMs,
        distanceMetres = recorded.distanceMetres,
        startAddress = stored.startAddress.takeUnless { stored.startAddressByHand },
        endAddress = stored.endAddress.takeUnless { stored.endAddressByHand },
        startAddressByHand = false,
        endAddressByHand = false,
        addressAttempts = if (lookedUpAgain) 0 else stored.addressAttempts,
        addressLastAttemptAtMs = stored.addressLastAttemptAtMs.takeUnless { lookedUpAgain },
        category = filed.category,
        categorySetByHand = filed.categorySetByHand,
        ranPastSchedule = filed.ranPastSchedule,
        editedByHand = false,
    )
}

/**
 * What a finished trip is to become by one of Shawn's own changes: [editedTrip] or
 * [restoredTrip], with what the form asked for. `TripDao.rewriteFinished` calls it on the row
 * it has just read, inside the transaction that writes the answer.
 */
fun interface TripRewrite {
    /** The row to store in place of [stored], or null if the change cannot be made to it. */
    fun of(stored: Trip): Trip?
}

/** How this trip is filed now, in the form `refileTrip` takes. */
private val Trip.filed: TripFiled
    get() = TripFiled(category, categorySetByHand, ranPastSchedule)

/** What became of one of Shawn's own changes to a finished trip's figures. */
sealed interface ByHandOutcome {
    /** The change was stored. [before] and [after] are the row on either side of it. */
    data class Done(val before: Trip, val after: Trip) : ByHandOutcome

    /** Nothing was written: the trip already is what was asked for. */
    data class Unchanged(val trip: Trip) : ByHandOutcome

    /**
     * Nothing was written: the trip is not a finished one, or it has nothing to restore.
     *
     * @param found the trip as it is, or null if there is no such trip.
     */
    data class Refused(val found: Trip?) : ByHandOutcome
}
