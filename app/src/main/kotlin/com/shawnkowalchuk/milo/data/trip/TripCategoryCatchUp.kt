package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.schedule.classifyTrip
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Whether the catch-up may sort [trip]: it has closed, it has no category, and Shawn has not
 * set one by hand. The query that finds the trips and the update that writes to them carry the
 * same three conditions (`TripDao.findUnsorted`, `TripDao.sortUnsorted`); this is the rule in
 * Kotlin, tested on its own and applied once more to what the query returns.
 *
 * The status does not matter beyond "not open": a discarded or a deleted trip is sorted too, so
 * that it has its category when it is counted or restored.
 */
fun needsSorting(trip: Trip): Boolean =
    trip.status != TripStatus.OPEN && trip.category == null && !trip.categorySetByHand

/**
 * Gives the trips that were recorded before MilO had a work schedule their Business or
 * Personal, from the schedule as it is when the pass runs and the phone's time zone then.
 *
 * It follows the pattern of the address lookup's catch-up (`platform/address/TripAddresses`): a
 * pass is asked for with [catchUp], the requests go through a channel that keeps only the
 * newest, and one coroutine works them off, so two passes never overlap.
 *
 * **What it never does.** It sorts only a trip that has no category ([needsSorting]), so a trip
 * that was sorted when it was finalised, or that Shawn marked by hand, is never touched, and a
 * later change of the schedule changes no stored trip. And it writes the category and the
 * ran-past-schedule flag only: a trip is never discarded here, whatever the setting for trips
 * outside the schedule says. That setting is applied to a trip once, when it is finalised.
 *
 * @param zone the phone's time zone at the moment of the pass.
 * @param clock wall-clock milliseconds.
 * @param scope the application scope. The coroutine runs in it for the life of the process.
 */
class TripCategoryCatchUp(
    private val trips: TripRepository,
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val crashFileStore: CrashFileStore,
    private val zone: () -> ZoneId,
    private val clock: () -> Long,
    scope: CoroutineScope,
) {
    private val requests = Channel<String>(Channel.CONFLATED)

    init {
        scope.launch { for (reason in requests) runPass(reason) }
    }

    /**
     * Asks for a pass over the trips that are not sorted yet. Safe to call from any thread; it
     * returns at once.
     *
     * @param reason what prompted it, in words, for the event log.
     */
    fun catchUp(reason: String) {
        requests.trySend(reason)
    }

    /**
     * One pass, with its failure kept away from everything else. Without the catch, an
     * exception here (settings or a database that cannot be read) would end the process, and
     * the trip service runs in it. The trips stay unsorted and the next process start tries
     * again.
     */
    private suspend fun runPass(reason: String) {
        try {
            pass(reason)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Every kind of failure, on purpose: sorting old trips is never worth a crash.
            report(failure)
        }
    }

    private suspend fun pass(reason: String) {
        val unsorted = trips.findUnsortedTrips().filter(::needsSorting)
        // The usual case after the first start: nothing to do, and nothing is logged.
        if (unsorted.isEmpty()) return
        val schedule = settings.current().schedule
        val zoneNow = zone()
        val sorted =
            unsorted.mapNotNull { trip ->
                val made = classifyTrip(trip.startedAtMs, trip.endedAtMs, schedule, zoneNow)
                if (trips.sortUnsorted(trip.id, made)) trip to made else null
            }
        eventLog.add(
            atMs = clock(),
            category = EventCategory.TRIP,
            message =
                caughtUpText(
                    reason = reason,
                    sorted = sorted.map { (trip, made) -> trip.id to made },
                    notSorted = unsorted.size - sorted.size,
                ),
            detail =
                sorted.joinToString("\n") { (trip, made) ->
                    val why = classificationText(made, trip.startedAtMs, schedule, zoneNow)
                    "Trip ${trip.id}: $why"
                },
        )
    }

    /**
     * Writes a failed pass to the event log. If the log is what failed, the failure goes to a
     * crash file, which reaches the log at a later start (the same route as the address
     * lookup's catch-up).
     */
    private suspend fun report(failure: Exception) {
        val message = "Sorting earlier trips into Business and Personal failed. They stay unsorted"
        val atMs = clock()
        try {
            eventLog.add(atMs, EventCategory.ERROR, message, failure.stackTraceToString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (logFailure: Exception) {
            val unlogged = IllegalStateException(message, failure)
            unlogged.addSuppressed(logFailure)
            val thread = Thread.currentThread().name
            crashFileStore.write(CrashRecord.from(atMs, thread, unlogged))
        }
    }
}
