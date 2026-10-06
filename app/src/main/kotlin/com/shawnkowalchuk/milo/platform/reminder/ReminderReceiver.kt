package com.shawnkowalchuk.milo.platform.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shawnkowalchuk.milo.app.MiloApplication
import com.shawnkowalchuk.milo.platform.bluetooth.holdUntilHandled

/**
 * Where the daily alarm of the monthly reminder arrives ([AlarmManagerReminderAlarm]). Declared
 * in the manifest, so that the alarm can start MilO's process when it is not running.
 *
 * It only hands the moment to [MonthlyReminder], which looks at whether a reminder is due and
 * asks for tomorrow's alarm. It does not call the trip controller and cannot start a trip.
 *
 * Not exported, and without an intent filter: the alarm is a `PendingIntent` MilO made itself
 * and aimed at this class by name, so nothing outside MilO can reach it.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminder = (context.applicationContext as MiloApplication).container.reports.reminder
        // Held open until the look is done: it reads storage a moment after onReceive has
        // returned, and Android may freeze a process whose broadcast is over.
        reminder.onAlarm(holdUntilHandled())
    }
}
