package com.shawnkowalchuk.milo.platform.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Tells the alarm's PendingIntent from any other MilO hands to Android. */
private const val DAILY_LOOK_REQUEST = 0

/**
 * The alarm that has MilO look at the reminder once a day. An interface, so that the reminder
 * is tested without Android.
 */
interface ReminderAlarm {
    /**
     * Asks to be woken at about [atMs], wall-clock milliseconds. An earlier request is replaced:
     * there is never more than one.
     */
    fun setFor(atMs: Long)

    /**
     * Asks to be woken about [delayMs] from now, counted on the clock that runs from the
     * phone's boot, which setting the phone's date does not move. For the time MilO's clock
     * and the phone's disagree (ADR-006): Android judges [setFor] by the phone's clock. An
     * earlier request of either kind is replaced, and a later one of either kind replaces this.
     */
    fun setAfter(delayMs: Long)
}

/**
 * Android's alarm service, asked for an **inexact** alarm: one that Android may deliver up to
 * about an hour late, together with other apps' work, and that needs no permission. An exact
 * alarm needs "Alarms and reminders", which Android 14 no longer grants by itself, and a
 * reminder to send a report gains nothing from the minute.
 *
 * `RTC`, not `RTC_WAKEUP`: a phone that is asleep is not woken for this. The alarm is then
 * delivered when the phone next wakes, which is when Shawn picks it up. [setAfter] is its twin
 * on the clock that runs from boot, `ELAPSED_REALTIME`: inexact as well, no wake, no
 * permission.
 *
 * Android forgets every alarm at a reboot, and when an app is force-stopped. Both end in a new
 * process, and the reminder asks again at every process start.
 *
 * The alarm is delivered to [ReminderReceiver], named by class, so it can start MilO's process
 * when it is not running. Whether HyperOS lets it do that with Autostart off is one of the
 * things only the phone can show.
 */
class AlarmManagerReminderAlarm(context: Context) : ReminderAlarm {
    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)

    override fun setFor(atMs: Long) {
        alarms.set(AlarmManager.RTC, atMs, dailyLook())
    }

    override fun setAfter(delayMs: Long) {
        val atElapsedMs = SystemClock.elapsedRealtime() + delayMs
        alarms.set(AlarmManager.ELAPSED_REALTIME, atElapsedMs, dailyLook())
    }

    /**
     * The same request every time, so that setting the alarm again replaces the one before,
     * whichever of the two clocks either was asked on: Android tells two alarms apart by this
     * and not by their time.
     */
    private fun dailyLook(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        DAILY_LOOK_REQUEST,
        Intent(appContext, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
