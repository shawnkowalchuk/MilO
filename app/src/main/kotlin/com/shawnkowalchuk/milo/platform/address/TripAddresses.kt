package com.shawnkowalchuk.milo.platform.address

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Where the trip in progress started, as far as it is known. Held in memory only: the trip's
 * row has no position until the trip closes.
 *
 * @param latitude and [longitude] are the position the address belongs to. The trip rules can
 * still replace a trip's first fix in its first seconds, and the address is then asked for
 * again.
 */
data class OpenTripStart(
    val tripId: Long,
    val latitude: Double,
    val longitude: Double,
    val place: TripPlace,
)

/**
 * Finds the start and end address of each trip and stores them on its row.
 *
 * **It only watches.** It reads the trip controller's published state and the stored trips, and
 * writes nothing but a finished trip's address columns. Ending a trip never waits for it, and
 * whatever goes wrong here leaves the trip as it was: the worst outcome is a trip with no
 * address.
 *
 * **When it looks.** A pass over the trips that are due ([isDueForLookup]) is asked for with
 * [catchUp]: at process start (after the trip controller has dealt with what the last process
 * left behind, which can close a trip this class never saw in progress), when the Trips screen
 * comes to the front, and, by this class itself, when a trip stops being in progress (which
 * looks up that trip and any older one that is due again) and when a trip in progress gets its
 * start position. There is no scheduled job: a trip whose lookup failed waits for the next of
 * those occasions.
 *
 * **One pass at a time.** The requests go through a channel that keeps only the newest and are
 * worked off by one coroutine, so two passes never ask for the same trip, and a request made
 * during a pass leads to one more pass after it.
 *
 * @param isOnline whether the phone has a working internet connection. Without one nothing is
 * asked and no attempt is counted.
 * @param tripActivity the trip controller's state.
 * @param clock wall-clock milliseconds.
 * @param scope the application scope. The two coroutines run in it for the life of the process.
 */
