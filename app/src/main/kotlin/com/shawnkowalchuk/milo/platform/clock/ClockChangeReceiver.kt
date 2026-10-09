package com.shawnkowalchuk.milo.platform.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shawnkowalchuk.milo.app.MiloApplication
import com.shawnkowalchuk.milo.platform.bluetooth.holdUntilHandled

/**
 * Hears Android say that the phone's clock was set: by hand in the phone's settings, or by the
 * network. Declared in the manifest, and `TIME_SET` is one of the few broadcasts Android still
 * delivers to an app that is not running, so it can start MilO's process.
 *
 * It only has MilO's clock look ([ClockWatch.onPhoneClockSet]). What that leads to is the one
 * line in the event log for a change MilO did not follow, and the two daily alarms being asked
 * for again once the clocks agree. It does not call the trip controller and cannot start,
 * end or change a trip.
 *
 * Not exported: the broadcast comes from Android's core, and no app may send it.
 */
class ClockChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIME_CHANGED) return
        val watch = (context.applicationContext as MiloApplication).container.clocks.watch
        // Held open until the look and what follows it are done: both happen a moment after
        // onReceive has returned, and Android may freeze a process whose broadcast is over.
        watch.onPhoneClockSet(holdUntilHandled())
    }
}
