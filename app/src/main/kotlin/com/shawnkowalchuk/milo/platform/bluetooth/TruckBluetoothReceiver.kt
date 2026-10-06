package com.shawnkowalchuk.milo.platform.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.shawnkowalchuk.milo.app.MiloApplication
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The longest a broadcast is kept open while the trip controller deals with it. Android gives a
 * receiver about ten seconds before it counts the app as not responding.
 */
private const val HOLD_LIMIT_MS = 8_000L

/**
 * Hears the truck connect and disconnect (ADR-002, the Bluetooth receiver): the link itself
 * (ACL), and the hands-free and audio profiles. It works out whether the broadcast is about the
 * truck and calls the trip controller before `onReceive` returns.
 *
 * It is used twice. Declared in the manifest, it is what starts MilO's process when the truck
 * connects while MilO is not running. Registered by the trip service for the length of a trip
 * ([registerIn]), it hears the same broadcasts without depending on Android (or HyperOS)
 * delivering to a manifest receiver. During a trip both hear every broadcast, so each one
 * reaches the controller twice; the trip rules are built for that, and the event log shows
 * which of the two paths delivered.
 *
 * **Why it is exported, both in the manifest and when registered.** These broadcasts are sent
 * by the Bluetooth app, which is an ordinary system app with its own user id, not Android's
 * core. Android delivers to a receiver that is not exported only from the core or from MilO
 * itself, so a receiver that is not exported never hears a disconnect and the trip never ends.
 *
 * **Why that is safe.** All four actions are protected broadcasts: Android refuses to let any
 * other app send them. Another app can still aim an intent of its own at an exported receiver,
 * so anything that is not one of the four actions is dropped unread.
 */
class TruckBluetoothReceiver : BroadcastReceiver() {
    /** True only on the instance the trip service registers for itself. */
    private var inService = false

    // EXTRA_TRANSPORT is new in Android 13. Its name is compiled in, so asking for it is safe
    // on Android 12, where the broadcast simply does not carry it.
    @SuppressLint("InlinedApi")
    override fun onReceive(context: Context, intent: Intent) {
        val signal =
            bluetoothSignal(
                action = intent.action,
                profileState = intent.intOrNull(BluetoothProfile.EXTRA_STATE),
                transport = intent.intOrNull(BluetoothDevice.EXTRA_TRANSPORT),
            ) ?: return
        val container = (context.applicationContext as MiloApplication).container
        val via = if (inService) "Bluetooth receiver (trip service)" else "Bluetooth receiver"
        val lookup = container.pairedTruck.now()
        val controller = container.tripController
        when (val decision = decideBroadcast(via, signal, intent.deviceAddress(), lookup)) {
            is SignalDecision.Ignore -> controller.note(EventCategory.TRIGGER, decision.why)
            is SignalDecision.Fire -> controller.onTrigger(decision.trigger, decision.source)
        }
        // In the trip service there is nothing to protect: the service keeps the process alive.
        if (!inService) controller.whenCaughtUp(holdUntilHandled())
    }

    /** Registers this receiver in [context] for as long as a trip is recorded. */
    fun registerIn(context: Context) {
        inService = true
        val filter = IntentFilter().apply { TRUCK_BROADCAST_ACTIONS.forEach(::addAction) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(this, filter, Context.RECEIVER_EXPORTED)
        } else {
            // Android 12 has no such flag: every receiver registered in code is exported.
            context.registerReceiver(this, filter)
        }
    }

    fun unregisterFrom(context: Context) {
        context.unregisterReceiver(this)
    }

    private fun Intent.intOrNull(name: String): Int? =
        if (hasExtra(name)) getIntExtra(name, 0) else null

    private fun Intent.deviceAddress(): String? {
        val device =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                // The typed call arrived with Android 13, where it can throw from inside
                // Android (AndroidX uses it from Android 14 for that reason). Older versions
                // have only this one.
                @Suppress("DEPRECATION")
                getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
        return device?.address
    }
}

/**
 * Keeps a manifest broadcast open until the returned function is called, or until
 * [HOLD_LIMIT_MS] has passed, whichever comes first.
 *
 * A broadcast that started the process must stay open until the controller has dealt with it.
 * Once `onReceive` returns, Android (and HyperOS more eagerly) may freeze or kill a process that
 * has nothing else running, and a trigger that needs a reading of the truck first is handled a
 * moment later, on the controller's own thread. Android must be told exactly once that the
 * broadcast is finished, however many times the function is called.
 */
internal fun BroadcastReceiver.holdUntilHandled(): () -> Unit {
    val pending = goAsync()
    val finished = AtomicBoolean(false)
    val finish = { if (finished.compareAndSet(false, true)) pending.finish() }
    Handler(Looper.getMainLooper()).postDelayed(finish, HOLD_LIMIT_MS)
    return finish
}
