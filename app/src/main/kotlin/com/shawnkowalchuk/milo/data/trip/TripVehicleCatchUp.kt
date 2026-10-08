package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setTripVehiclesFilled
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gives the trips recorded before MilO knew several vehicles (2026-10-08) the vehicle they were
 * in: the truck, the only one there was. Database version 7 added the column empty, and the
 * truck's address is in the settings file, which a migration cannot read.
 *
 * It runs at a process start until it has once got through: it fills the trips that were in the
 * truck (seen connected, or typed in by hand) and have no vehicle, and notes in the settings
 * that it is done. With no truck stored there is nothing to fill, and it notes the same. Done
 * twice, it changes nothing: it only fills what is empty.
 *
 * @param clock wall-clock milliseconds.
 * @param scope the application scope.
 */
class TripVehicleCatchUp(
    private val trips: TripRepository,
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val crashFileStore: CrashFileStore,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
) {
    private val oneAtATime = Mutex()

    /**
     * Fills the trips, if it has not been done. Safe to call from any thread; it returns at
     * once.
     *
     * @param reason what prompted it, in words, for the event log.
     */
    fun catchUp(reason: String) {
        scope.launch { oneAtATime.withLock { runPass(reason) } }
    }

    /**
     * One pass, with its failure kept away from everything else: an exception here would end
     * the process, and the trip service runs in it. The next process start tries again.
     */
    private suspend fun runPass(reason: String) {
        try {
            pass(reason)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Every kind of failure, on purpose: naming the vehicle of old trips is never worth
            // a crash.
            report(failure)
        }
    }

    private suspend fun pass(reason: String) {
        val now = settings.current()
        if (now.tripVehiclesFilled) return
        val truck = now.truckAddress
        val filled = if (truck == null) 0 else trips.fillVehicle(truck)
        settings.setTripVehiclesFilled()
        val what =
            if (truck == null) {
                "No truck is paired, so no earlier trip was given a vehicle ($reason)"
            } else {
                "Earlier trips in the truck given its address ($truck): $filled ($reason)"
            }
        eventLog.add(clock(), EventCategory.TRIP, what)
    }

    /** As `TripCategoryCatchUp` does: the event log, or a crash file if the log failed too. */
    private suspend fun report(failure: Exception) {
        val message = "Giving earlier trips the truck's address failed. They are tried again"
        val atMs = clock()
        try {
            eventLog.add(atMs, EventCategory.ERROR, message, failure.stackTraceToString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (logFailure: Exception) {
            val unlogged = IllegalStateException(message, failure)
            unlogged.addSuppressed(logFailure)
            crashFileStore.write(CrashRecord.from(atMs, Thread.currentThread().name, unlogged))
        }
    }
}
