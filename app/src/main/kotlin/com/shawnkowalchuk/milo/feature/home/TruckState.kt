package com.shawnkowalchuk.milo.feature.home

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.TruckLinkLook
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.platform.trip.ParkedTruckWatch
import com.shawnkowalchuk.milo.platform.trip.TripActivity

// What Home says about the truck, decided from what MilO really knows. Plain values and pure
// functions, so every state is tested without a phone or a truck. Only the choice of words is
// made here: the words themselves are in strings.xml.
//
// **To add a state:** one constant in [TruckState], with its look and its words, and one line
// in [truckState] that says when it holds. Nothing else decides what the tile shows.

/**
 * What the state is decided from.
 *
 * @param paired whether a truck is stored, or null while the settings have not been read.
 * @param connected what the trip rules believe about the truck's Bluetooth connection, or null
 * before they have picked the stored state up (`TripActivity.truckConnected`). A belief, not a
 * fresh reading; it is read again every time MilO is opened.
 * @param recording whether a trip is open.
 * @param waitingForTruck whether that trip is in its grace period: the truck has gone, and the
 * trip ends unless it comes back.
 * @param autoStartHeldOff whether automatic start is held off: End was pressed with the truck
 * still connected, and a connected truck starts no trip until the hold-off ends.
 * @param setupNeedsAttention the home screen's warning (`needsAttention`): while it shows, a
 * trip may not start by itself.
 * @param parked what MilO is doing beside a truck that is connected and standing still, after
 * the parked rule has ended its trip (`TripActivity.parked`), or null when that is not so.
 * Never set while a trip is open.
 */
internal data class TruckFacts(
    val paired: Boolean?,
    val connected: Boolean?,
    val recording: Boolean,
    val waitingForTruck: Boolean,
    val autoStartHeldOff: Boolean,
    val setupNeedsAttention: Boolean,
    val parked: ParkedTruckWatch? = null,
)

/**
 * Each state the truck's tile can be in, with the look it is drawn in and its words.
 *
 * While a trip is being recorded Home shows the small "Truck" tile, which has room for the
 * [title] alone; the three states that only happen then are marked. The large tile, with its
 * drawing, is shown while no trip is open, and for a moment after the truck has arrived and
 * started one (`ShownArrival.kt`).
 *
 * The design's third look, "Connecting…", is not among them: it is no state of the truck. It
 * is how Home shows that the truck has just arrived, for a fixed time (`ShownArrival.kt`).
 *
 * @param title the state in a word or two. Also the word of the small tile.
 * @param sentence what the state means for the next trip, or null if there is nothing to add.
 * Kept short (since 2026-10-09, when the tile was made lower): one line where it can be, and
 * never more than two, at the usual font size on a phone 360 dp wide.
 */
internal enum class TruckState(val look: TruckLinkLook, val title: Int, val sentence: Int?) {
    /** The settings or the truck's connection have not been read yet. A moment at a start. */
    CHECKING(
        TruckLinkLook.IDLE,
        R.string.home_truck_checking,
        R.string.home_truck_checking_text,
    ),

    /** No truck is paired. The tile is then the way to the pairing screen. */
    NO_TRUCK(TruckLinkLook.IDLE, R.string.home_truck_none, R.string.home_truck_none_text),

    /** A truck is paired, it is not connected, and nothing stands in the way of a start. */
    NOT_CONNECTED(
        TruckLinkLook.IDLE,
        R.string.home_truck_not_connected,
        R.string.home_truck_not_connected_text,
    ),

    /** The same, while the setup checklist says a trip may not start by itself. */
    NOT_CONNECTED_SETUP_OPEN(
        TruckLinkLook.IDLE,
        R.string.home_truck_not_connected,
        R.string.home_truck_may_not_start_text,
    ),

    /** Connected with no trip, because End was pressed: automatic start is held off. */
    CONNECTED_HELD_OFF(
        TruckLinkLook.CONNECTED,
        R.string.home_truck_connected,
        R.string.home_truck_held_off_text,
    ),

    /**
     * Connected with no trip and no hold-off: a start that was refused (the tile above says
     * why), or the moment before a trip opens.
     */
    CONNECTED_NOT_RECORDING(
        TruckLinkLook.CONNECTED,
        R.string.home_truck_connected,
        R.string.home_truck_not_recording_text,
    ),

    /**
     * The parked rule ended the trip, the truck is still connected (by Bluetooth, or by Android
     * Auto on the cable), and MilO watches its position: the next trip starts when it moves.
     * The title is the notification's; the sentence is the tile's own.
     */
    PARKED_WAITING(
        TruckLinkLook.CONNECTED,
        R.string.trip_status_parked,
        R.string.home_trip_parked_detail,
    ),

    /** The same after three days of standing: MilO has stopped watching, and Start is needed. */
    PARKED_NOT_WATCHED(
        TruckLinkLook.CONNECTED,
        R.string.trip_status_parked_not_watched,
        R.string.home_trip_parked_not_watched_detail,
    ),

    /**
     * While recording: the truck is connected. The sentence is the design's, and is read on the
     * large tile in the moment after the truck has arrived and started the trip.
     */
    CONNECTED_RECORDING(
        TruckLinkLook.CONNECTED,
        R.string.home_truck_connected,
        R.string.home_truck_recording_started,
    ),

