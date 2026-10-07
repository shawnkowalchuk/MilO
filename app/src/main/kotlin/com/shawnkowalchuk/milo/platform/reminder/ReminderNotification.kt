package com.shawnkowalchuk.milo.platform.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.util.formatMonthAndYear
import java.time.DateTimeException
import java.time.YearMonth

// The ids below are stored by Android: the channel's with Shawn's choices for it, the
// notification's while it is showing. The trip notifications use 1 to 3 (TripNotifications.kt).
private const val REMINDER_NOTIFICATION_ID = 4
private const val REMINDER_CHANNEL_ID = "monthly_reminder"

/** Tells this notification's tap from the trip notification's, which opens the same activity. */
private const val OPEN_REPORT_REQUEST = 1

/**
 * What the tap asks of MilO's activity. An action of MilO's own: Android tells two requests to
 * the same activity apart by it, and the activity knows by it that the two numbers are there.
 */
private const val ACTION_OPEN_REPORT = "com.shawnkowalchuk.milo.action.OPEN_REPORT"
private const val EXTRA_YEAR = "com.shawnkowalchuk.milo.extra.REPORT_YEAR"
private const val EXTRA_MONTH = "com.shawnkowalchuk.milo.extra.REPORT_MONTH"

/**
 * The reminder's notification: "Mileage report for September 2026. It has not been sent to
 * your accountant yet. Tap to open it." On a channel of its own, so that Shawn can silence it
 * in the phone's settings without touching the trip notifications.
 *
 * Default importance: it makes the phone's notification sound and does not pop up over what he
 * is doing. Nothing about it is urgent, and it comes back tomorrow.
 *
 * A tap opens MilO on the Report screen for that month ([reportMonthToOpen]).
 *
 * @param opens the activity a tap opens: MilO's one activity. Handed in by the container, so
 * that this package does not reach into `app/`.
 */
class ReminderNotification(private val context: Context, private val opens: Class<*>) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        // Creating a channel that already exists changes nothing, so this is safe at every start.
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDER_CHANNEL_ID,
                context.getString(R.string.notification_channel_reminder),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    /**
     * Posts the reminder for [month]. Posted again while one is showing, it replaces that one
     * and makes no second sound.
     *
     * @return false if nobody will see it: notifications are switched off for MilO, or this
     * kind of notification is switched off in the phone's settings.
     */
    fun show(month: YearMonth): Boolean {
        val locale = context.resources.configuration.locales[0]
        val monthName = formatMonthAndYear(month, locale)
        val notification =
            Notification
                .Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_trip)
                .setColor(context.getColor(R.color.milo_notification_accent))
                .setContentTitle(context.getString(R.string.notification_reminder_title, monthName))
                .setContentText(context.getString(R.string.notification_reminder_text))
                .setContentIntent(openReport(month))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build()
        manager.notify(REMINDER_NOTIFICATION_ID, notification)
        val channel = manager.getNotificationChannel(REMINDER_CHANNEL_ID)
        val channelOn = channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
        return manager.areNotificationsEnabled() && channelOn
    }

    /** Takes the reminder away. Safe to call when none is showing. */
    fun cancel() {
        manager.cancel(REMINDER_NOTIFICATION_ID)
    }

    /**
     * A tap brings MilO's one activity to the front and hands it the month. If the activity is
     * already there it is told (`onNewIntent`) and not built a second time, and whatever another
     * app had put on top of it inside MilO's window (an email draft, a PDF) is closed.
     */
    private fun openReport(month: YearMonth): PendingIntent = PendingIntent.getActivity(
        context,
        OPEN_REPORT_REQUEST,
        Intent(context, opens)
            .setAction(ACTION_OPEN_REPORT)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_YEAR, month.year)
            .putExtra(EXTRA_MONTH, month.monthValue),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * The month whose Report screen [intent] asks for, or null if it asks for none: it is not a tap
 * on the reminder, or its two numbers make no month.
 *
 * Null as well for an activity that Android starts again from the list of recent apps. Android
 * hands such a start the request the window was first opened with, and the reminder that was
 * tapped then was dealt with then.
 */
fun reportMonthToOpen(intent: Intent?): YearMonth? {
    if (intent?.action != ACTION_OPEN_REPORT) return null
    if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
    return try {
        YearMonth.of(intent.getIntExtra(EXTRA_YEAR, 0), intent.getIntExtra(EXTRA_MONTH, 0))
    } catch (noSuchMonth: DateTimeException) {
        null
    }
}
