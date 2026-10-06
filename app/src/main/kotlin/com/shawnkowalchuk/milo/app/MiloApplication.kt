package com.shawnkowalchuk.milo.app

import android.app.Application
import com.shawnkowalchuk.milo.platform.diagnostics.CrashHandler
import com.shawnkowalchuk.milo.platform.trip.TripTrigger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The process-wide entry point. Android creates it before any activity, service or receiver, so
 * it runs however the process was started: from the launcher, or from a Bluetooth event or a
 * reboot with no screen at all.
 *
 * It does four things only: it owns the [AppContainer], it starts the crash and kill capture,
 * it has the trip controller look at what the last process left behind, and it checks that
 * Android still watches for the truck.
 */
class MiloApplication : Application() {
    /** Created in [onCreate]. Screens and services reach every shared object through it. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Before any real work begins (building the container only wires objects together), so
        // a crash anywhere later in start-up is still captured.
        CrashHandler.install(container.crashFileStore)

        // Reading files and the database must not hold up the main thread, least of all when
        // the process was started by a trip trigger with seconds to begin recording.
        container.applicationScope.launch(Dispatchers.IO) {
            container.startupDiagnostics.record()
        }

        // ADR-002's reconcile. No Bluetooth event fires for a truck that is already connected
        // when the process starts, and a trip left open by a killed process has to be picked
        // up or closed. The call only puts the trigger in the controller's inbox; the reading
        // and the database work happen on the controller's worker.
        container.tripController.onTrigger(TripTrigger.RECONCILE, PROCESS_START)

        // The association with the truck is Android's and can be removed behind MilO's back.
        // Asking Android again to watch for the truck changes nothing if it already does.
        container.truckPairing.check(PROCESS_START)
    }

    private companion object {
        /** What the event log calls the reconcile and the pairing check at process start. */
        const val PROCESS_START = "process start"
    }
}
