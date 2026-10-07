package com.shawnkowalchuk.milo.platform.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shawnkowalchuk.milo.app.MiloApplication
import com.shawnkowalchuk.milo.platform.bluetooth.holdUntilHandled
import com.shawnkowalchuk.milo.platform.trip.TripTrigger

/** The widget's End button. Only [HomeWidgetActionReceiver] is aimed at with it. */
internal const val ACTION_END_TRIP = "com.shawnkowalchuk.milo.action.WIDGET_END_TRIP"

/**
 * The home-screen widget, as Android knows it (declared in the manifest with
 * res/xml/home_widget_info.xml). When the home screen asks for it to be drawn (it was just
 * added, the phone restarted, or its few hours are up), it is drawn from storage. Between those
 * moments [HomeWidget] keeps it current while MilO's process runs.
 */
class HomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val widget = (context.applicationContext as MiloApplication).container.widgets.homeWidget
        // Held open until it is drawn: reading storage takes a moment after onUpdate returns.
        widget.refresh(holdUntilHandled())
    }
}

/**
 * Where the widget's End button arrives. It hands the press to the trip controller, the same
 * trigger as the End button in the app and on the Android Auto screen. Not exported: the
 * button is a PendingIntent MilO made itself.
 */
class HomeWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_END_TRIP) return
        val controller = (context.applicationContext as MiloApplication).container.tripController
        // Held open until the controller has dealt with it, so that a process the press
        // itself started is not frozen before the trip is closed.
        val done = holdUntilHandled()
        controller.onTrigger(TripTrigger.MANUAL_END, WIDGET_END_SOURCE)
        controller.whenCaughtUp(done)
    }
}
