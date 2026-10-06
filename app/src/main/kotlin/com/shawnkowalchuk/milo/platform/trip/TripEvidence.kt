package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.AndroidAutoHoldGuard
import com.shawnkowalchuk.milo.core.trip.LostDisconnectDetector
import com.shawnkowalchuk.milo.core.trip.TripEvent
import com.shawnkowalchuk.milo.core.trip.TripState
import com.shawnkowalchuk.milo.platform.bluetooth.TruckConnectionSource
import com.shawnkowalchuk.milo.platform.bluetooth.TruckReading

/**
 * What a trigger turned out to mean.
 *
 * @param event what the trip rules are told, or null if the trigger tells them nothing.
 * @param reading the reading of the truck taken for the trigger, or null if it needed none.
 * @param routine true for a poll that only confirms what is already believed about the truck.
 * One arrives every minute of every trip, so it is not worth a line in the event log.
 * @param linkNotSeenYet true when the reading said "not connected" and was not passed on
 * because no reading has yet shown the truck connected on its present link.
 */
internal data class Evidence(
    val event: TripEvent?,
    val reading: TruckReading? = null,
    val routine: Boolean = false,
    val linkNotSeenYet: Boolean = false,
)

/**
 * Turns what the outside world reports into what the trip rules are told. A trigger is a hint;
 * the rules take facts. For most triggers the fact is a reading of the truck's connection taken
 * here. Four kinds of report are not passed on as they come:
 *
 * - **A reading of "unknown"** (MilO may not use Bluetooth, or Bluetooth did not answer). It is
 *   never "connected", and it is not "not connected" either: a trip must not start on it, and a
 *   trip that is recording must not be put into its grace period by it. See [evidenceFor].
 * - **A reading of "not connected" for a link no reading has seen yet** (ADR-002, amendment
 *   18). See [provesNothingYet].
 * - **The once-a-minute poll** (ADR-002, amendment 6). A reading of "not connected" that
 *   contradicts what is believed is only believed the second time in a row.
 * - **Android Auto's "connected"**, which is not believed for ever once the truck has gone
 *   (see [AndroidAutoHoldGuard]).
 *
 * Not thread-safe: only [TripWorker] calls it.
 */
internal class TripEvidence(private val truck: TruckConnectionSource) {
    private val lostDisconnect = LostDisconnectDetector()
    private val androidAutoGuard = AndroidAutoHoldGuard()

    /** What Android Auto last reported, believed or not. */
    private var androidAutoReported = false

    /**
     * Whether a reading has shown the truck connected since its link was last made. A
     * link-level connect event is trusted as it stands, but on most Android versions a reading
     * does not look at the link: it asks the hands-free and the audio profile, which connect
     * seconds after the link, and which a truck can be connected without. A reading that has
     * shown this link once has proved it can see it.
     */
    private var truckSeenOnThisLink = false

    /**
     * Takes note of a trigger before anything is read for it. The worker calls this first,
     * ahead even of the reading taken when the stored state is picked up: a reading comes
     * after the event that prompted it, and must be able to see the link that event made.
     */
    fun arrived(request: StartRequest) {
        // Any trigger but the poll brings evidence of its own, so the count of doubtful polls
        // starts again.
        if (request.trigger != TripTrigger.POLL) lostDisconnect.reset()
        if (request.trigger == TripTrigger.TRUCK_LINK_CONNECTED) truckSeenOnThisLink = false
    }

    /** Is the truck connected right now? */
    suspend fun readTruck(): TruckReading {
        val reading = truck.read()
        if (reading.connected) truckSeenOnThisLink = true
        return reading
    }

