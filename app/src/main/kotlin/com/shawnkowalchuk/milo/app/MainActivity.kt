package com.shawnkowalchuk.milo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloSystemBarStyle
import com.shawnkowalchuk.milo.platform.nothingrecorded.homeAskedFor
import com.shawnkowalchuk.milo.platform.reminder.reportMonthToOpen
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import java.time.YearMonth

/**
 * The only activity. It hosts the Compose UI, says when MilO has been opened or has come back
 * to the front, and passes on what a tapped notification asked for: which month's report (the
 * monthly reminder), or the Home screen (the daily check).
 * Nothing else: no logic, no system calls. Anything that talks to Android (Bluetooth, location,
 * notifications) belongs in `platform/` and is reached through a ViewModel.
 */
class MainActivity : ComponentActivity() {
    /**
     * The month whose Report screen a tap on the monthly reminder asked for, until the screens
     * have opened it. Read from the request that started or reached the activity; what the
     * request holds is the reminder's own business (`reportMonthToOpen`).
     */
    private var reportToOpen by mutableStateOf<YearMonth?>(null)

    /**
     * True from a tap on the daily check's notification until the screens have shown Home.
     * Whether a request is such a tap is the check's own business (`homeAskedFor`).
     */
    private var homeAsked by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 15 and later force apps to draw edge to edge; the phone this runs on
        // (Android 14) does not. Opting in here gives both the same layout, so what is tested on
        // the phone today does not shift after a system update.
        enableEdgeToEdge(MiloSystemBarStyle, MiloSystemBarStyle)

        // Only for an activity that is being built for the first time. One that Android builds
        // again (the phone was turned, or MilO was put away and brought back) still carries the
        // request it was first started with, and that tap was dealt with then: the screens come
        // back as they were left.
        if (savedInstanceState == null) {
            reportToOpen = reportMonthToOpen(intent)
            homeAsked = homeAskedFor(intent)
        }

        val container = (application as MiloApplication).container
        setContent {
            MiloApp(
                container = container,
                reportToOpen = reportToOpen,
                onReportOpened = { reportToOpen = null },
                homeAsked = homeAsked,
                onHomeShown = { homeAsked = false },
            )
        }
    }

    /** A notification was tapped while MilO's activity was already there. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        reportMonthToOpen(intent)?.let { reportToOpen = it }
        if (homeAskedFor(intent)) homeAsked = true
    }

    /**
     * ADR-002's reconcile at app launch, the check that Android still watches for the truck,
     * a look at the monthly reminder and one at the daily check that a work day has a trip.
     * All run every time MilO comes to the front, not only when the activity is first created:
     * Android keeps an activity for days, and opening MilO is what Shawn does when a trip did
     * not start by itself. Each call only queues work; nothing here waits.
     */
    override fun onStart() {
        super.onStart()
        val container = (application as MiloApplication).container
        container.tripController.onTrigger(TripTrigger.RECONCILE, APP_OPENED)
        container.truckPairing.check(APP_OPENED)
        // The monthly reminder is looked at whenever MilO comes to the front, beside its daily
        // alarm: on a phone that holds the alarm back, opening MilO is what brings it.
        container.reports.reminder.look(APP_OPENED)
        // So is the daily check. It follows the reconcile above, which only asks for the trip
        // service when the truck turns out to be connected: the trip is stored a moment later.
        // The check gives it that moment by itself before it says "no trip" (its second look).
        container.checks.nothingRecorded.look(APP_OPENED)
    }

    /**
     * The driving alert depends on the Physical activity permission, and Android reports no
     * change of a permission. Here and not in [onStart]: Android's permission dialog only
     * pauses MilO, so its answer is followed by a resume and by no start. The call asks the
     * phone for nothing unless the permission or the Settings switch has changed.
     */
    override fun onResume() {
        super.onResume()
        (application as MiloApplication).container.drivingAlert.arm(IN_FRONT)
    }

    private companion object {
        /** What the event log calls the work that is prompted by MilO coming to the front. */
        const val APP_OPENED = "app opened"

        /** What the event log calls the driving alert's look at its permission. */
        const val IN_FRONT = "MilO in front"
    }
}
