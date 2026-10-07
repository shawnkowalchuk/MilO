package com.shawnkowalchuk.milo.platform.driving

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.bluetooth.TruckConnectionSource
import com.shawnkowalchuk.milo.platform.trip.ParkedTruckWatch
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The driving alert, the safety net for the day Bluetooth never connects: during the work hours,
 * when the phone reports that Shawn is in a moving vehicle while no trip is being recorded and
 * the paired truck is not connected, MilO posts a notification that starts a trip when it is
 * tapped. Every condition is in [judgeDriving].
 *
 * **It never starts a trip.** The phone cannot tell the truck from any other vehicle, so a
 * report of driving only ever leads to a notification (ADR-002). This class is given neither the
 * trip controller nor the trip storage: it can ask whether a trip is open and when the last one
 * ended, watch what the controller publishes and wait for it to have caught up, and nothing
 * else. The tap starts the trip service through the same intent the "could not start this trip"
 * notification uses, and that path is not in this package.
 *
 * **A failure in here never reaches the recording.** The alert runs in the process the trip
 * service runs in, and a report arrives about a minute into every drive. Each of the three
 * pieces of work below is run by [DrivingFailures.keptApart], which writes a failure to the
 * event log and lets nothing out.
 *
 * Three things happen here:
 * - [arm] keeps the request to the phone in step with the Settings switch and the Physical
 *   activity permission.
 * - [onReports] decides what a report means ([judgeDriving]) and shows or withdraws the alert.
 * - A trip that starts being recorded, however it started, withdraws the alert: "no trip is
 *   being recorded" is no longer true.
 *
 * @param showAlert posts the notification, and answers false if nobody can see it because
 * notifications are switched off.
 * @param withdrawAlert takes the notification away. Safe to call when none is showing.
 * @param openTripStored whether storage holds an open trip. The stored row is asked, and not
 * what the controller publishes: a trip the last process left open is published only once the
 * trip service is up again, seconds after a report that started the process, and an alert in
 * between would say "no trip is being recorded" about a trip that is being picked up.
 * @param lastTripEndedAtMs when the newest closed trip ended, or null if there is none. Read
 * from storage as well, so the answer is the same after a restart of the process.
 * @param tripActivity what the trip controller publishes about the trip in progress. It is what
 * says that a trip has begun.
 * @param whenTripsCaughtUp runs its argument once the trip controller has dealt with everything
 * handed to it so far (`TripController.whenCaughtUp`). A report can be what started MilO's
 * process, and a trip the last process left open long ago is closed by the controller's first
 * look at storage. Asked before that, the stale row would silence the alert.
 * @param crashFileStore where a failure goes if the event log itself cannot be written.
 * @param clock wall-clock milliseconds.
 * @param scope the application scope: a report is dealt with after `onReceive` has returned.
 */
