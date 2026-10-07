package com.shawnkowalchuk.milo.platform.trip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.app.MainActivity
import com.shawnkowalchuk.milo.core.util.formatKilometres

/** The id of the ongoing trip notification. It is the service's foreground notification. */
const val TRIP_NOTIFICATION_ID = 1

private const val COULD_NOT_START_NOTIFICATION_ID = 2
private const val DRIVING_ALERT_NOTIFICATION_ID = 3

// Channel ids are stored by Android with the user's choices for the channel. Renaming one
// creates a new channel and loses those choices.
private const val TRIP_CHANNEL_ID = "trip_in_progress"
private const val FAILURE_CHANNEL_ID = "trip_failures"
private const val DRIVING_CHANNEL_ID = "driving_alert"

/** The source written to the event log for a trip started from the warning notification. */
private const val COULD_NOT_START_TAP = "tap on the could-not-start notification"

/** The source written to the event log for a trip started from the driving alert. */
private const val DRIVING_ALERT_TAP = "tap on the driving alert"

// Android tells two tap intents for the same service apart by this number and not by what
// they carry. With one number for both, the second notification's intent would replace the
// first one's, and a tap on either would be logged as a tap on the other.
private const val COULD_NOT_START_TAP_REQUEST = 0
private const val DRIVING_ALERT_TAP_REQUEST = 1

/**
 * The three notifications around trip recording, each on its own channel.
 *
 * - **Trip in progress**: low importance, so it never makes a sound or pops up. The trip-start
 *   sound is played by the service itself and not through this channel, because MIUI is reported
 *   to switch channel sounds off (docs/research/2026-10-03-miui-dev-bluetooth-audio.md). It is
 *   the trip service's own notification, so while the service waits beside a parked truck with
 *   no trip open it says that in its place ([parkedWaiting]), as quietly.
 * - **Could not start this trip**: high importance. It is the only way MilO can tell Shawn that
 *   a trip is not being recorded, and tapping it starts the trip.
 * - **Driving alert**: high importance. The phone reports driving during the work hours while
 *   no trip is being recorded and the truck is not connected (`platform/driving/`). Tapping it
 *   starts a trip exactly as a tap on the warning above does. Posting it never starts one.
 *
 * Posting needs the notification permission on Android 13 and later. Without it Android drops
 * the notification silently, and the trip service still runs. [showCouldNotStart] and
 * [showDrivingAlert] report whether the notification can be seen, so the event log can say
 * that it went unseen.
 */
class TripNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        // Creating a channel that already exists changes nothing, so this is safe at every start.
        manager.createNotificationChannel(
            NotificationChannel(
                TRIP_CHANNEL_ID,
                context.getString(R.string.notification_channel_trip),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                FAILURE_CHANNEL_ID,
                context.getString(R.string.notification_channel_failures),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                DRIVING_CHANNEL_ID,
                context.getString(R.string.notification_channel_driving),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    /**
     * The ongoing notification.
     *
     * @param trip the trip to show, or null in the moment between the service starting and the
     * trip being opened.
     */
    fun tripInProgress(trip: CurrentTrip?): Notification {
        val builder = ongoing(titleFor(trip))
        if (trip != null) {
            val locale = context.resources.configuration.locales[0]
            val kilometres = formatKilometres(trip.distanceMetres, locale)
            builder
                .setContentText(context.getString(R.string.distance_km, kilometres))
                // The system counts the elapsed time up by itself from the trip's start, so the
                // notification does not have to be posted again every second to show it.
                .setWhen(trip.startedAtMs)
                .setShowWhen(true)
                .setUsesChronometer(true)
        }
        return builder.build()
    }

    /**
     * The ongoing notification while no trip is open and the service watches a truck that is
     * connected and parked: it says so, and that a trip starts when the truck moves.
     */
    fun parkedWaiting(): Notification = ongoing(R.string.trip_status_parked)
        .setContentText(context.getString(R.string.notification_parked_text))
        .build()

    /** The ongoing notification for what the controller shows at this moment. */
    fun ongoingFor(activity: TripActivity): Notification {
        val waiting = activity.trip == null && activity.parked == ParkedTruckWatch.WAITING_TO_MOVE
        return if (waiting) parkedWaiting() else tripInProgress(activity.trip)
    }

    private fun ongoing(titleRes: Int): Notification.Builder = Notification
        .Builder(context, TRIP_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_trip)
        .setContentTitle(context.getString(titleRes))
        .setContentIntent(openApp())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(Notification.CATEGORY_SERVICE)
        // Android 12 and later may hold a foreground notification back for ten seconds.
        // This one is Shawn's sign that the trip started, so it is shown at once.
        .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)

    /** Replaces the ongoing notification's content. */
    fun updateTripInProgress(trip: CurrentTrip?) {
        manager.notify(TRIP_NOTIFICATION_ID, tripInProgress(trip))
    }

    /** Replaces the ongoing notification's content with [parkedWaiting]. */
    fun showParkedWaiting() {
        manager.notify(TRIP_NOTIFICATION_ID, parkedWaiting())
    }

    /**
     * Posts "MilO could not start this trip. Tap to start".
     *
     * @return false if notifications are switched off for MilO, so nobody will see it.
     */
    fun showCouldNotStart(): Boolean {
        val notification =
            Notification
                .Builder(context, FAILURE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_trip)
                .setContentTitle(context.getString(R.string.notification_could_not_start_title))
                .setContentText(context.getString(R.string.notification_could_not_start_text))
                .setContentIntent(startTrip(COULD_NOT_START_TAP_REQUEST, COULD_NOT_START_TAP))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ERROR)
                .build()
        manager.notify(COULD_NOT_START_NOTIFICATION_ID, notification)
        return manager.areNotificationsEnabled()
    }

    /** Takes the warning away again: a trip is being recorded. */
    fun cancelCouldNotStart() {
        manager.cancel(COULD_NOT_START_NOTIFICATION_ID)
    }

    /**
     * Posts the driving alert: "You seem to be driving. No trip is being recorded. Tap to start
     * one". Posting it starts nothing; only the tap does.
     *
     * Posted again while it is still showing, it makes no second sound, so a phone that reports
     * the same drive twice does not nag.
     *
     * @return false if nobody will see it: notifications are switched off for MilO, or this
     * kind of notification is switched off in the phone's settings.
     */
    fun showDrivingAlert(): Boolean {
        // TODO(debt): the tap runs no preflight, unlike Home's Start button, and before this
        // tap none has run. With location switched off for the whole phone it starts a trip
        // that records nothing (FINDINGS_LOG, 2026-10-06, "A tap on the driving alert skips
        // the preflight").
        val notification =
            Notification
                .Builder(context, DRIVING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_trip)
                .setContentTitle(context.getString(R.string.notification_driving_title))
                .setContentText(context.getString(R.string.notification_driving_text))
                .setContentIntent(startTrip(DRIVING_ALERT_TAP_REQUEST, DRIVING_ALERT_TAP))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build()
        manager.notify(DRIVING_ALERT_NOTIFICATION_ID, notification)
        val channel = manager.getNotificationChannel(DRIVING_CHANNEL_ID)
        val channelOn = channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
        return manager.areNotificationsEnabled() && channelOn
    }

    /** Takes the driving alert away: a trip is being recorded, or the drive is over. */
    fun cancelDrivingAlert() {
        manager.cancel(DRIVING_ALERT_NOTIFICATION_ID)
    }

    private fun titleFor(trip: CurrentTrip?): Int = when {
        trip == null -> R.string.notification_trip_starting
        trip.waitingForTruck -> R.string.trip_status_waiting_for_truck
        else -> R.string.trip_status_in_progress
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * A tap starts the trip service directly. A tap on a notification is one of the moments at
     * which Android lets an app start a foreground service and use location from the
     * background, so this works even when the automatic start was refused (ADR-002).
     *
     * @param request tells this notification's tap from the other one's.
     * @param source which notification was tapped, in words, for the event log.
     */
    private fun startTrip(request: Int, source: String): PendingIntent =
        PendingIntent.getForegroundService(
            context,
            request,
            // No time in the intent: the trip starts when the notification is tapped, not when
            // it was posted.
            tripServiceIntent(context, TripTrigger.MANUAL_START, source, atMs = null),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
