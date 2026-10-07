package com.shawnkowalchuk.milo.platform.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.app.MainActivity
import com.shawnkowalchuk.milo.platform.car.CarAction
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import com.shawnkowalchuk.milo.platform.trip.tripServiceIntent

// The widget as Android draws it on the home screen: a layout of plain views (RemoteViews),
// filled in from [HomeWidgetContent]. The home screen app draws it, not MilO, which is why it
// is not made of the app's Compose components; its colours are the theme's, written in
// res/values/colors.xml (kept equal by WidgetColorsTest).

/** What the event log calls a press of the widget's buttons. */
internal const val WIDGET_START_SOURCE = "home-screen widget Start button"
internal const val WIDGET_END_SOURCE = "home-screen widget End button"

// Each PendingIntent of the widget has a number of its own, so that none replaces another.
private const val START_REQUEST = 7301
private const val END_REQUEST = 7302
private const val OPEN_REQUEST = 7303

/** The widget showing [content]. A tap anywhere but on the button opens MilO. */
internal fun homeWidgetViews(context: Context, content: HomeWidgetContent): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_home)
    val screen = content.screen
    views.setTextViewText(R.id.widget_status, context.getString(screen.status.textRes))

    val trip = screen.trip
    val startedAtMs = content.tripStartedAtMs
    if (trip == null || startedAtMs == null) {
        views.setViewVisibility(R.id.widget_trip, View.GONE)
    } else {
        views.setViewVisibility(R.id.widget_trip, View.VISIBLE)
        views.setTextViewText(R.id.widget_km, trip.kilometres)
        // The clock runs by itself on the home screen: it needs no redraw every second. Its
        // base is on the clock that counts from boot, worked out from the trip's start.
        val runningMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0)
        val base = SystemClock.elapsedRealtime() - runningMs
        views.setChronometer(R.id.widget_elapsed, base, null, true)
    }

    views.setTextViewText(R.id.widget_dollars, dollarsLine(context, content.dollars))

    val starting = screen.action == CarAction.START_TRIP
    views.setTextViewText(R.id.widget_button, context.getString(screen.action.labelRes))
    views.setInt(
        R.id.widget_button,
        "setBackgroundResource",
        if (starting) R.drawable.widget_button_accent else R.drawable.widget_button_quiet,
    )
    views.setTextColor(
        R.id.widget_button,
        context.getColor(if (starting) R.color.milo_widget_on_accent else R.color.milo_widget_text),
    )
    views.setOnClickPendingIntent(
        R.id.widget_button,
        if (starting) startTrip(context) else endTrip(context),
    )
    views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
    return views
}

/** What a widget on the home screen shows while the switch in Settings is off. */
internal fun switchedOffViews(context: Context): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_off).apply {
        setOnClickPendingIntent(R.id.widget_root, openApp(context))
    }

/** "Oct $412 · 2026 $3,980 at the CRA rate", with the rate's year when it is not this one. */
private fun dollarsLine(context: Context, dollars: WidgetDollars?): String {
    if (dollars == null) return context.getString(R.string.widget_dollars_unknown)
    val line =
        context.getString(
            R.string.widget_dollars,
            dollars.monthLabel,
            dollars.month,
            dollars.yearLabel,
            dollars.year,
        )
    return if (dollars.rateIsTheYears) {
        line
    } else {
        context.getString(R.string.widget_dollars_older_rate, line, dollars.rateYear.toString())
    }
}

/**
 * Start goes to the trip service directly, as the tap on a notification does: a press on a
 * widget is one of the moments at which Android lets an app start a foreground service that
 * uses location from the background (ADR-002, amendment 35).
 */
private fun startTrip(context: Context): PendingIntent = PendingIntent.getForegroundService(
    context,
    START_REQUEST,
    tripServiceIntent(context, TripTrigger.MANUAL_START, WIDGET_START_SOURCE, atMs = null),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

/** End goes to [HomeWidgetActionReceiver], which hands it to the trip controller. */
private fun endTrip(context: Context): PendingIntent = PendingIntent.getBroadcast(
    context,
    END_REQUEST,
    Intent(context, HomeWidgetActionReceiver::class.java).setAction(ACTION_END_TRIP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    OPEN_REQUEST,
    Intent(context, MainActivity::class.java),
    PendingIntent.FLAG_IMMUTABLE,
)
