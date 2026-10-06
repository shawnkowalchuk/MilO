package com.shawnkowalchuk.milo.platform.bluetooth

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager

/** Why the phone's list of paired devices could not be read. */
enum class PairedDevicesProblem {
    /** The Bluetooth permission ("Nearby devices") is not granted. */
    PERMISSION_MISSING,

    /** This phone has no Bluetooth at all. */
    NO_BLUETOOTH,

    /** Bluetooth is switched off. Android lists no paired devices while it is. */
    BLUETOOTH_OFF,
}

/**
 * The phone's paired Bluetooth devices, for the pairing screen to list.
 *
 * @param problem why [devices] is empty, or null if the list was read. A list that was read can
 * still be empty: nothing is paired with the phone yet.
 */
data class PairedDeviceList(val devices: List<PairedDevice>, val problem: PairedDevicesProblem?)

/**
 * Reads the list of devices paired with the phone in its Bluetooth settings. The truck has to be
 * paired there first: MilO never scans for it (ADR-002), which is why it does not ask for the
 * scan permission.
 */
internal fun listPairedDevices(context: Context): PairedDeviceList {
    val permission = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
    if (permission != PackageManager.PERMISSION_GRANTED) {
        return PairedDeviceList(emptyList(), PairedDevicesProblem.PERMISSION_MISSING)
    }
    val adapter =
        context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return PairedDeviceList(emptyList(), PairedDevicesProblem.NO_BLUETOOTH)
    return try {
        if (!adapter.isEnabled) {
            return PairedDeviceList(emptyList(), PairedDevicesProblem.BLUETOOTH_OFF)
        }
        val devices = adapter.bondedDevices.map { PairedDevice(it.address, it.name) }
        // Named devices first, by name: the truck is found by its name, not by its address.
        val byName = compareBy<PairedDevice>({ it.name == null }, { it.name }, { it.address })
        PairedDeviceList(devices.sortedWith(byName), problem = null)
    } catch (denied: SecurityException) {
        // The permission was there a moment ago: it was taken away in between.
        PairedDeviceList(emptyList(), PairedDevicesProblem.PERMISSION_MISSING)
    }
}
