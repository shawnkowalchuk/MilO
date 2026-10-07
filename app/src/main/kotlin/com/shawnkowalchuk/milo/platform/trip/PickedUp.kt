package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.Parked
import com.shawnkowalchuk.milo.core.trip.TripRules
import com.shawnkowalchuk.milo.core.trip.TripState
import com.shawnkowalchuk.milo.core.trip.TripStateMachine
import com.shawnkowalchuk.milo.core.trip.TripTransition
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading

/**
 * What storage held at a process start, and what the trip rules made of it.
 *
 * @param found the stored state as it was read, before the rules looked at it, for the log.
 * @param reading the reading of the truck that was taken for it.
 * @param waitStandsUnread see [storedWaitStandsUnread].
 */
internal class PickedUp(
    val transition: TripTransition,
    val found: TripState,
    val reading: TruckReading,
    val waitStandsUnread: Boolean,
) {
    /** The reading in words, for the event log, with what was made of one that could not be had. */
    val readingText: String get() = reading.describe(waitStandsUnread)
}

/**
 * Whether a stored wait beside the parked truck carries on although the truck could not be read
 * at this process start. Everything else is told "not connected" for such a reading, which is
 * safe for it: a trip waits out its grace period, and nothing starts. A wait has no grace
 * period. Ended on a reading that says nothing, it would stay ended with the truck still
 * connected, and with the link never dropping nothing would start the next trip. So an unknown
 * reading changes nothing about a wait, as it changes nothing at a reconcile (ADR-002, amendment
 * 11), and the next reading that can be had decides.
 *
 * Not past the wait's limit: there the rules stop waiting and start a trip for a connected
 * truck, and an unknown reading must never start a trip. And not beside a stored trip, which is
 * what counts then.
 */
internal fun storedWaitStandsUnread(
    reading: TruckReading,
    tripStored: Boolean,
    parkedSinceMs: Long?,
    atMs: Long,
    rules: TripRules,
): Boolean = !reading.known &&
    !tripStored &&
    parkedSinceMs != null &&
    atMs - parkedSinceMs < rules.waitingLimitMs

/**
 * Reads what storage holds after a process start (the open trip, the hold-off, a wait beside
 * the parked truck), takes a fresh reading of the truck, and asks the trip rules what carries
 * on (ADR-002, "State survives the process"). Nothing is changed here: [TripWorker] carries the
 * answer out. This is a part of the worker, in a file of its own only to keep both readable.
 *
 * TODO(debt): after a restore by Android's backup, the stored wait is taken out of the settings
 *  on another thread (`BackupAftermath`), and nothing orders that with this read: a wait from
 *  the other installation can be picked up here first. See docs/FINDINGS_LOG.md, 2026-10-06.
 */
internal suspend fun pickUpStored(
    ledger: TripLedger,
    evidence: TripEvidence,
    rules: TripRules,
    settingsNow: MiloSettings,
    atMs: Long,
): PickedUp {
    val stored = ledger.load(rules)
    val reading = evidence.readTruck()
    val heldOffSinceMs = settingsNow.autoStartHeldOffSinceMs
    val parkedSinceMs = settingsNow.parkedTruck?.sinceMs
    val waitStands = storedWaitStandsUnread(reading, stored != null, parkedSinceMs, atMs, rules)
    if (waitStands) evidence.waitCarriedOnUnread()
    // A reading of "unknown" counts as "not connected" here: nothing starts on it, and a
    // trip that was recording waits out a grace period, in which a later reading can
    // still show the truck. The price: it also releases a hold-off. The one thing it does
    // not do is end a wait beside the parked truck, which goes by what was stored.
    val truckConnected = reading.connected || waitStands
    val transition =
        TripStateMachine.restore(
            storedTrip = stored?.trip,
            lastRecordedAtMs = stored?.lastRecordedAtMs,
            autoStartHeldOffSinceMs = heldOffSinceMs,
            truckConnected = truckConnected,
            // Android Auto can only be watched from the running service. If it is connected
            // the service says so a moment after it starts, in time to cancel a grace period
            // that was begun here. It is not in time for a wait that Android Auto alone was
            // holding: with the truck's Bluetooth read as gone, that wait ends here.
            androidAutoConnected = false,
            atMs = atMs,
            rules = rules,
            parkedSinceMs = parkedSinceMs,
        )
    val found =
        TripState(
            trip = stored?.trip,
            truckConnected = truckConnected,
            autoStartHeldOffSinceMs = heldOffSinceMs,
            parked = parkedSinceMs?.let { Parked(it) },
        )
    return PickedUp(transition, found, reading, waitStands)
}
