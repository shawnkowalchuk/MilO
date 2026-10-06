package com.shawnkowalchuk.milo.platform.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.companion.DevicePresenceEvent
import com.shawnkowalchuk.milo.platform.trip.TripTrigger

// What each thing Android reports about a Bluetooth device means for the trip controller. Plain
// functions with no Android object in them (the constants are compiled in), so the decisions can
// be read and tested in one place. The receiver and the companion service only gather the facts
// and carry the decision out.

/** The four broadcasts the Bluetooth receiver listens for. They are also in the manifest. */
internal val TRUCK_BROADCAST_ACTIONS =
    listOf(
        BluetoothDevice.ACTION_ACL_CONNECTED,
        BluetoothDevice.ACTION_ACL_DISCONNECTED,
        BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
    )

/** What MilO does about one report from Android. */
internal sealed interface SignalDecision {
    /**
     * Tell the trip controller.
     *
     * @param source where the trigger came from, in words, for the event log.
     */
    data class Fire(val trigger: TripTrigger, val source: String) : SignalDecision

    /** Not about the truck. The line still goes to the event log: it proves the report arrived. */
    data class Ignore(val why: String) : SignalDecision
}

/**
 * A Bluetooth broadcast in the form the decision needs.
 *
 * @param what the broadcast in words, such as "ACL connected".
 * @param trigger what it means if it is about the truck.
 */
internal data class BluetoothSignal(val what: String, val trigger: TripTrigger)

/**
 * Reads one of the four broadcasts.
 *
 * - The link itself (ACL) going up or down is trusted as it stands (ADR-002), but only for the
 *   classic transport: that is the one the truck's hands-free connection uses. A low-energy
 *   link to the same address says nothing certain about it, so it only prompts a reading.
 * - A hands-free or audio profile changing state prompts a reading too. This is ADR-002's
 *   independent detector: it comes from a different part of the Bluetooth stack than the link
 *   events. A reading, because the other profile may still be connected, and because a profile
 *   can reconnect without a new link, which must not release the hold-off.
 *
 * @param profileState the broadcast's `EXTRA_STATE`, for the two profile broadcasts.
 * @param transport the broadcast's `EXTRA_TRANSPORT`, which only Android 13 and later send.
 * Null when it is absent.
 * @return null for an action that is not one of the four, and for a profile that is only on its
 * way to a state ("connecting", "disconnecting"), which changes nothing yet.
 */
internal fun bluetoothSignal(
    action: String?,
    profileState: Int?,
    transport: Int?,
): BluetoothSignal? {
    val lowEnergy = transport == BluetoothDevice.TRANSPORT_LE
    return when (action) {
        BluetoothDevice.ACTION_ACL_CONNECTED ->
            if (lowEnergy) {
                BluetoothSignal("low-energy link connected", TripTrigger.RECONCILE)
            } else {
                BluetoothSignal("ACL connected", TripTrigger.TRUCK_LINK_CONNECTED)
            }

        BluetoothDevice.ACTION_ACL_DISCONNECTED ->
            if (lowEnergy) {
                BluetoothSignal("low-energy link disconnected", TripTrigger.RECONCILE)
            } else {
                BluetoothSignal("ACL disconnected", TripTrigger.TRUCK_DISCONNECTED)
            }

        BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED ->
            profileSignal("hands-free", profileState)

        BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> profileSignal("audio", profileState)

        else -> null
    }
}

private fun profileSignal(profile: String, state: Int?): BluetoothSignal? = when (state) {
    BluetoothProfile.STATE_CONNECTED ->
        BluetoothSignal("$profile profile connected", TripTrigger.RECONCILE)

    BluetoothProfile.STATE_DISCONNECTED ->
        BluetoothSignal("$profile profile disconnected", TripTrigger.RECONCILE)

    else -> null
}

/**
 * Decides what a Bluetooth broadcast becomes. The broadcast fires for every device the phone
 * connects to (earbuds, a watch), so everything turns on whether [address] is the truck's.
 *
 * @param via which receiver heard it, for the event log.
 * @param address the address of the device the broadcast is about, or null if it named none.
 */
internal fun decideBroadcast(
    via: String,
    signal: BluetoothSignal,
    address: String?,
    lookup: TruckLookup,
): SignalDecision {
    val device = address ?: "a device it does not name"
    val truck = lookup.truck
    return when {
        // Whose event it is cannot be told. It may be the truck's, so it is not dropped: the
        // controller reads the truck's connection, and that reading decides.
        lookup.problem != null -> {
            val source = "$via: ${signal.what} for $device, and ${lookup.problem}"
            SignalDecision.Fire(TripTrigger.RECONCILE, source)
        }

        truck == null ->
            SignalDecision.Ignore("$via: ${signal.what} for $device. No truck is paired. Ignored")

        truck.isDevice(address, associationId = null) ->
            SignalDecision.Fire(signal.trigger, "$via: ${signal.what} for the truck ($device)")

        else ->
            SignalDecision.Ignore("$via: ${signal.what} for another device ($device). Ignored")
    }
}

/** What a companion device callback says happened, whichever Android version sent it. */
internal enum class CompanionSignal(val what: String, val trigger: TripTrigger) {
    /**
     * "Appeared". It starts a trip that must be confirmed within 15 seconds (ADR-002): on
     * Android 13 to 15 the callback also fires when the truck is only seen nearby.
     */
    APPEARED("device appeared", TripTrigger.TRUCK_APPEARED),

    /** "Disappeared". A disconnect is safe to trust: the grace period reads the truck again. */
    DISAPPEARED("device disappeared", TripTrigger.TRUCK_DISCONNECTED),
}

/**
 * Reads the event of Android 16's presence callback. Only the two Bluetooth connection events
 * count. The others (a low-energy sighting, the kinds for devices an app manages itself) say
 * nothing about the truck's hands-free connection.
 */
internal fun companionSignal(presenceEvent: Int): CompanionSignal? = when (presenceEvent) {
    DevicePresenceEvent.EVENT_BT_CONNECTED -> CompanionSignal.APPEARED
    DevicePresenceEvent.EVENT_BT_DISCONNECTED -> CompanionSignal.DISAPPEARED
    else -> null
}

/**
 * Decides what a companion device callback becomes. Android binds the companion service only
 * for devices MilO itself is associated with, and that is normally the truck alone.
 *
 * @param address the address the callback named, or null (Android 16 names only the id).
 * @param associationId the association the callback named, or null (Android 12 names only the
 * address).
 */
internal fun decideCompanion(
    signal: CompanionSignal,
    address: String?,
    associationId: Int?,
    lookup: TruckLookup,
): SignalDecision {
    val named = listOfNotNull(address, associationId?.let { "association $it" }).joinToString()
    val what = "companion service: ${signal.what} ($named)"
    val truck = lookup.truck
    return when {
        // The truck cannot be looked up, but the callback is about one of MilO's own
        // associations, so it is acted on. A start it causes still has to be confirmed.
        lookup.problem != null ->
            SignalDecision.Fire(signal.trigger, "$what, and ${lookup.problem}")

        truck == null -> SignalDecision.Ignore("$what. No truck is paired. Ignored")

        truck.isDevice(address, associationId) -> SignalDecision.Fire(signal.trigger, what)

        else -> SignalDecision.Ignore("$what. That device is not the truck. Ignored")
    }
}
