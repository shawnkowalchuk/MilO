package com.shawnkowalchuk.milo.platform.nothingrecorded

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.shawnkowalchuk.milo.R

// The ids below are stored by Android: the channel's with Shawn's choices for it, the
// notification's while it is showing. The trip notifications use 1 to 3 (TripNotifications.kt)
// and the monthly reminder 4 (ReminderNotification.kt).
private const val NOTHING_RECORDED_NOTIFICATION_ID = 5
private const val NOTHING_RECORDED_CHANNEL_ID = "nothing_recorded"

/**
 * Tells this notification's tap from the other two that open the same activity: the trip
 * notification's is 0 and the monthly reminder's is 1.
 */
private const val OPEN_HOME_REQUEST = 2

/**
 * What the tap asks of MilO's activity. An action of MilO's own: Android tells two requests to
 * the same activity apart by it, and the activity knows by it that Home is wanted.
 */
private const val ACTION_OPEN_HOME = "com.shawnkowalchuk.milo.action.OPEN_HOME"

/**
 * The check's notification: "No trip recorded today. That is fine if the truck has not been
 * driven today. If it has, tap to open MilO and check that it is still set up." On a channel of
 * its own ("Daily check"), so that Shawn can silence it in the phone's settings without
 * touching the trip notifications.
 *
 * Default importance, like the monthly reminder: it makes the phone's notification sound and
 * does not pop up over what he is doing. On a day he does not drive before noon it is a false
 * alarm, and a false alarm must not be loud.
 *
 * A tap opens MilO on the Home screen ([homeAskedFor]), where a warning stands while the setup
 * is not in order.
 *
 * @param opens the activity a tap opens: MilO's one activity. Handed in by the container, so
 * that this package does not reach into `app/` for it.
 */
class NothingRecordedNotification(private val context: Context, private val opens: Class<*>) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        // Creating a channel that already exists changes nothing, so this is safe at every start.
        manager.createNotificationChannel(
            NotificationChannel(
                NOTHING_RECORDED_CHANNEL_ID,
                context.getString(R.string.notification_channel_nothing_recorded),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    /**
     * Posts the notification. Posted again while one is showing, it replaces that one and makes
     * no second sound.
     *
     * @return false if nobody will see it: notifications are switched off for MilO, or this
     * kind of notification is switched off in the phone's settings.
     */
    fun show(): Boolean {
        val text = context.getString(R.string.notification_nothing_recorded_text)
        val notification =
            Notification
                .Builder(context, NOTHING_RECORDED_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_trip)
                .setContentTitle(context.getString(R.string.notification_nothing_recorded_title))
                .setContentText(text)
                // The text is two sentences. Without this Android shows one line of it and
                // cuts the rest off, also when the notification is opened out.
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(openHome())
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build()
        manager.notify(NOTHING_RECORDED_NOTIFICATION_ID, notification)
        val channel = manager.getNotificationChannel(NOTHING_RECORDED_CHANNEL_ID)
        val channelOn = channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
        return manager.areNotificationsEnabled() && channelOn
    }

    /** Takes the notification away. Safe to call when none is showing. */
    fun cancel() {
        manager.cancel(NOTHING_RECORDED_NOTIFICATION_ID)
    }

    /**
     * A tap brings MilO's one activity to the front and asks it for Home. If the activity is
     * already there it is told (`onNewIntent`) and not built a second time, and whatever another
     * app had put on top of it inside MilO's window (an email draft, a PDF) is closed.
     */
    private fun openHome(): PendingIntent = PendingIntent.getActivity(
        context,
        OPEN_HOME_REQUEST,
        Intent(context, opens)
            .setAction(ACTION_OPEN_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Whether [intent] is a tap on the check's notification, which asks for the Home screen.
 *
 * False as well for an activity that Android starts again from the list of recent apps. Android
 * hands such a start the request the window was first opened with, and the notification that
 * was tapped then was dealt with then.
 */
fun homeAskedFor(intent: Intent?): Boolean = intent?.action == ACTION_OPEN_HOME &&
    intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
