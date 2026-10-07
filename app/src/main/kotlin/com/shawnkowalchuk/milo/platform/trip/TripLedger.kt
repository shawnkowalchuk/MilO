package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.schedule.fileTrip
import com.shawnkowalchuk.milo.core.trip.ActiveTrip
import com.shawnkowalchuk.milo.core.trip.ClosedTrip
import com.shawnkowalchuk.milo.core.trip.Grace
import com.shawnkowalchuk.milo.core.trip.ParkedWatch
import com.shawnkowalchuk.milo.core.trip.TripClosing
import com.shawnkowalchuk.milo.core.trip.TripEffect
import com.shawnkowalchuk.milo.core.trip.TripEndReason
import com.shawnkowalchuk.milo.core.trip.TripEvent
import com.shawnkowalchuk.milo.core.trip.TripProgress
import com.shawnkowalchuk.milo.core.trip.TripRules
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.core.trip.WaitingEnd
import com.shawnkowalchuk.milo.core.trip.confirmByMsFor
import com.shawnkowalchuk.milo.core.trip.leftInAnotherVehicle
import com.shawnkowalchuk.milo.core.trip.movementSince
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.point.RawPointRepository
import com.shawnkowalchuk.milo.data.settings.ParkedTruck
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setDrivenOffTripId
import com.shawnkowalchuk.milo.data.trip.TripRepository
import java.time.ZoneId

/**
 * The open trip as the controller holds it between events: its row, and how far it has got.
 *
 * @param fromParked true if a parked truck's moving started it. Such a trip is removed for good
 * if it loses the truck within its first kilometre (`leftInAnotherVehicle`). Kept in the
 * settings file as well (`setDrivenOffTripId`), so that a restart knows it too.
 */
internal data class OpenTrip(
    val id: Long,
    val startedAtMs: Long,
    val startedBy: TripStartCause,
    val progress: TripProgress = TripProgress(),
    val fromParked: Boolean = false,
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
 * The wait beside a parked truck, which has no trip to store anything under, is kept by
 * [TripParking]; the ledger passes the effects that concern it on.
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

    private val parking = TripParking(settings, points)

    /**
     * Reads the open trip and its fixes from storage. Called once per process, before any rule.
     *
     * @param drivenOffTripId the trip the settings file names as started by a parked truck's
     * moving (`MiloSettings.drivenOffTripId`).
     */
    suspend fun load(rules: TripRules, drivenOffTripId: Long?): StoredTrip? {
        open = null
        val row = trips.findOpenTrip() ?: return null
        val progress = TripProgress.of(points.pointsForTrip(row.id).map { it.toTrackPoint() })
        open =
            OpenTrip(row.id, row.startedAtMs, row.startedBy, progress, row.id == drivenOffTripId)
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
        parking.forget()
    }

    /** A restart found MilO waiting beside the parked truck: the watch starts again. */
    fun resumeWaiting(stored: ParkedTruck?) = parking.resume(stored)

    /** One fix while waiting beside the parked truck. See [TripParking.onFix]. */
    fun watchFix(fix: RawPoint): Long? = parking.onFix(fix)

    /** When the open trip last really moved, as its stored fixes say, or null if it has not. */
    val lastMovementAtMs: Long? get() = open?.progress?.lastMovementAtMs

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
                // A trip that starts because the parked truck moved begins where it was parked,
                // and is remembered as such until it closes: it may yet turn out to be a drive
                // in another vehicle.
                val progress =
                    if (effect.fromParked) parking.startTrip(row.id) else TripProgress()
                if (effect.fromParked) settings.setDrivenOffTripId(row.id)
                open = OpenTrip(row.id, row.startedAtMs, row.startedBy, progress, effect.fromParked)
                val text = startedText(row.id, row.startedBy, effect.fromParked, progress.fixCount)
                LogLine(EventCategory.TRIP, text)
            }

            is TripEffect.StartWaiting -> {
                parking.begin(effect.sinceMs)
                LogLine(EventCategory.TRIP, WAITING_BEGAN)
            }

            is TripEffect.EndWaiting -> {
                parking.end(tripFollows = effect.reason == WaitingEnd.MOVED)
                LogLine(EventCategory.TRIP, waitingEndedText(effect.reason))
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
                close(openTrip(effect), effect, settingsNow)

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
     * @return what the trip rules have to be told about the truck's movement, or null if the
     * fix changed nothing about it: the truck really moved, or the movement last reported was
     * one bad fix and has been taken back.
     */
    suspend fun addFix(fix: RawPoint): TripEvent? {
        val trip = open ?: return null
        val stored = fix.copy(tripId = trip.id)
        points.add(stored)
        val progress = trip.progress.plus(stored.toTrackPoint())
        open = trip.copy(progress = progress)
        return progress.movementSince(trip.progress, trip.startedAtMs, fix.wallClockMs)
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
        settingsNow: TripRuleSettings,
    ): LogLine {
        val minimumTripDistanceMetres = settingsNow.minimumTripDistanceMetres
        val filingRules = settingsNow.filingRules
        val fixes = points.pointsForTrip(trip.id).map { it.toTrackPoint() }
        val closed =
            TripClosing.close(
                points = fixes,
                lastPointNotAfterMs = effect.lastPointNotAfterMs,
                minimumDistanceMetres = minimumTripDistanceMetres.toDouble(),
                falseStart = effect.reason == TripEndReason.FALSE_START,
            )
        val zoneNow = zone()
        if (trip.fromParked) settings.setDrivenOffTripId(null)
        if (leftInAnotherVehicle(trip.fromParked, effect.reason, closed)) {
            return removeForGood(trip, closed, zoneNow)
        }
        val keptByTripRules = closed.status == TripStatus.FINISHED
        val filing =
            fileTrip(trip.startedAtMs, closed.endedAtMs, keptByTripRules, filingRules, zoneNow)
        val changed = trips.closeTrip(trip.id, closed, filing)
        open = null
        // Where the truck stands now. If the trip rules go on to wait beside it, in this same
        // step, this is the place the wait watches and the next trip starts from.
        parking.tripEndedAt(ParkedWatch.placeAfter(closed, fixes))
        val parked =
            parkedText(effect.lastPointNotAfterMs, settingsNow.rules.parkedLimitMs, zoneNow)
                .takeIf { effect.reason == TripEndReason.NO_MOVEMENT }
        val measured =
            closedText(
                closed = closed,
                reason = effect.reason,
                storedFixes = fixes.size,
                minimumTripDistanceMetres = minimumTripDistanceMetres,
                ignored = filing.ignored,
                parked = parked,
            )
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

    /**
     * Removes a trip that was a drive in another vehicle, row and points (Shawn's decision of
     * 2026-10-07: deleted for good, not kept as discarded). The row goes first: if the points
     * cannot be removed after it, they are left where nothing reads them, and the trip is gone
     * all the same. No wait follows: the truck's connection is gone.
     */
    private suspend fun removeForGood(
        trip: OpenTrip,
        closed: ClosedTrip,
        zoneNow: ZoneId,
    ): LogLine {
        val removed = trips.removeOpenTripForGood(trip.id)
        open = null
        points.removeForTrip(trip.id)
        val text = anotherVehicleText(closed, trip.startedAtMs, zoneNow)
        return tripLine(EventCategory.TRIP, trip, text, removed)
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
