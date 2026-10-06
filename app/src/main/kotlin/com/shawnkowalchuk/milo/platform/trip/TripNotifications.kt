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

// Channel ids are stored by Android with the user's choices for the channel. Renaming one
// creates a new channel and loses those choices.
private const val TRIP_CHANNEL_ID = "trip_in_progress"
private const val FAILURE_CHANNEL_ID = "trip_failures"

/** The source written to the event log for a trip started from the warning notification. */
private const val COULD_NOT_START_TAP = "tap on the could-not-start notification"

/**
 * The two notifications of trip recording, each on its own channel.
 *
 * - **Trip in progress**: low importance, so it never makes a sound or pops up. The trip-start
 *   sound is played by the service itself and not through this channel, because MIUI is reported
 *   to switch channel sounds off (docs/research/2026-10-03-miui-dev-bluetooth-audio.md).
 * - **Could not start this trip**: high importance. It is the only way MilO can tell Shawn that
 *   a trip is not being recorded, and tapping it starts the trip.
 *
 * Posting needs the notification permission on Android 13 and later. Without it Android drops
 * the notification silently, and the trip service still runs. [showCouldNotStart] reports whether
 * notifications are on, so the event log can say that the warning went unseen.
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
    }

    /**
     * The ongoing notification.
     *
     * @param trip the trip to show, or null in the moment between the service starting and the
     * trip being opened.
     */
    fun tripInProgress(trip: CurrentTrip?): Notification {
        val builder =
            Notification
                .Builder(context, TRIP_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_trip)
                .setContentTitle(context.getString(titleFor(trip)))
                .setContentIntent(openApp())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                // Android 12 and later may hold a foreground notification back for ten seconds.
                // This one is Shawn's sign that the trip started, so it is shown at once.
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
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

    /** Replaces the ongoing notification's content. */
    fun updateTripInProgress(trip: CurrentTrip?) {
        manager.notify(TRIP_NOTIFICATION_ID, tripInProgress(trip))
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
                .setContentIntent(startTrip())
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
     */
    private fun startTrip(): PendingIntent = PendingIntent.getForegroundService(
        context,
        0,
        // No time in the intent: the trip starts when the notification is tapped, not when it
        // was posted.
        tripServiceIntent(context, TripTrigger.MANUAL_START, COULD_NOT_START_TAP, atMs = null),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