    /**
     * What [request] tells the trip rules, reading the truck where the trigger calls for it.
     *
     * What an unknown reading becomes depends on the trigger:
     * - **A reconcile or a poll** tells the rules nothing. Whatever is believed stands.
     * - **A timer or a button** has to act, so the rules are told what they already believe
     *   about the truck. A timer only runs while the truck is believed gone, so a grace period
     *   that runs out still closes its trip, and a companion start that nothing confirmed is
     *   still a false start.
     *
     * [arrived] must have been called for [request] first.
     */
    suspend fun evidenceFor(request: StartRequest, state: TripState): Evidence {
        val atMs = request.atMs
        return when (request.trigger) {
            TripTrigger.TRUCK_LINK_CONNECTED -> Evidence(TripEvent.TruckLinkConnected(atMs))

            TripTrigger.TRUCK_APPEARED -> Evidence(TripEvent.TruckAppeared(atMs))

            TripTrigger.TRUCK_DISCONNECTED ->
                Evidence(TripEvent.TruckConnection(connected = false, atMs))

            TripTrigger.RECONCILE -> {
                val reading = readTruck()
                val unseen = provesNothingYet(reading, state)
                val news = reading.known && !unseen
                val event = TripEvent.TruckConnection(reading.connected, atMs)
                Evidence(event.takeIf { news }, reading, linkNotSeenYet = unseen)
            }

            TripTrigger.CHECK_DUE -> {
                val reading = readTruck()
                Evidence(TripEvent.TruckConnection(reading.orBelieved(state), atMs), reading)
            }

            TripTrigger.MANUAL_START -> {
                val reading = readTruck()
                Evidence(TripEvent.ManualStart(reading.orBelieved(state), atMs), reading)
            }

            TripTrigger.MANUAL_END -> {
                val reading = readTruck()
                Evidence(TripEvent.ManualEnd(reading.orBelieved(state), atMs), reading)
            }

            TripTrigger.POLL -> pollEvidence(state, atMs)
        }
    }

    private fun TruckReading.orBelieved(state: TripState): Boolean =
        if (known) connected else state.truckConnected

    /**
     * Whether [reading] is a "not connected" that says nothing about the trip (ADR-002,
     * amendment 18): a trip is open, the truck is believed connected, and no reading has yet
     * shown it connected on its present link. Then the belief rests on a link-level connect
     * event, and this reading has not proved that it can see that link. Either the profiles
     * are not up yet (they follow the link by seconds), or this truck is connected without
     * them, and every reading would say "not connected" for the whole drive.
     *
     * The price: until a reading has shown the truck, a disconnect that is never reported is
     * not caught by a reading either. A missed trip is worse.
     */
    private fun provesNothingYet(reading: TruckReading, state: TripState): Boolean =
        reading.known &&
            !reading.connected &&
            state.trip != null &&
            state.truckConnected &&
            !truckSeenOnThisLink

    private suspend fun pollEvidence(state: TripState, atMs: Long): Evidence {
        val reading = readTruck()
        // The phone could not say. That is neither of the two readings the count is about, so
        // it is not counted and it does not start the count again.
        if (!reading.known) return Evidence(null, reading)
        if (provesNothingYet(reading, state)) {
            return Evidence(null, reading, linkNotSeenYet = true)
        }
        val contradictsConnected = !reading.connected && state.truckConnected
        // A reading that agrees with what is believed, or that shows the truck connected, is
        // passed on as it is. Only a contradicting "not connected" is counted first: the
        // Bluetooth profile state can lag behind the link.
        val believed = !contradictsConnected || lostDisconnect.onReading(connected = false)
        if (!contradictsConnected) lostDisconnect.reset()
        val event = TripEvent.TruckConnection(reading.connected, atMs)
        val routine = reading.connected == state.truckConnected
        return Evidence(event.takeIf { believed }, reading, routine)
    }

    /**
     * Android Auto reported its connection.
     *
     * @param state what the trip rules know, or null if the stored state has not been picked up
     * yet. The report is remembered either way.
     */
    fun androidAutoReported(
        connected: Boolean,
        state: TripState?,
        atMs: Long,
    ): TripEvent.AndroidAutoConnection? {
        androidAutoReported = connected
        if (state == null) return null
        val believed = androidAutoGuard.believed(connected, state.truckConnected, atMs)
        return TripEvent.AndroidAutoConnection(believed, atMs)
    }

    /**
     * Looks again at whether Android Auto's last report may be believed. Called after every
     * poll, which is what lets the guard notice how long Android Auto has been alone.
     *
     * @return the event to tell the rules if what may be believed has changed, otherwise null.
     */
    fun androidAutoAfterPoll(state: TripState?, atMs: Long): TripEvent.AndroidAutoConnection? {
        if (state == null) return null
        val believed = androidAutoGuard.believed(androidAutoReported, state.truckConnected, atMs)
        if (believed == state.androidAutoConnected) return null
        return TripEvent.AndroidAutoConnection(believed, atMs)
    }
}
