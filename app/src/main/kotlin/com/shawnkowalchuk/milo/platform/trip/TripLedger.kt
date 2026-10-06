package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.ActiveTrip
import com.shawnkowalchuk.milo.core.trip.Grace
import com.shawnkowalchuk.milo.core.trip.TripClosing
import com.shawnkowalchuk.milo.core.trip.TripEffect
import com.shawnkowalchuk.milo.core.trip.TripEndReason
import com.shawnkowalchuk.milo.core.trip.TripProgress
import com.shawnkowalchuk.milo.core.trip.TripRules
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.confirmByMsFor
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.point.RawPointRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.TripRepository

/** The open trip as the controller holds it between events: its row, and how far it has got. */
internal data class OpenTrip(
    val id: Long,
    val startedAtMs: Long,
    val startedBy: TripStartCause,
    val progress: TripProgress = TripProgress(),
)

/**
 * The open trip as it was found in storage at process start, in the form the trip rules take.
 *
 * @param lastRecordedAtMs wall-clock time of its newest stored fix, or of its start if it has
 * none: the trip's last sign of life.
 */
internal data class StoredTrip(val trip: ActiveTrip, val lastRecordedAtMs: Long)

/** One line for the event log. The controller adds the time. */
internal data class LogLine(val category: EventCategory, val message: String)

/**
 * The storage side of the trip controller. It carries out the effects of the trip rules: each
 * one is a write through a repository, and each returns the line that records it in the event
 * log. It also stores the GPS fixes and keeps the running distance of the open trip.
 *
 * It holds [open] in memory so that a fix every five seconds does not need a database read to
 * learn which trip it belongs to. Not thread-safe: only the controller's worker calls it.
 */
internal class TripLedger(
    private val trips: TripRepository,
    private val points: RawPointRepository,
    private val settings: SettingsStore,
) {
    /** The open trip, or null when idle or before [load]. */
    var open: OpenTrip? = null
        private set

    /** Reads the open trip and its fixes from storage. Called once per process, before any rule. */
    suspend fun load(rules: TripRules): StoredTrip? {
        open = null
        val row = trips.findOpenTrip() ?: return null
        val progress = TripProgress.of(points.pointsForTrip(row.id).map { it.toTrackPoint() })
        open = OpenTrip(row.id, row.startedAtMs, row.startedBy, progress)
        val graceStartedAtMs = row.graceStartedAtMs
        val graceDeadlineMs = row.graceDeadlineMs
        val trip =
            ActiveTrip(
                startedBy = row.startedBy,
                truckSeen = row.truckSeen,
                grace =
                    if (graceStartedAtMs != null && graceDeadlineMs != null) {
                        Grace(graceStartedAtMs, graceDeadlineMs)
                    } else {
                        null
                    },
                lastMovementAtMs = progress.lastMovementAtMs ?: row.startedAtMs,
                confirmByMs = confirmByMsFor(row.startedBy, row.truckSeen, row.startedAtMs, rules),
            )
        return StoredTrip(trip, lastRecordedAtMs = progress.lastFixAtMs ?: row.startedAtMs)
    }

    /** Forgets what was loaded. The next [load] starts from storage again. */
    fun forget() {
        open = null
    }

    /**
     * Makes one effect of the trip rules real.
     *
     * @param minimumTripDistanceMetres the setting; a closed trip shorter than this is discarded.
     * @return the line that records it.
     */
    suspend fun carryOut(effect: TripEffect, minimumTripDistanceMetres: Int): LogLine =
        when (effect) {
            is TripEffect.StartTrip -> {
                val row = trips.startTrip(effect.startedAtMs, effect.startedBy, effect.truckSeen)
                open = OpenTrip(row.id, row.startedAtMs, row.startedBy)
                LogLine(EventCategory.TRIP, "Trip ${row.id} started by ${row.startedBy}")
            }

            TripEffect.MarkTruckSeen -> {
                val trip = openTrip(effect)
                val changed = trips.markTruckSeen(trip.id)
                tripLine(EventCategory.TRIP, trip, "the truck is connected", changed)
            }

            is TripEffect.StartGrace -> {
                val trip = openTrip(effect)
                val changed = trips.startGrace(trip.id, effect.startedAtMs, effect.deadlineMs)
                val seconds = (effect.deadlineMs - effect.startedAtMs) / MILLIS_PER_SECOND
                tripLine(EventCategory.GRACE, trip, "grace period started ($seconds s)", changed)
            }

            TripEffect.CancelGrace -> {
                val trip = openTrip(effect)
                val changed = trips.cancelGrace(trip.id)
                tripLine(EventCategory.GRACE, trip, "grace period cancelled", changed)
            }

            is TripEffect.EndTrip -> close(openTrip(effect), effect, minimumTripDistanceMetres)

            is TripEffect.HoldOffAutoStart -> {
                settings.setAutoStartHeldOffSinceMs(effect.sinceMs)
                LogLine(
                    EventCategory.TRIP,
                    "Automatic start held off: ended with the truck connected",
                )
            }

            is TripEffect.ReleaseHoldOff -> {
                settings.setAutoStartHeldOffSinceMs(null)
                LogLine(EventCategory.TRIP, "Automatic start no longer held off: ${effect.reason}")
            }
        }

    /**
     * Stores one fix under the open trip and adds it to the running distance.
     *
     * @param fix as the location recorder built it. Its trip id is filled in here, because only
     * the controller's side knows which trip is open.
     * @return true if the fix added distance: the truck really moved.
     */
    suspend fun addFix(fix: RawPoint): Boolean {
        val trip = open ?: return false
        val stored = fix.copy(tripId = trip.id)
        points.add(stored)
        val progress = trip.progress.plus(stored.toTrackPoint())
        open = trip.copy(progress = progress)
        return progress.distance.metres > trip.progress.distance.metres
    }

    /**
     * Closes the trip. The distance, the end time and the two positions are worked out from the
     * stored fixes, not from the running total: the fixes recorded after the truck was found
     * gone have to be cut off first (see [TripClosing]).
     */
    private suspend fun close(
        trip: OpenTrip,
        effect: TripEffect.EndTrip,
        minimumTripDistanceMetres: Int,
    ): LogLine {
        val fixes = points.pointsForTrip(trip.id).map { it.toTrackPoint() }
        val closed =
            TripClosing.close(
                points = fixes,
                lastPointNotAfterMs = effect.lastPointNotAfterMs,
                minimumDistanceMetres = minimumTripDistanceMetres.toDouble(),
                falseStart = effect.reason == TripEndReason.FALSE_START,
            )
        val changed = trips.closeTrip(trip.id, closed)
        open = null
        return tripLine(
            EventCategory.TRIP,
            trip,
            closedText(closed, effect.reason, fixes.size, minimumTripDistanceMetres),
            changed,
        )
    }

    private fun openTrip(effect: TripEffect): OpenTrip =
        checkNotNull(open) { "The trip rules asked for $effect, but no trip is open in storage" }

    /**
     * @param changed what the repository returned: false means the row was no longer open, which
     * should never happen and is said out loud in the log when it does.
     */
    private fun tripLine(
        category: EventCategory,
        trip: OpenTrip,
        text: String,
        changed: Boolean,
    ): LogLine {
        val surprise = if (changed) "" else " (the row was not open: nothing was written)"
        return LogLine(category, "Trip ${trip.id}: $text$surprise")
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
