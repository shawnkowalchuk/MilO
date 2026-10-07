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
 * It does twelve things only: it owns the [AppContainer], it has what Android's backup left
 * behind dealt with (a restore above all), it starts the crash and kill capture (which also
 * trims the event log), it has an import finished that the last process was ended in the
 * middle of, it has the trip controller look at what the last process left behind,
 * it checks that Android still watches for the truck, it has the addresses of finished trips
 * caught up, it has the trips that are not sorted into Business or Personal yet sorted, it has
 * the driving alert ask the phone again to report driving, it has the monthly reminder ask
 * for its daily alarm again and look at whether a reminder is due, it has the daily check do
 * the same and ask whether a trip has been recorded today, and it starts the watch that writes
 * Android Auto's connection changes to the event log outside trips.
 *
 * Android's backup and restore do not come through here: Android runs them in a process of
 * another kind, with a plain `Application` object in place of this one (`MiloBackupAgent`).
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
            // First what the backup agent left behind. After a restore the settings are those
            // of another installation, and what was only true there is taken out before the
            // diagnostics read them. The pairing check below may run before this has finished;
            // it then finds no association for the truck either way, and is asked once more.
            container.transfer.aftermath.settle()
            container.startupDiagnostics.record()
            // Last, an import that the process before this one was ended in the middle of: it
            // stored the file's trips and left the raw points of the trips that are gone.
            // Finishing it can take half a minute, so it comes after the diagnostics, whose
            // lines say when this process started. It touches no trip, and a trigger that
            // started this process does not wait for it.
            container.transfer.dataTransfer.finishCutOffImport()
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

        // Trips recorded before MilO had a work schedule have no Business or Personal yet. They
        // are sorted now, by the schedule as it is. The pass waits for the reconcile like the
        // address pass, so that a process started by a trip trigger does the trigger's work
        // first; a trip the reconcile closes is sorted by the controller itself as it closes.
        // Built here and not in the callback, which runs on the trip controller's own worker:
        // nothing of this may fail in there.
        val categories = container.tripCategoryCatchUp
        container.tripController.whenCaughtUp { categories.catchUp(PROCESS_START) }

        // The request to be told about driving does not outlive a reboot or an update of MilO,
        // and both of those start a new process, so it is made again here. Nothing in it can
        // start a trip: a report of driving leads to a notification at most.
        container.drivingAlert.arm(PROCESS_START)

        // Android forgets every alarm at a reboot and when an app is force-stopped, and both
        // end in a new process, so the reminder's daily alarm is asked for again here. The
        // reminder is looked at as well: the phone may have been off on the reminder day. It
        // waits for the reconcile like the two passes above, so that a process started by a
        // trip trigger does the trigger's work first. Built here and not in the callback, which
        // runs on the trip controller's own worker.
        val reminder = container.reports.reminder
        container.tripController.whenCaughtUp { reminder.arm(PROCESS_START) }

        // The daily check that a work day has a trip asks for its alarm again for the same
        // reason, and asks its question: the phone may have been off at the time it is set to.
        // It waits for the reconcile by itself, and reads the trips only after it; before it
        // says that there is none it reads them a second time, a moment later, because a trip
        // the truck is just starting is stored only once the trip service is up.
        container.checks.nothingRecorded.arm(PROCESS_START)

        // Android Auto's connection is watched from here on, for the event log only: while a
        // trip is being recorded the trip service has a watch of its own, and outside a trip
        // nothing else writes a change down. The watch can neither start nor hold a trip. It
        // begins after the reconcile like the passes above, so that a process started by a trip
        // trigger does the trigger's work first. Built here and not in the callback, which runs
        // on the trip controller's own worker.
        val androidAuto = container.car.connectionLog
        container.tripController.whenCaughtUp { androidAuto.start() }

        // The home-screen widget is kept in step with the trip for the life of the process,
        // and its switch in Settings is applied again (a restore can have changed it). It only
        // draws: its buttons reach the trip controller like every other button. After the
        // reconcile, like the passes above.
        val widget = container.widgets.homeWidget
        container.tripController.whenCaughtUp { widget.start() }
    }

    private companion object {
        /** What the event log calls the work that is prompted by a process start. */
        const val PROCESS_START = "process start"
    }
}
