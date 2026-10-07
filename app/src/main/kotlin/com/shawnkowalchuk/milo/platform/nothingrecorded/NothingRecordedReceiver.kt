package com.shawnkowalchuk.milo.platform.nothingrecorded

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shawnkowalchuk.milo.app.MiloApplication
import com.shawnkowalchuk.milo.platform.bluetooth.holdUntilHandled

/**
 * Where the daily alarm of the "nothing recorded" check arrives
 * ([AlarmManagerNothingRecordedAlarm]). Declared in the manifest, so that the alarm can start
 * MilO's process when it is not running.
 *
 * It only hands the moment to [NothingRecordedCheck], which asks whether a trip has been
 * recorded today and asks for the next alarm. It does not call the trip controller and cannot
 * start a trip.
 *
 * Not exported, and without an intent filter: the alarm is a `PendingIntent` MilO made itself
 * and aimed at this class by name, so nothing outside MilO can reach it.
 */
class NothingRecordedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val check = (context.applicationContext as MiloApplication).container.checks.nothingRecorded
        // Held open until the look is done: it reads storage a moment after onReceive has
        // returned, and Android may freeze a process whose broadcast is over.
        check.onAlarm(holdUntilHandled())
    }
}
