package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.TripRules
import com.shawnkowalchuk.milo.core.trip.TripState
import com.shawnkowalchuk.milo.core.trip.TripStateMachine

/**
 * The controller's hold on the trip service: whether it is in the foreground, and what it was
 * last told.
 *
 * The service reports in on the main thread while the worker, on its own thread, may be deciding
 * to let it go. The functions that take or give up the hold are synchronized, so the two cannot
 * cross: a service that has just reported in is never let go of by a decision made a moment
 * before.
 */
internal class TripServiceLink {
    /** The trip service while it is in the foreground, otherwise null. Read from any thread. */
    @Volatile
    var recorder: TripRecorder? = null
        private set

    /**
     * Triggers the service has handed over that the worker has not reached yet. While there are
     * any, the service is not stopped for being idle: it was started for such a trigger, and
     * the trigger may yet open a trip.
     */
    private var owedTriggers = 0

    /** Set by the worker when a trip has really begun. The next [sync] passes it on. */
    var tripJustStarted = false

    private var lastOrders: Orders? = null

    /**
     * What a service was last told: to record or to watch the parked truck, until when, and
     * beside the parked truck how long to read GPS.
     */
    private data class Orders(
        val service: TripRecorder,
        val watching: Boolean,
        val checkAtMs: Long?,
        val gpsUntilMs: Long?,
    )

    /** The service is in the foreground and is handing a trigger over. */
    @Synchronized
    fun attach(service: TripRecorder) {
        recorder = service
        owedTriggers++
    }

    /** The worker has reached a trigger the service handed over. */
    @Synchronized
    fun triggerReached() {
        owedTriggers--
    }

    /**
     * Tells the service what the state now asks of it: keep recording, watch the parked truck,
     * or stop.
     *
     * @param parkedGpsUntilMs beside the parked truck, when GPS goes off (`parkedGpsUntilMs`),
     * or null to keep it on.
     */
    fun sync(state: TripState?, rules: TripRules, parkedGpsUntilMs: Long?) {
        val service = recorder
        if (service == null || state == null) return
        if (state.wantsService) {
            val watching = state.trip == null
            val checkAtMs = TripStateMachine.nextCheckAtMs(state, rules)
            val orders = Orders(service, watching, checkAtMs, parkedGpsUntilMs)
            // Told again only when something changed: this runs after every GPS fix.
            if (tripJustStarted || orders != lastOrders) {
                if (watching) {
                    service.watchParked(orders.checkAtMs, orders.gpsUntilMs)
                } else {
                    service.record(orders.checkAtMs, tripJustStarted)
                }
            }
            lastOrders = orders
        } else if (letGoIfIdle()) {
            // Let go of before it is told, so that a trigger arriving now asks for the service
            // afresh instead of leaning on one that is about to stop.
            service.stop()
        }
        tripJustStarted = false
    }

    @Synchronized
    private fun letGoIfIdle(): Boolean {
        if (owedTriggers > 0) return false
        recorder = null
        lastOrders = null
        return true
    }

    /**
     * The service reported that it is gone.
     *
     * @return true if that was a surprise: it had not been told to stop.
     */
    @Synchronized
    fun lostUnexpectedly(stopped: TripRecorder): Boolean {
        if (recorder !== stopped) return false
        recorder = null
        lastOrders = null
        return true
    }
}

/**
 * Whether the trip service has work to do: a trip is open, or MilO is watching a parked truck
 * for movement. With neither, the service stops.
 */
internal val TripState.wantsService: Boolean get() = trip != null || waitingToMove
