package com.shawnkowalchuk.milo.platform.nothingrecorded

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Tells the alarm's PendingIntent from any other MilO hands to Android. The monthly reminder's
 * alarm has the same number and another receiver, and Android tells the two apart by that.
 */
private const val DAILY_LOOK_REQUEST = 0

/**
 * The alarm that has MilO ask once a day whether a trip has been recorded. An interface, so
 * that the check is tested without Android.
 */
interface NothingRecordedAlarm {
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

    /** Takes the request back: the check is switched off. Safe to call when none was made. */
    fun cancel()
}

/**
 * Android's alarm service, asked for an **inexact** alarm, like the monthly reminder's
 * (`platform/reminder/ReminderAlarm.kt`): one that Android may deliver late, together with
 * other apps' work, and that needs no permission. MilO declares no exact-alarm permission.
 *
 * `RTC_WAKEUP`, where the reminder has `RTC`. The reminder can wait until Shawn picks the
 * phone up. This notification is worth most while the work day is still going on: a phone that
 * lies asleep in the truck at noon is woken for the moment the look takes, so that the
 * notification is there when he next glances at it, and not hours later. It is one wake a day.
 * [setAfter] is its twin on the clock that runs from boot, `ELAPSED_REALTIME_WAKEUP`: inexact
 * as well, the same one wake, no permission.
 *
 * Android forgets every alarm at a reboot, and when an app is force-stopped. Both end in a new
 * process, and the check asks again at every process start.
 *
 * The alarm is delivered to [NothingRecordedReceiver], named by class, so it can start MilO's
 * process when it is not running. Whether HyperOS lets it do that with Autostart off is one of
 * the things only the phone can show.
 */
class AlarmManagerNothingRecordedAlarm(context: Context) : NothingRecordedAlarm {
    private val appContext = context.applicationContext
    private val alarms = appContext.getSystemService(AlarmManager::class.java)

    override fun setFor(atMs: Long) {
        alarms.set(AlarmManager.RTC_WAKEUP, atMs, dailyLook())
    }

    override fun setAfter(delayMs: Long) {
        val atElapsedMs = SystemClock.elapsedRealtime() + delayMs
        alarms.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsedMs, dailyLook())
    }

    override fun cancel() {
        alarms.cancel(dailyLook())
    }

    /**
     * The same request every time, so that setting the alarm again replaces the one before,
     * whichever of the two clocks either was asked on, and cancelling finds it: Android tells
     * two alarms apart by this and not by their time.
     */
    private fun dailyLook(): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        DAILY_LOOK_REQUEST,
        Intent(appContext, NothingRecordedReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
