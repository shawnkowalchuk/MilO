package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.platform.clock.askForDailyAlarm
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.io.IOException
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The "nothing recorded" check: on a work day, if no trip has been recorded by the time Shawn
 * set, MilO says so with a notification. It catches the case where trip detection has quietly
 * stopped (Autostart switched off by an update of the phone, a permission taken back, the truck
 * no longer paired), which nothing else can report: a MilO that is never woken cannot say that
 * it was not.
 *
 * **It is built like the monthly reminder** (`platform/reminder/MonthlyReminder.kt`). It is
 * decided by what is true when MilO looks, not by an alarm for a time: every condition is in
 * [judgeNothingRecorded]. MilO looks on four occasions, and the answer is the same however
 * often it looks:
 * - once a day, when the alarm arrives ([onAlarm]), which also asks for the next one;
 * - at every process start, and when the check is changed in Settings ([arm]), which asks for
 *   the alarm again as well, because Android forgets every alarm at a reboot and when an app is
 *   force-stopped;
 * - when the stored work schedule changes, which the check notices by itself, and which asks
 *   for the alarm again too: the alarm's time is worked out from the schedule;
 * - whenever MilO comes to the front ([look]).
 *
 * What one look does, and the wait before it notifies, is in [NothingRecordedLook]. A trip that
 * starts being recorded, however it started, takes the notification away at once.
 *
 * **It never touches a trip.** It reads the settings and the stored trips, and writes one
 * thing: the day the notification was shown. It is handed neither the trip controller nor a way
 * to write a trip; of the controller it gets what is published and "tell me when you have
 * caught up".
 *
 * **A failure in here never reaches the recording.** It runs in the application scope, in the
 * process the trip service runs in. Every piece of its work is run by
 * [NothingRecordedFailures.keptApart], which writes a failure to the event log and lets nothing
 * out.
 *
 * @param show posts the notification, and answers false if nobody can see it.
 * @param withdraw takes the notification away. Safe to call when none is showing.
 * @param tripsStartedBetween the stored trips that started in a span of time, whatever their
 * status: today's.
 * @param openTrip the trip that is open in storage right now, or null. Asked beside today's
 * trips because a trip that began before midnight is being recorded all the same.
 * @param tripActivity what the trip controller publishes about the trip in progress. It is what
 * says that a trip has begun.
 * @param whenTripsCaughtUp runs its argument once the trip controller has dealt with everything
 * handed to it so far (`TripController.whenCaughtUp`). A look can be the first thing a new
 * process does, and a trip the last process left open long ago is closed by the controller's
 * first look at storage; asked before that, the stale row would count as a trip being recorded.
 * It does not say that a trip which is being started is stored ([TRIP_START_WAIT_MS]).
 * @param crashFileStore where a failure goes if the event log itself cannot be written.
 * @param clock the time of day in milliseconds: MilO's clock (`AppContainer.clock`).
 * @param phoneClockAgrees whether MilO's time is the phone's right now. Android's alarm service
 * goes by the phone's clock: asked for the real next noon while the phone's date is a day
 * ahead, the alarm would be delivered at once, and again each time it was asked for. So while
 * the two disagree it is asked for on the clock that counts from boot, due after the time that
 * is left until then (ADR-005). `ClockWatch` has it asked for in the normal way once they agree.
 * @param clockOnProbation whether MilO's time was taken from the phone's clock with nothing to
 * check it against, and has not been confirmed since (`TrustedClock.onProbation`). The phone's
 * date may then be set ahead: no notification is shown, and the alarm is asked for two minutes
 * ahead, so that MilO asks and looks again by a confirmed clock (`askForDailyAlarm`).
 * @param scope the application scope: a look outlives the broadcast or the screen that asked.
 */
