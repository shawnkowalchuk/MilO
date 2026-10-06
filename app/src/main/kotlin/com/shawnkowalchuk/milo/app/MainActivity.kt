package com.shawnkowalchuk.milo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shawnkowalchuk.milo.platform.trip.TripTrigger

/**
 * The only activity. It hosts the Compose UI and says when MilO has been opened or has come
 * back to the front, and nothing else: no logic, no system calls. Anything that talks to
 * Android (Bluetooth, location, notifications) belongs in `platform/` and is reached through a
 * ViewModel.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 15 and later force apps to draw edge to edge; the phone this runs on
        // (Android 14) does not. Opting in here gives both the same layout, so what is tested on
        // the phone today does not shift after a system update.
        enableEdgeToEdge()

        val container = (application as MiloApplication).container
        setContent {
            MiloApp(container)
        }
    }

    /**
     * ADR-002's reconcile at app launch, and the check that Android still watches for the
     * truck. Both run every time MilO comes to the front, not only when the activity is first
     * created: Android keeps an activity for days, and opening MilO is what Shawn does when a
     * trip did not start by itself. Each call only queues work; nothing here waits.
     */
    override fun onStart() {
        super.onStart()
        val container = (application as MiloApplication).container
        container.tripController.onTrigger(TripTrigger.RECONCILE, APP_OPENED)
        container.truckPairing.check(APP_OPENED)
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
        /** What the event log calls the reconcile and the pairing check at app launch. */
        const val APP_OPENED = "app opened"

        /** What the event log calls the driving alert's look at its permission. */
        const val IN_FRONT = "MilO in front"
    }
}
