package com.shawnkowalchuk.milo.platform.bluetooth

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.companion.DevicePresenceEvent
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val EARBUDS_ADDRESS = "11:22:33:44:55:66"
private const val TRUCK_ASSOCIATION = 7

private val TRUCK = Truck(TRUCK_ADDRESS, "Work truck", TRUCK_ASSOCIATION)
private val PAIRED = TruckLookup(TRUCK)
private const val VAN_ASSOCIATION = 8
private val TWO_VEHICLES = TruckLookup(listOf(TRUCK, Truck(VAN_ADDRESS, "Van", VAN_ASSOCIATION)))
private val NOT_PAIRED = TruckLookup(truck = null)
private val LOOKUP_FAILED = TruckLookup(truck = null, problem = "the settings cannot be read")

private val ACL_CONNECTED = BluetoothSignal("ACL connected", TripTrigger.TRUCK_LINK_CONNECTED)

/**
 * What each Bluetooth broadcast and each companion callback becomes. These decisions are what
 * stands between "the phone connected to something" and "a trip starts", and Android's own part
 * (delivering the broadcast, binding the service) can only be tested on the phone.
 */
class TruckSignalsTest {
    // ---- Reading a broadcast --------------------------------------------------------------------

    @Test
    fun `the link going up is a link connect, and going down a disconnect`() {
        val up = bluetoothSignal(BluetoothDevice.ACTION_ACL_CONNECTED, null, null)
        val down = bluetoothSignal(BluetoothDevice.ACTION_ACL_DISCONNECTED, null, null)

        assertEquals(TripTrigger.TRUCK_LINK_CONNECTED, up?.trigger)
        assertEquals(TripTrigger.TRUCK_DISCONNECTED, down?.trigger)
    }

    @Test
    fun `a link broadcast that names no transport is taken for the classic one`() {
        val classic = BluetoothDevice.TRANSPORT_BREDR

        assertEquals(
            bluetoothSignal(BluetoothDevice.ACTION_ACL_CONNECTED, null, transport = classic),
            bluetoothSignal(BluetoothDevice.ACTION_ACL_CONNECTED, null, transport = null),
        )
    }

    @Test
    fun `a low-energy link only prompts a reading of the truck`() {
        // It says nothing certain about the hands-free connection, in either direction. As a
        // disconnect it would start the grace period of a trip whose truck is still connected.
        val lowEnergy = BluetoothDevice.TRANSPORT_LE

        val up = bluetoothSignal(BluetoothDevice.ACTION_ACL_CONNECTED, null, lowEnergy)
        val down = bluetoothSignal(BluetoothDevice.ACTION_ACL_DISCONNECTED, null, lowEnergy)

        assertEquals(TripTrigger.RECONCILE, up?.trigger)
        assertEquals(TripTrigger.RECONCILE, down?.trigger)
    }

    @Test
    fun `a profile connecting or disconnecting prompts a reading of the truck`() {
        // Not a link connect: a profile can reconnect on a link that never dropped, and that
        // must not release the hold-off. Not a disconnect: the other profile may still be up.
        val actions =
            listOf(
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            )
        val states = listOf(BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_DISCONNECTED)

        for (action in actions) {
            for (state in states) {
                assertEquals(TripTrigger.RECONCILE, bluetoothSignal(action, state, null)?.trigger)
            }
        }
    }

    @Test
    fun `a profile that is only on its way to a state changes nothing yet`() {
        val headset = BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED

        assertNull(bluetoothSignal(headset, BluetoothProfile.STATE_CONNECTING, null))
        assertNull(bluetoothSignal(headset, BluetoothProfile.STATE_DISCONNECTING, null))
        assertNull(bluetoothSignal(headset, profileState = null, transport = null))
    }

    @Test
    fun `an intent with any other action is dropped`() {
        // The receiver is exported, so another app can aim an intent of its own at it.
        assertNull(bluetoothSignal("com.example.FAKE_TRUCK_CONNECTED", null, null))
        assertNull(bluetoothSignal(action = null, profileState = null, transport = null))
    }

