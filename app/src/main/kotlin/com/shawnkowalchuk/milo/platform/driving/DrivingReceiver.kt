package com.shawnkowalchuk.milo.platform.driving

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.android.gms.location.ActivityTransitionResult
import com.shawnkowalchuk.milo.app.MiloApplication
import com.shawnkowalchuk.milo.platform.bluetooth.holdUntilHandled

/**
 * Where the phone's driving detection reports to (see [PlayServicesDrivingDetection]). Declared
 * in the manifest, so that a report can start MilO's process when it is not running. Whether
 * HyperOS lets it do that with Autostart off is one of the things the phone has to show
 * (docs/DEVICE_TEST_CHECKLIST.md, the DA checks).
 *
 * It hands the report to [DrivingAlert], which may post a notification, and a fresh report of
 * getting into a vehicle to the trip controller, which reads GPS again beside a parked truck
 * ([enteredVehicleAtMs]). What an event of the report means is read by [vehicleReport], a pure
 * function with tests of its own. Neither can start a trip on a report alone.
 *
 * Not exported. The report is sent through a `PendingIntent` MilO made itself, which Android
 * delivers as coming from MilO, so nothing outside MilO needs to reach this receiver and
 * nothing outside can.
 */
class DrivingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // An intent without a report in it is not from the driving detection.
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val nowNanos = SystemClock.elapsedRealtimeNanos()
        val reports =
            result.transitionEvents.mapNotNull {
                vehicleReport(it.activityType, it.transitionType, it.elapsedRealTimeNanos, nowNanos)
            }
        val container = (context.applicationContext as MiloApplication).container
        // First, so that the controller has it before the alert waits for it to catch up.
        enteredVehicleAtMs(reports, container.clock())?.let { atMs ->
            container.tripController.onVehicleEntered(atMs)
        }
        // Held open until the alert has been decided: the decision reads the truck's
        // connection first, a moment after onReceive has returned (see holdUntilHandled).
        container.drivingAlert.onReports(reports, holdUntilHandled())
    }
}
