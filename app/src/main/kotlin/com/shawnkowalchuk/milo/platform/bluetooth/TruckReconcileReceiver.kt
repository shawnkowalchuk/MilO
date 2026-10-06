package com.shawnkowalchuk.milo.platform.bluetooth

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shawnkowalchuk.milo.app.MiloApplication
import com.shawnkowalchuk.milo.platform.trip.TripTrigger

/**
 * ADR-002's reconcile for the two moments at which no Bluetooth event can arrive: the phone has
 * booted, or MilO was updated, with the truck already connected. The connect broadcast was sent
 * before MilO could hear it, and nothing sends it again. So each of these asks the controller
 * to read the truck's connection and to look at the trip the last process left open.
 *
 * It lives beside the Bluetooth receiver because it stands in for the Bluetooth event that was
 * missed. It is not exported: both broadcasts come from Android's core.
 *
 * After a reboot Android sends nothing until the phone has been unlocked once, so a trip that
 * began before that starts recording at the unlock (ADR-002, "Open, and assumed for now").
 */
class TruckReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val source =
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED -> bootSource()
                Intent.ACTION_MY_PACKAGE_REPLACED -> "MilO was updated"
                else -> return
            }
        val controller = (context.applicationContext as MiloApplication).container.tripController
        controller.onTrigger(TripTrigger.RECONCILE, source)
        // Held open until the controller has read the truck: see TruckBluetoothReceiver.
        controller.whenCaughtUp(holdUntilHandled())
    }

    /**
     * From Android 15 the boot broadcast is also sent when an app that was force-stopped is
     * started again, so there it cannot be told from a boot.
     */
    private fun bootSource(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            "phone booted, or MilO started again after a force stop"
        } else {
            "phone booted"
        }
}