    @Test
    fun `the receiver in the trip service listens for the same four broadcasts`() {
        assertTrue(TRUCK_BROADCAST_ACTIONS.all { bluetoothSignal(it, STATE_FOR_ANY, null) != null })
        assertEquals(4, TRUCK_BROADCAST_ACTIONS.size)
    }

    // ---- Whose broadcast it is ------------------------------------------------------------------

    @Test
    fun `a broadcast for the truck's address fires its trigger and names the truck`() {
        val decision = decideBroadcast("Bluetooth receiver", ACL_CONNECTED, TRUCK_ADDRESS, PAIRED)

        assertEquals(
            SignalDecision.Fire(
                TripTrigger.TRUCK_LINK_CONNECTED,
                "Bluetooth receiver: ACL connected for the truck (AA:BB:CC:DD:EE:FF)",
                TRUCK_ADDRESS,
            ),
            decision,
        )
    }

    @Test
    fun `the truck's address is matched whatever its case`() {
        // Android's Bluetooth classes write addresses in capitals, its companion classes in
        // small letters. A plain comparison would never match the two.
        val decision = decideBroadcast("via", ACL_CONNECTED, "aa:bb:cc:dd:ee:ff", PAIRED)

        assertTrue(decision is SignalDecision.Fire)
    }

    @Test
    fun `a broadcast for another device is ignored, and the log line says whose it was`() {
        val decision = decideBroadcast("Bluetooth receiver", ACL_CONNECTED, EARBUDS_ADDRESS, PAIRED)

        assertEquals(
            SignalDecision.Ignore(
                "Bluetooth receiver: ACL connected for another device (11:22:33:44:55:66). Ignored",
            ),
            decision,
        )
    }

    @Test
    fun `a broadcast that names no device is not the truck's`() {
        val decision = decideBroadcast("via", ACL_CONNECTED, address = null, PAIRED)

        assertTrue(decision is SignalDecision.Ignore)
    }

    @Test
    fun `before a truck is paired every broadcast is ignored`() {
        val decision = decideBroadcast("via", ACL_CONNECTED, TRUCK_ADDRESS, NOT_PAIRED)

        assertEquals(
            SignalDecision.Ignore(
                "via: ACL connected for AA:BB:CC:DD:EE:FF. No truck is paired. Ignored",
            ),
            decision,
        )
    }

    @Test
    fun `when the truck cannot be looked up a broadcast becomes a reading, not a start`() {
        // It may be the truck's. Dropping it could miss a trip; trusting it would start a trip
        // for a pair of earbuds. So the truck's connection is read, and that decides.
        val decision = decideBroadcast("via", ACL_CONNECTED, EARBUDS_ADDRESS, LOOKUP_FAILED)

        assertEquals(TripTrigger.RECONCILE, (decision as SignalDecision.Fire).trigger)
        assertTrue(decision.source.contains("the settings cannot be read"))
    }

    // ---- The companion service ------------------------------------------------------------------

    @Test
    fun `of Android 16's presence events only the two Bluetooth connection events count`() {
        assertEquals(
            CompanionSignal.APPEARED,
            companionSignal(DevicePresenceEvent.EVENT_BT_CONNECTED),
        )
        assertEquals(
            CompanionSignal.DISAPPEARED,
            companionSignal(DevicePresenceEvent.EVENT_BT_DISCONNECTED),
        )
        // A low-energy sighting says the truck is near, not that it is connected.
        assertNull(companionSignal(DevicePresenceEvent.EVENT_BLE_APPEARED))
        assertNull(companionSignal(DevicePresenceEvent.EVENT_BLE_DISAPPEARED))
        assertNull(companionSignal(DevicePresenceEvent.EVENT_ASSOCIATION_REMOVED))
    }