class DrivingAlert(
    private val detection: DrivingDetection,
    private val showAlert: () -> Boolean,
    private val withdrawAlert: () -> Unit,
    private val settings: SettingsStore,
    private val truck: TruckConnectionSource,
    private val openTripStored: suspend () -> Boolean,
    private val lastTripEndedAtMs: suspend () -> Long?,
    private val tripActivity: StateFlow<TripActivity>,
    private val whenTripsCaughtUp: (done: () -> Unit) -> Unit,
    private val eventLog: EventLogRepository,
    crashFileStore: CrashFileStore,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
    private val scope: CoroutineScope,
) {
    private val failures = DrivingFailures(eventLog, crashFileStore, clock)

    /** One request to the phone at a time; the two fields below are only touched inside it. */
    private val oneRequestAtATime = Mutex()

    /** What the phone was last asked for, successfully, by this process. Null before that. */
    private var asked: DrivingWatch? = null

    /** The failure last written to the event log, so that a repeat of it is not written again. */
    private var failureLogged: String? = null

    init {
        scope.launch {
            tripActivity
                .map { it.trip != null }
                .distinctUntilChanged()
                .filter { recording -> recording }
                .collect { failures.keptApart(WITHDRAWING_FOR_A_TRIP) { withdrawAlert() } }
        }
    }

    /**
     * Brings the request to the phone in line with the Settings switch and the Physical activity
     * permission: reports are asked for while both allow it, and no longer otherwise.
     *
     * The phone is asked only when what is wanted differs from what this process last asked
     * for. The first call in a process always asks, and that is what renews the request after a
     * reboot and after an update of MilO, which both drop it: each of them starts a new process
     * (the boot and update receiver is what Android starts it for), and [arm] is called from the
     * application's start. Later calls come from MilO coming to the front, where the permission
     * may just have been granted, and from the Settings switch.
     *
     * @param source what prompted the call, in words, for the event log.
     */
    fun arm(source: String) {
        scope.launch {
            oneRequestAtATime.withLock {
                failures.keptApart("looking at what to ask of the phone ($source)") {
                    bringInLine(source)
                }
            }
        }
    }

    private suspend fun bringInLine(source: String) {
        val watch =
            DrivingWatch(stored().settings.drivingAlertEnabled, detection.permissionGranted())
        if (watch == asked) return
        // An alert must not outlive the switch or the permission it depends on.
        if (!watch.wanted) withdrawAlert()
        val problem =
            when {
                watch.wanted -> detection.watch()

                // Switched off: the earlier request is taken back, so the phone does not start
                // MilO for reports nobody wants.
                watch.permissionGranted -> detection.stopWatching()

                // Without the permission the phone reports nothing and refuses every request,
                // the one to stop included. There is nothing to ask.
                else -> null
            }
        if (problem == null) {
            asked = watch
            failureLogged = null
            eventLog.add(clock(), EventCategory.DRIVING, watchText(watch, source))
        } else if (problem != failureLogged) {
            // Tried again at the next call. Written once, not every time MilO is opened.
            failureLogged = problem
            eventLog.add(clock(), EventCategory.ERROR, watchFailedText(watch, source), problem)
        }
    }

    /**
     * The phone has reported entering or leaving a vehicle.
     *
     * @param reports oldest first, as the phone handed them over.
     * @param done called when the reports have been dealt with, whatever happened. The receiver
     * keeps its broadcast open until then, so that Android does not freeze a process that the
     * report itself started.
     */
    fun onReports(reports: List<VehicleReport>, done: () -> Unit) {
        // The callback runs on the trip controller's own worker, so it only hands the work on.
        whenTripsCaughtUp {
            scope.launch {
                try {
                    failures.keptApart(JUDGING_A_REPORT) { judge(reports) }
                } finally {
                    done()
                }
            }
        }
    }

    private suspend fun judge(reports: List<VehicleReport>) {
        val stored = stored()
        val reading = truck.read()
        val moment =
            DrivingMoment(
                reports = reports,
                alertEnabled = stored.settings.drivingAlertEnabled,
                tripInProgress = openTripStored(),
                truckPaired = stored.truckPaired,
                truck = reading.answer,
                lastTripEndedAtMs = lastTripEndedAtMs(),
                lastAlertAtMs = stored.settings.lastDrivingAlertAtMs,
                nowMs = clock(),
                schedule = stored.settings.schedule,
                zone = zone(),
                truckNoLongerWatched =
                    tripActivity.value.parked == ParkedTruckWatch.NO_LONGER_WATCHED,
            )
        val verdict = judgeDriving(moment)
        var seen = true
        var notRemembered: String? = null
        when (verdict.step) {
            DrivingAlertStep.SHOW -> {
                seen = showAndCheck()
                notRemembered = rememberAlert(moment.nowMs)
            }

            DrivingAlertStep.WITHDRAW -> withdrawAlert()

            DrivingAlertStep.LEAVE -> Unit
        }
        eventLog.add(
            moment.nowMs,
            EventCategory.DRIVING,
            judgedText(reports, verdict, seen),
            momentText(moment, reading.evidence, listOfNotNull(stored.problem, notRemembered)),
        )
    }

    /**
     * Posts the alert, and takes it straight back if a trip began while the truck was being
     * read: the withdrawal that follows a trip start may have run before the alert was posted.
     */
    private fun showAndCheck(): Boolean {
        val seen = showAlert()
        if (tripActivity.value.trip != null) withdrawAlert()
        return seen
    }

    /**
     * Stores when the alert was posted, which is what keeps a second one away for a while and
     * what a new process finds. The alert is posted first: if the time cannot be stored, the
     * next report may alert again, and the log says so. The other order could leave MilO quiet
     * for half an hour about an alert nobody was shown.
     *
     * @return null if it was stored, otherwise the sentence for the log.
     */
    private suspend fun rememberAlert(atMs: Long): String? = try {
        settings.setLastDrivingAlertAtMs(atMs)
        null
    } catch (notStored: IOException) {
        "The time of this alert could not be stored ($notStored), so the next report of " +
            "driving may bring it up again."
    }

    /**
     * The settings, or the defaults if the file cannot be read. An unreadable file is never
     * reset (see `buildSettingsStore`) and must not silence the safety net: the alert then runs
     * as it does out of the box, and the log line of each decision says so. Whether a truck is
     * paired is not known then; it is taken to be, as an unreadable truck is taken to be away.
     * The trip controller logs the unreadable file itself, with its stack trace.
     */
    private suspend fun stored(): Stored = try {
        val now = settings.current()
        Stored(now, truckPaired = now.truckAddress != null, problem = null)
    } catch (unreadable: IOException) {
        Stored(
            MiloSettings(),
            truckPaired = true,
            problem =
                "The settings cannot be read ($unreadable). The defaults were used: the " +
                    "alert switched on, the work hours MilO starts out with, a truck taken " +
                    "to be paired, and no earlier alert.",
        )
    }

    private class Stored(val settings: MiloSettings, val truckPaired: Boolean, val problem: String?)

    // What the alert was doing when it failed. Each finishes "The driving alert failed while".
    private companion object {
        const val WITHDRAWING_FOR_A_TRIP = "taking the alert away for a trip that began"
        const val JUDGING_A_REPORT = "dealing with a report of driving"
    }
}
