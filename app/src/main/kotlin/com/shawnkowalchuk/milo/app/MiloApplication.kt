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
 * It does five things only: it owns the [AppContainer], it starts the crash and kill capture,
 * it has the trip controller look at what the last process left behind, it checks that
 * Android still watches for the truck, and it has the addresses of finished trips caught up.
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

        // Trips whose address lookup failed, or that were recorded before MilO looked addresses
        // up at all, are tried again. The request goes through the trip controller so that the
        // pass follows the reconcile above: a trip the restart rules close there is finished by
        // the time the pass reads the trips. The lookup never saw that trip in progress, so
        // nothing else would ask for it. The lookup itself is created here, not in the callback,
        // so that it is watching before the controller publishes anything.
        val addresses = container.tripAddresses
        container.tripController.whenCaughtUp { addresses.catchUp(PROCESS_START) }
    }

    private companion object {
        /** What the event log calls the work that is prompted by a process start. */
        const val PROCESS_START = "process start"
    }
}
