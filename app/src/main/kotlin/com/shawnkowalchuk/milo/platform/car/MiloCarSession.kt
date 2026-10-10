package com.shawnkowalchuk.milo.platform.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.system.SetupChecklist
import com.shawnkowalchuk.milo.platform.trip.TripController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * One visit of Android Auto to MilO. Android Auto creates a session when MilO is opened on the
 * car's display and destroys it some time after the driver has left MilO, so a session holds
 * nothing: it builds the one screen from the app-wide objects and keeps the event log informed.
 *
 * **The log lines are the point of this class.** How long Android Auto keeps a session, which
 * host it is and which Car API level it speaks are not documented for this phone and truck
 * (docs/research/2026-10-03-android-auto-screen.md, findings 22 and 23). The lines written here
 * are the only way to find out.
 */
class MiloCarSession(
    private val controller: TripController,
    private val trips: TripRepository,
    private val checklist: SetupChecklist,
    private val settings: Flow<MiloSettings>,
    private val shownUnit: StateFlow<DistanceUnit>,
    private val clock: () -> Long,
) : Session() {
    init {
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                logLineFor(event)?.let { controller.note(EventCategory.ANDROID_AUTO, it) }
            },
        )
    }

    /** The only screen. Nothing is ever pushed on top of it (see [TripStatusScreen]). */
    override fun onCreateScreen(intent: Intent): Screen =
        TripStatusScreen(carContext, controller, trips, checklist, settings, shownUnit, clock)

    private fun logLineFor(event: Lifecycle.Event): String? = when (event) {
        Lifecycle.Event.ON_CREATE -> sessionCreatedText(hostPackage(), carApiLevel())

        Lifecycle.Event.ON_START -> "Android Auto screen: shown on the car's display"

        Lifecycle.Event.ON_STOP -> "Android Auto screen: no longer shown"

        Lifecycle.Event.ON_DESTROY -> "Android Auto screen: session destroyed"

        // Resume and pause say only whether the screen has the driver's focus.
        else -> null
    }

    private fun hostPackage(): String? = carContext.hostInfo?.packageName

    /** The level agreed with the host, or null if the library says none has been agreed. */
    private fun carApiLevel(): Int? = try {
        carContext.carAppApiLevel
    } catch (notAgreed: IllegalStateException) {
        // The library throws this until the handshake is over. It is over by the time a session
        // is created; if that ever changes, the log line says so and the session carries on.
        null
    }
}

/** The line that records who opened MilO on a car's display, and in which dialect. */
internal fun sessionCreatedText(hostPackage: String?, carApiLevel: Int?): String {
    val host = hostPackage ?: "a host that gave no name"
    val level = carApiLevel?.toString() ?: "not agreed"
    return "Android Auto screen: session created by $host, Car API level $level"
}