    @Test
    fun `appeared starts a trip that must be confirmed, disappeared is a disconnect`() {
        assertEquals(TripTrigger.TRUCK_APPEARED, CompanionSignal.APPEARED.trigger)
        assertEquals(TripTrigger.TRUCK_DISCONNECTED, CompanionSignal.DISAPPEARED.trigger)
    }

    @Test
    fun `a callback of Android 14 and 15 names both, and the address alone is enough`() {
        // The number Android gives the association can be newer than the one MilO stored.
        val byAddress = decideCompanion(CompanionSignal.APPEARED, "aa:bb:cc:dd:ee:ff", 99, PAIRED)

        assertEquals(
            SignalDecision.Fire(
                TripTrigger.TRUCK_APPEARED,
                "companion service: device appeared (aa:bb:cc:dd:ee:ff, association 99)",
                TRUCK_ADDRESS,
            ),
            byAddress,
        )
    }

    @Test
    fun `a callback is matched by association id on Android 16, which names nothing else`() {
        val byId = decideCompanion(CompanionSignal.DISAPPEARED, null, TRUCK_ASSOCIATION, PAIRED)

        assertEquals(
            SignalDecision.Fire(
                TripTrigger.TRUCK_DISCONNECTED,
                "companion service: device disappeared (association 7)",
                TRUCK_ADDRESS,
            ),
            byId,
        )
    }

    @Test
    fun `a broadcast for a second vehicle fires its trigger, names it and says which it is`() {
        val decision =
            decideBroadcast("Bluetooth receiver", ACL_CONNECTED, VAN_ADDRESS, TWO_VEHICLES)

        assertEquals(
            SignalDecision.Fire(
                TripTrigger.TRUCK_LINK_CONNECTED,
                "Bluetooth receiver: ACL connected for the vehicle Van ($VAN_ADDRESS)",
                VAN_ADDRESS,
            ),
            decision,
        )
    }

    @Test
    fun `among several vehicles the first is named by its name too, and earbuds are ignored`() {
        val truck = decideBroadcast("via", ACL_CONNECTED, TRUCK_ADDRESS, TWO_VEHICLES)
        val earbuds = decideBroadcast("via", ACL_CONNECTED, EARBUDS_ADDRESS, TWO_VEHICLES)

        assertEquals(TRUCK_ADDRESS, (truck as SignalDecision.Fire).vehicle)
        assertTrue(truck.source.contains("the vehicle Work truck"))
        assertTrue(earbuds is SignalDecision.Ignore)
    }

    @Test
    fun `a companion callback for a second vehicle is matched by its association id`() {
        val decision =
            decideCompanion(CompanionSignal.APPEARED, null, VAN_ASSOCIATION, TWO_VEHICLES)

        assertEquals(VAN_ADDRESS, (decision as SignalDecision.Fire).vehicle)
    }

    @Test
    fun `a callback for another association is ignored`() {
        // Left over from an earlier truck, or made by hand with adb.
        val other = decideCompanion(CompanionSignal.APPEARED, EARBUDS_ADDRESS, 99, PAIRED)

        assertTrue(other is SignalDecision.Ignore)
    }

    @Test
    fun `a callback before a truck is stored is ignored`() {
        val decision = decideCompanion(CompanionSignal.APPEARED, TRUCK_ADDRESS, 7, NOT_PAIRED)

        assertTrue(decision is SignalDecision.Ignore)
    }

    @Test
    fun `when the truck cannot be looked up a companion callback is still acted on`() {
        // Android binds the service only for MilO's own associations, and a start it causes
        // still has to be confirmed within 15 seconds.
        val decision = decideCompanion(CompanionSignal.APPEARED, TRUCK_ADDRESS, 7, LOOKUP_FAILED)

        assertEquals(TripTrigger.TRUCK_APPEARED, (decision as SignalDecision.Fire).trigger)
    }

    private companion object {
        /** A profile state that makes a profile broadcast count. The link ignores it. */
        const val STATE_FOR_ANY = BluetoothProfile.STATE_CONNECTED
    }
}