    /**
     * While recording: the truck is not believed to be connected. A trip started by hand that
     * the truck has not joined, or one that Android Auto alone is holding open.
     */
    RECORDING_WITHOUT_TRUCK(TruckLinkLook.IDLE, R.string.home_truck_not_connected, null),

    /** While recording: the grace period. The sentence is the one Home has always shown. */
    WAITING_TO_RECONNECT(
        TruckLinkLook.IDLE,
        R.string.home_truck_not_connected,
        R.string.trip_status_waiting_for_truck,
    ),
    ;

    /** Whether the state is drawn as connected: lit, with the truck's name in white. */
    val connected: Boolean get() = look == TruckLinkLook.CONNECTED
}

/** The one place that decides which state is shown. The first line that holds wins. */
internal fun truckState(facts: TruckFacts): TruckState = when {
    // A trip is open: what matters is whether the truck is in it.
    facts.waitingForTruck -> TruckState.WAITING_TO_RECONNECT

    facts.recording && facts.connected == true -> TruckState.CONNECTED_RECORDING

    facts.recording -> TruckState.RECORDING_WITHOUT_TRUCK

    // No trip, and MilO is beside a parked truck. The trip rules say so by themselves, and it
    // stands whatever else is read: Android Auto can hold the wait while Bluetooth is down.
    facts.parked == ParkedTruckWatch.WAITING_TO_MOVE -> TruckState.PARKED_WAITING

    facts.parked == ParkedTruckWatch.NO_LONGER_WATCHED -> TruckState.PARKED_NOT_WATCHED

    // No trip. Without a truck there is no connection to speak of.
    facts.paired == null -> TruckState.CHECKING

    !facts.paired -> TruckState.NO_TRUCK

    facts.connected == null -> TruckState.CHECKING

    facts.connected && facts.autoStartHeldOff -> TruckState.CONNECTED_HELD_OFF

    facts.connected -> TruckState.CONNECTED_NOT_RECORDING

    facts.setupNeedsAttention -> TruckState.NOT_CONNECTED_SETUP_OPEN

    else -> TruckState.NOT_CONNECTED
}

/**
 * The facts, from where MilO keeps them: the stored settings (which truck, and the hold-off),
 * the trip controller's published state, and the setup checklist's verdict.
 *
 * @param stored null while the settings have not been read, or cannot be.
 */
internal fun truckFacts(
    stored: MiloSettings?,
    activity: TripActivity,
    setupNeedsAttention: Boolean,
): TruckFacts = TruckFacts(
    paired = stored?.let { it.truckAddress != null },
    connected = activity.truckConnected,
    recording = activity.trip != null,
    waitingForTruck = activity.trip?.waitingForTruck == true,
    autoStartHeldOff = stored?.autoStartHeldOffSinceMs != null,
    setupNeedsAttention = setupNeedsAttention,
    parked = activity.parked,
)

/**
 * What the truck's tile shows.
 *
 * @param truckName the name, as the phone knows it, of the vehicle the tile is about
 * (`vehicleName` in `HomeUi.kt`): the one that is connected or in the trip, and otherwise the
 * first one paired. Null if that vehicle has no name, if none is paired, or while the settings
 * have not been read. What the tile then writes is [vehicleShown]'s to say.
 */
internal data class TruckTileState(val state: TruckState, val truckName: String?)

/**
 * What stands at the end of the tile's first line, where the vehicle is named, or null to
 * write nothing there. With no vehicle paired there is none to name, and while MilO is still
 * reading, only a name it has read already is shown. In every other state there is a vehicle,
 * and one the phone knows no name for is called by [withoutName], "The truck", as on Settings.
 */
internal fun TruckTileState.vehicleShown(withoutName: String): String? = when (state) {
    TruckState.NO_TRUCK -> null
    TruckState.CHECKING -> truckName
    else -> truckName ?: withoutName
}

/**
 * What the tile of the trip being recorded says about the truck, under its two rows, or null
 * while it has nothing to add. Only the grace period is said there, in the sentence Home has
 * always used for it: the truck has gone, and the trip ends unless it comes back. That a trip
 * is recorded with the truck connected, or without it, is the small "Truck" tile's to say.
 */
internal fun recordingNoteRes(state: TruckState): Int? = when (state) {
    TruckState.WAITING_TO_RECONNECT -> state.sentence
    else -> null
}

/**
 * The quieter line on the Start tile. The design's "Or just connect the truck" is only true
 * while a paired truck is not connected and nothing stands in the way of an automatic start.
 * Beside a parked truck that MilO watches, the truck is connected already and the trip starts
 * when it moves; once MilO has stopped watching, the press is what starts it. In every other
 * state the line says what the press does and promises nothing.
 */
internal fun startLineRes(state: TruckState): Int = when (state) {
    TruckState.NOT_CONNECTED -> R.string.home_start_line
    TruckState.PARKED_WAITING -> R.string.home_start_line_parked
    TruckState.PARKED_NOT_WATCHED -> R.string.home_start_line_not_watched
    else -> R.string.home_start_line_by_hand
}
