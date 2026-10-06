package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.schedule.FilingRules
import com.shawnkowalchuk.milo.core.schedule.fileTrip
import com.shawnkowalchuk.milo.core.trip.ActiveTrip
import com.shawnkowalchuk.milo.core.trip.Grace
import com.shawnkowalchuk.milo.core.trip.TripClosing
import com.shawnkowalchuk.milo.core.trip.TripEffect
import com.shawnkowalchuk.milo.core.trip.TripEndReason
import com.shawnkowalchuk.milo.core.trip.TripProgress
import com.shawnkowalchuk.milo.core.trip.TripRules
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.confirmByMsFor
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.point.RawPointRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.TripRepository
import java.time.ZoneId

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
 *
 * @param zone the phone's time zone, asked for only when a trip is closed.
 */
internal class TripLedger(
    private val trips: TripRepository,
    private val points: RawPointRepository,
    private val settings: SettingsStore,
    private val zone: () -> ZoneId,
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
     * @param settingsNow the settings as they were read for this event. One effect only uses
     * them, the end of a trip: a closed trip shorter than the minimum distance is discarded,
     * and the work schedule sorts the trip into Business or Personal.
     * @return the line that records it.
     */
    suspend fun carryOut(effect: TripEffect, settingsNow: TripRuleSettings): LogLine =
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

            is TripEffect.EndTrip ->
                close(
                    trip = openTrip(effect),
                    effect = effect,
                    minimumTripDistanceMetres = settingsNow.minimumTripDistanceMetres,
                    filingRules = settingsNow.filingRules,
                )

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
     *
     * This is also the one moment the work schedule is asked about a trip: once the trip rules
     * have finished with it, it is sorted into Business or Personal by when it started, and the
     * result is stored in the same write that closes it. The trip was recorded to its end
     * whatever the schedule says; the schedule only decides what it is saved as, and with
     * "ignore" chosen, that a trip which turns out Personal is stored as discarded.
     */
    private suspend fun close(
        trip: OpenTrip,
        effect: TripEffect.EndTrip,
        minimumTripDistanceMetres: Int,
        filingRules: FilingRules?,
    ): LogLine {
        val fixes = points.pointsForTrip(trip.id).map { it.toTrackPoint() }
        val closed =
            TripClosing.close(
                points = fixes,
                lastPointNotAfterMs = effect.lastPointNotAfterMs,
                minimumDistanceMetres = minimumTripDistanceMetres.toDouble(),
                falseStart = effect.reason == TripEndReason.FALSE_START,
            )
        val zoneNow = zone()
        val keptByTripRules = closed.status == TripStatus.FINISHED
        val filing =
            fileTrip(trip.startedAtMs, closed.endedAtMs, keptByTripRules, filingRules, zoneNow)
        val changed = trips.closeTrip(trip.id, closed, filing)
        open = null
        val measured =
            closedText(closed, effect.reason, fixes.size, minimumTripDistanceMetres, filing.ignored)
        val sorted =
            filedText(
                filing = filing,
                kept = keptByTripRules && !filing.ignored,
                startedAtMs = trip.startedAtMs,
                schedule = filingRules?.schedule,
                zone = zoneNow,
            )
        return tripLine(EventCategory.TRIP, trip, "$measured; $sorted", changed)
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