class TripAddresses(
    private val trips: TripRepository,
    private val eventLog: EventLogRepository,
    private val crashFileStore: CrashFileStore,
    private val lookup: AddressLookup,
    private val isOnline: () -> Boolean,
    private val tripActivity: StateFlow<TripActivity>,
    private val clock: () -> Long,
    scope: CoroutineScope,
) {
    private val requests = Channel<String>(Channel.CONFLATED)

    private val mutableOpenTripStart = MutableStateFlow<OpenTripStart?>(null)

    /**
     * The start of the trip in progress. It keeps the last trip's value after that trip has
     * ended, so a reader must match [OpenTripStart.tripId] against the trip it is showing.
     */
    val openTripStart: StateFlow<OpenTripStart?> = mutableOpenTripStart.asStateFlow()

    /**
     * What the last "no network" line was written for: the finished trips, and the trip whose
     * start was waiting. Only the pass coroutine uses it.
     */
    private var putOffFor: Pair<Set<Long>, Long?>? = null

    init {
        scope.launch { for (reason in requests) runPass(reason) }
        scope.launch { watchTheTripInProgress() }
    }

    /**
     * Asks for a pass over the trips that are due. Safe to call from any thread and as often as
     * wanted; it returns at once.
     *
     * @param reason what prompted it, in words, for the event log.
     */
    fun catchUp(reason: String) {
        requests.trySend(reason)
    }

    private suspend fun watchTheTripInProgress() {
        var lastTripId: Long? = null
        tripActivity.collect { activity ->
            val trip = activity.trip
            val ended = lastTripId
            // The row is closed in storage before the controller publishes, so the trip that
            // was in progress a moment ago can be read as finished (or discarded) by now.
            if (ended != null && ended != trip?.tripId) catchUp("trip $ended is over")
            lastTripId = trip?.tripId
            if (trip != null && noteStartOf(trip)) catchUp("trip ${trip.tripId} has a start")
        }
    }

    /** Remembers where [trip] started. Returns true if that is news, and so needs a lookup. */
    private fun noteStartOf(trip: CurrentTrip): Boolean {
        val latitude = trip.startLatitude ?: return false
        val longitude = trip.startLongitude ?: return false
        val known = mutableOpenTripStart.value
        val same =
            known != null &&
                known.tripId == trip.tripId &&
                known.latitude == latitude &&
                known.longitude == longitude
        if (same) return false
        mutableOpenTripStart.value =
            OpenTripStart(trip.tripId, latitude, longitude, TripPlace.LookingUp)
        return true
    }

    /**
     * One pass, with its failure kept away from everything else. Without the catch, an
     * exception here (a database that cannot be read) would end the process, and the trip
     * service runs in it.
     */
    private suspend fun runPass(reason: String) {
        try {
            pass(reason)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Every kind of failure, on purpose: an address is never worth a crash.
            report(failure)
        }
    }

    private suspend fun pass(reason: String) {
        val now = clock()
        val openStart = openStartToLookUp()
        val due = trips.findTripsLackingAddress(MAX_ADDRESS_ATTEMPTS).filter {
            isDueForLookup(it, now)
        }
        if (openStart == null && due.isEmpty()) return
        if (!isOnline()) {
            notePutOff(reason, due, openStart)
            return
        }
        putOffFor = null
        // A lookup that failed (as opposed to finding nothing) says the geocoder cannot be
        // reached. Asking it about the other trips would fail the same way and cost each of
        // them an attempt, so the pass stops there.
        if (openStart != null && !lookUpOpenStart(openStart)) return
        for ((index, trip) in due.withIndex()) {
            if (!lookUpTrip(trip, waitingBehind = due.size - index - 1)) return
        }
    }

    /** Looks up what [trip] lacks and stores it. Returns false if the geocoder failed. */
    private suspend fun lookUpTrip(trip: Trip, waitingBehind: Int): Boolean {
        val start = find(trip.startPlace(), trip.startLatitude, trip.startLongitude, trip)
        val end =
            if (start is PlaceOutcome.Failed) {
                PlaceOutcome.NotAsked
            } else {
                find(trip.endPlace(), trip.endLatitude, trip.endLongitude, forStartOf = null)
            }
        val stillLacking = start.leavesGap || end.leavesGap
        val failed = start is PlaceOutcome.Failed || end is PlaceOutcome.Failed
        val stored =
            trips.recordAddressLookup(
                tripId = trip.id,
                startAddress = (start as? PlaceOutcome.Found)?.address,
                endAddress = (end as? PlaceOutcome.Found)?.address,
                stillLacking = stillLacking,
                atMs = clock(),
            )
        val attempts = trip.addressAttempts + if (stillLacking) 1 else 0
        val line =
            lookupText(
                tripId = trip.id,
                start = start,
                end = end,
                failedAttempts = attempts.takeIf { stillLacking },
                stored = stored,
                leftWaiting = if (failed) waitingBehind else 0,
            )
        eventLog.add(clock(), EventCategory.ADDRESS, line)
        return !failed
    }

    /**
     * The outcome for one end of a trip.
     *
     * @param known what the trip's row says about this end before the lookup.
     * @param forStartOf the trip, when this is its start: an address found for the very same
     * position while the trip was in progress is used again, with no second question.
     */
    private suspend fun find(
        known: TripPlace,
        latitude: Double?,
        longitude: Double?,
        forStartOf: Trip?,
    ): PlaceOutcome {
        if (known is TripPlace.Known) return PlaceOutcome.AlreadyStored
        if (latitude == null || longitude == null) return PlaceOutcome.NoPosition
        val remembered = mutableOpenTripStart.value
        if (forStartOf != null &&
            remembered != null &&
            remembered.tripId == forStartOf.id &&
            remembered.latitude == latitude &&
            remembered.longitude == longitude &&
            remembered.place is TripPlace.Known
        ) {
            return PlaceOutcome.Found(remembered.place.address)
        }
        return ask(latitude, longitude)
    }

    private suspend fun ask(latitude: Double, longitude: Double): PlaceOutcome =
        when (val answer = lookup.lookUp(latitude, longitude)) {
            is LookupAnswer.Failed -> PlaceOutcome.Failed(answer.reason)

            is LookupAnswer.Places ->
                addressLine(answer.candidates)?.let { PlaceOutcome.Found(it) }
                    ?: PlaceOutcome.NothingUsable(answer.candidates.size)
        }

    /** The start of the trip in progress, if it is still waiting for its address. */
    private fun openStartToLookUp(): OpenTripStart? = mutableOpenTripStart.value?.takeIf {
        it.place == TripPlace.LookingUp && tripActivity.value.trip?.tripId == it.tripId
    }

    /**
     * Looks up where the trip in progress started, for the Trips screen. Nothing is stored: the
     * row is not touched while the trip is open. A failure is not counted either; the next
     * pass simply asks again. Returns false if the geocoder failed.
     */
    private suspend fun lookUpOpenStart(start: OpenTripStart): Boolean {
        val outcome = ask(start.latitude, start.longitude)
        val place =
            when (outcome) {
                is PlaceOutcome.Found -> TripPlace.Known(outcome.address)
                is PlaceOutcome.NothingUsable -> TripPlace.NotFound
                else -> TripPlace.LookingUp
            }
        // Only if it is still the same start: the trip may have ended, or its first fix may
        // have been replaced, while the geocoder was answering.
        mutableOpenTripStart.update { if (it == start) start.copy(place = place) else it }
        eventLog.add(clock(), EventCategory.ADDRESS, openStartText(start.tripId, outcome))
        return outcome !is PlaceOutcome.Failed
    }

    /**
     * One line each time what is waiting changes, not one per pass: passes can come in bursts.
     *
     * The finished trips and the start of the trip in progress are compared apart. A trip that
     * starts and ends without a network waits first as a start and then as a finished trip, and
     * as one set of ids the second would look like the first and go unsaid.
     */
    private suspend fun notePutOff(reason: String, due: List<Trip>, openStart: OpenTripStart?) {
        val waiting = due.map { it.id }.toSet() to openStart?.tripId
        if (waiting == putOffFor) return
        putOffFor = waiting
        eventLog.add(clock(), EventCategory.ADDRESS, putOffText(reason, due.size, openStart))
    }

    /**
     * Writes a failed pass to the event log. If the log is what failed, the failure goes to a
     * crash file, which reaches the log at a later start (the same route as
     * `StartupDiagnostics`).
     */
    private suspend fun report(failure: Exception) {
        val message = "The address lookup failed"
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
