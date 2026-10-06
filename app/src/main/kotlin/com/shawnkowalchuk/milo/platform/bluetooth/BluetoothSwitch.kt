package com.shawnkowalchuk.milo.platform.bluetooth

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Whether [adapterState] is one of the two states in which the phone's Bluetooth has finished
 * switching: on, or off. The two states in between ("turning on", "turning off") are not worth
 * a new look at the paired devices, because Android still lists none.
 */
internal fun isBluetoothSwitchSettled(adapterState: Int): Boolean =
    adapterState == BluetoothAdapter.STATE_ON || adapterState == BluetoothAdapter.STATE_OFF

/**
 * Says each time the phone's Bluetooth has finished switching on or off, for as long as the
 * flow is collected.
 *
 * The pairing screen needs it. Switching Bluetooth on takes the phone a second or two, and
 * Shawn is back on the screen sooner than that: a list read when he returns still says
 * "Bluetooth is switched off", and nothing else would ever read it again.
 *
 * The receiver is exported for the same reason as [TruckBluetoothReceiver]: it must not depend
 * on which part of Android sends the broadcast. That is safe, because the action is a protected
 * broadcast, which no other app may send, and a receiver registered in code cannot be addressed
 * in any other way.
 */
fun bluetoothSwitchChanges(context: Context): Flow<Unit> = callbackFlow {
    val appContext = context.applicationContext
    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                val state =
                    intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                // A full channel means a reading is already on its way; dropping one is fine.
                if (isBluetoothSwitchSettled(state)) trySend(Unit)
            }
        }
    val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
    } else {
        // Android 12 has no such flag: every receiver registered in code is exported.
        appContext.registerReceiver(receiver, filter)
    }
    awaitClose { appContext.unregisterReceiver(receiver) }
}
