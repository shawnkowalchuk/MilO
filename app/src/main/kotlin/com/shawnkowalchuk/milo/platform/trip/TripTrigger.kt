package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.trip.ParkedGps
import com.shawnkowalchuk.milo.data.settings.TripSound

/**
 * Everything that can prompt the trip controller, apart from GPS fixes and Android Auto changes
 * (which carry data and have their own entry points). ADR-002: every trigger calls the same
 * function, [TripController.onTrigger].
 *
 * A trigger is a hint, not a fact. Those marked "reads the truck" make the controller ask
 * whether the truck is connected right now and tell the trip rules that.
 */
enum class TripTrigger {
    /** The Bluetooth ACL connect broadcast for the truck's address. Trusted as it stands. */
    TRUCK_LINK_CONNECTED,

    /** The companion device "appeared" callback. It starts a trip that must be confirmed. */
    TRUCK_APPEARED,

    /** A disconnect event that names the truck. Trusted as it stands. */
    TRUCK_DISCONNECTED,

    /**
     * Something happened that says nothing by itself: the process started, the app was opened,
     * the phone booted, the app was updated, the service was restarted, a Bluetooth profile
     * changed state. Reads the truck.
     */
    RECONCILE,

    /** Start was pressed, on the phone or on the Android Auto screen. Reads the truck. */
    MANUAL_START,

    /** End was pressed, on the phone or on the Android Auto screen. Reads the truck. */
    MANUAL_END,

    /** A timer the trip rules asked for has run out. Reads the truck. Sent by the service. */
    CHECK_DUE,

    /**
     * The once-a-minute look at the truck during a trip, and while MilO waits beside the parked
     * truck. Reads the truck. Sent by the service.
     */
    POLL,
}

/**
 * Whether the trigger is one of the trip service's own timers. Such a trigger means something
 * only while the service is wanted: for a trip, or for the watch on a parked truck.
 */
val TripTrigger.isServiceTimer: Boolean
    get() = this == TripTrigger.CHECK_DUE || this == TripTrigger.POLL

/**
 * A trigger on its way to the controller through the trip service. When recording has to begin,
 * the service is started first and the trigger rides in the start intent; the service hands it
 * to the controller once it is in the foreground (see [TripController]).
 *
 * @param source where the trigger came from, in words, for the event log.
 * @param atMs wall-clock time the trigger fired. The trip starts then, not when the service is
 * finally up.
 * @param vehicle the address of the paired vehicle the trigger named, or null if it named none
 * (since 2026-10-08): a trip it starts records it.
 */
data class StartRequest(
    val trigger: TripTrigger,
    val source: String,
    val atMs: Long,
    val vehicle: String? = null,
)

/** Starts the trip service. An interface so the controller can be tested without Android. */
fun interface RecordingStarter {
    /**
     * Runs the preflight and asks Android to start the service in the foreground. On failure it
     * also posts the "could not start this trip" notification.
     *
     * @return null if Android accepted the request, otherwise why it could not be made. It may
     * be called from any thread, and it must not block: a trigger calls it directly.
     */
    fun start(request: StartRequest): StartFailure?
}

/**
 * What the controller needs from the trip service while it is in the foreground. Every function
 * may be called from any thread.
 */
interface TripRecorder {
    /**
     * A trip is open: keep recording.
     *
     * @param checkAtMs wall-clock time at which the service must send [TripTrigger.CHECK_DUE],
     * or null if no timer is needed.
     * @param sounds the sounds to play now, in this order, usually none. [TripSound.CONNECT]
     * exactly once per trip, when its recording has really begun
     * (`TripTransition.tripReallyBegan`); [TripSound.DRIVING_OFF] exactly once per trip, when it
     * is first seen driving (`TripProgress.drivenAtMs`). Both in one order when the two moments
     * fall together, the connect sound first.
     */
    fun record(checkAtMs: Long?, sounds: List<TripSound>)

    /**
     * No trip is open, and the truck is connected and parked: stay in the foreground, stop
     * recording, and watch the truck's position at a low rate until the controller says
     * otherwise. Every fix is still handed to the controller, which decides whether the truck
     * has moved, and so is every change of Android Auto, which holds the wait as it holds a
     * trip.
     *
     * @param checkAtMs as in [record]: when the wait has lasted too long.
     * @param gps how GPS is read meanwhile: until when at all, and until when every 5 seconds
     * (`ParkedGps`). A later order with later times turns it on again.
     */
    fun watchParked(checkAtMs: Long?, gps: ParkedGps)

    /**
     * No trip is open: stop recording and stop the service, unless the service has reported in
     * to the controller again since this was called (see [TripController.holds]).
     */
    fun stop()
}