class NothingRecordedCheck(
    private val alarm: NothingRecordedAlarm,
    show: () -> Boolean,
    withdraw: () -> Unit,
    private val settings: SettingsStore,
    tripsStartedBetween: suspend (fromMs: Long, untilMs: Long) -> List<Trip>,
    openTrip: suspend () -> Trip?,
    tripActivity: StateFlow<TripActivity>,
    whenTripsCaughtUp: (done: () -> Unit) -> Unit,
    private val eventLog: EventLogRepository,
    crashFileStore: CrashFileStore,
    private val clock: () -> Long,
    private val phoneClockAgrees: () -> Boolean,
    private val clockOnProbation: () -> Boolean,
    private val zone: () -> ZoneId,
    private val scope: CoroutineScope,
) {
    private val failures = NothingRecordedFailures(eventLog, crashFileStore, clock)

    private val oneLook =
        NothingRecordedLook(
            show = show,
            withdraw = withdraw,
            settings = settings,
            tripsStartedBetween = tripsStartedBetween,
            openTrip = openTrip,
            tripActivity = tripActivity,
            whenTripsCaughtUp = whenTripsCaughtUp,
            eventLog = eventLog,
            clock = clock,
            clockOnProbation = clockOnProbation,
            zone = zone,
        )

    init {
        scope.launch {
            tripActivity
                .map { it.trip != null }
                .distinctUntilChanged()
                .filter { recording -> recording }
                .collect { failures.keptApart(WITHDRAWING_FOR_A_TRIP) { withdraw() } }
        }
        scope.launch { failures.keptApart(WATCHING_THE_SCHEDULE) { followTheSchedule() } }
    }

    /**
     * Process start, or the check or the work schedule was changed: asks for the daily alarm
     * (or takes it back, if the check is switched off) and looks.
     *
     * @param source what prompted the call, in words, for the event log.
     * @param done called when both are finished, whatever happened.
     */
    fun arm(source: String, done: () -> Unit = {}) {
        scope.launch {
            try {
                failures.keptApart("asking for the daily alarm ($source)") { askForAlarm(source) }
                lookKeptApart(source)
            } finally {
                done()
            }
        }
    }

    /**
     * The daily alarm has arrived: asks for the next one, and looks.
     *
     * @param done called when both are finished, whatever happened. The receiver keeps its
     * broadcast open until then, so that Android does not freeze a process the alarm started.
     */
    fun onAlarm(done: () -> Unit) {
        scope.launch {
            try {
                failures.keptApart("asking for the next daily alarm") { askForAlarm(DAILY_ALARM) }
                lookKeptApart(DAILY_ALARM)
            } finally {
                done()
            }
        }
    }

    /**
     * Asks whether a trip has been recorded today, and shows or withdraws the notification.
     * Safe to call as often as anything asks: a day brings one notification at most.
     *
     * @param source what prompted the look, in words, for the event log.
     */
    fun look(source: String) {
        scope.launch { lookKeptApart(source) }
    }

    /**
     * The alarm goes by the time Shawn set and by the schedule, so both are read. If they
     * cannot be, the alarm is asked for as out of the box: the daily look must not stop because
     * of a file, and the look itself then says that nothing can be checked.
     */
    private suspend fun askForAlarm(source: String) {
        val stored =
            try {
                settings.current()
            } catch (unreadable: IOException) {
                MiloSettings()
            }
        val nowMs = clock()
        if (!stored.nothingRecorded.enabled) {
            // Switched off, MilO has no reason to be woken once a day.
            alarm.cancel()
            eventLog.add(nowMs, EventCategory.TRIP, noAlarmText(source))
            return
        }
        val zoneNow = zone()
        val atMs =
            nextNothingRecordedLookMs(
                nowMs,
                zoneNow,
                stored.nothingRecorded.checkAt,
                stored.schedule,
            )
        // In one of three ways, by how MilO's clock stands to the phone's: see there.
        val asked =
            askForDailyAlarm(
                atMs = atMs,
                nowMs = nowMs,
                phoneClockAgrees = phoneClockAgrees,
                clockOnProbation = clockOnProbation,
                setFor = alarm::setFor,
                setAfter = alarm::setAfter,
            )
        eventLog.add(nowMs, EventCategory.TRIP, alarmAskedText(asked, atMs, zoneNow, source))
    }

    /**
     * Has the check asked for its alarm again, and looked, whenever the stored work schedule
     * changes: on the Settings screen, or by an import. The alarm was asked for at the moment
     * the next day is checked from, which is the later of the time set and that day's start,
     * so a day whose start is moved would otherwise keep the old moment until MilO next
     * starts; and a notification must go when its day is switched off in the schedule.
     *
     * The first value is the schedule as it stood when the watch began, which the process
     * start has dealt with. A settings file that cannot be read ends the watch quietly: the
     * look says that itself, once.
     */
    private suspend fun followTheSchedule() {
        settings.settings
            .map { it.schedule }
            .distinctUntilChanged()
            .drop(1)
            .catch { unreadable -> if (unreadable !is IOException) throw unreadable }
            .collect { arm(SCHEDULE_CHANGED) }
    }

    private suspend fun lookKeptApart(source: String) {
        failures.keptApart("asking whether a trip has been recorded today ($source)") {
            oneLook.look(source)
        }
    }

    private companion object {
        /** What the event log calls a look that the daily alarm prompted. */
        const val DAILY_ALARM = "the daily alarm"

        /** What the event log calls an asking that a change of the work schedule prompted. */
        const val SCHEDULE_CHANGED = "the work schedule was changed"

        /** Each finishes "The nothing-recorded check failed while". */
        const val WITHDRAWING_FOR_A_TRIP = "taking the notification away for a trip that began"
        const val WATCHING_THE_SCHEDULE = "watching the work schedule for a change"
    }
}
