package com.shawnkowalchuk.milo.platform.bluetooth

import android.bluetooth.BluetoothAdapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which changes of the phone's Bluetooth switch make the pairing screen read the paired devices
 * again. The broadcast itself can only be tested on the phone.
 */
class BluetoothSwitchTest {
    @Test
    fun `Bluetooth having finished switching on or off is worth a new reading`() {
        assertTrue(isBluetoothSwitchSettled(BluetoothAdapter.STATE_ON))
        assertTrue(isBluetoothSwitchSettled(BluetoothAdapter.STATE_OFF))
    }

    @Test
    fun `Bluetooth still switching is not - Android lists no paired device yet`() {
        assertFalse(isBluetoothSwitchSettled(BluetoothAdapter.STATE_TURNING_ON))
        assertFalse(isBluetoothSwitchSettled(BluetoothAdapter.STATE_TURNING_OFF))
        // What the broadcast is read as when it carries no state at all.
        assertFalse(isBluetoothSwitchSettled(BluetoothAdapter.ERROR))
    }
}
