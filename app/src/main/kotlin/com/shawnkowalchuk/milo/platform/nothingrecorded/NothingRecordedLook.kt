package com.shawnkowalchuk.milo.platform.nothingrecorded

import com.shawnkowalchuk.milo.core.util.daySpan
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.forgetNothingRecordedShownOn
import com.shawnkowalchuk.milo.data.settings.setNothingRecordedShownOn
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How long a look waits for the trip controller to have dealt with what it was handed. Together
 * with [TRIP_START_WAIT_MS] it is under the eight seconds a manifest broadcast is kept open
 * for, so that a look the daily alarm prompted is done before Android may freeze the process
 * again, also when the controller does not answer at all.
 */
internal const val CAUGHT_UP_WAIT_MS = 5_000L

/**
 * How long a look that found no trip waits before it looks once more, and only then notifies.
 *
 * "The controller has caught up" does not mean that a trip which is being started is stored.
 * The controller opens a trip only once the trip service has reached the foreground (ADR-002,
 * "the service comes first"): until then it has asked Android for the service, holds no trip
 * row, publishes none, and has nothing left to deal with. MilO opened beside a connected truck,
 * and a process the truck's connect started, are in that state for a moment, and a look that
 * fell into it said "no trip recorded today" a moment before the trip began. Nothing the check
 * is handed says that a trip is on its way, so it gives one this long to arrive.
 *
 * The length is a judgement, not a measurement: on an emulator Android has brought the process
 * and the trip service back within a second, and what the phone takes has not been measured
 * (device check NR-15). A service that is slower still has its trip take the notification away.
 */
internal const val TRIP_START_WAIT_MS = 2_500L

/**
 * One look of the "nothing recorded" check ([NothingRecordedCheck]): it reads the settings and
 * the trips, has [judgeNothingRecorded] decide, writes the line, and shows or withdraws the
 * notification. When it looks, and the daily alarm, are the check's own business.
 *
 * **A notification is posted only after two looks that both found no trip,**
 * [TRIP_START_WAIT_MS] apart: see there. Every other answer is acted on at once.
 *
 * **No notification by a clock that is on probation** (ADR-006). A MilO that started with
 * nothing to check the phone's clock against may have started in the seconds the date is set
 * ahead, and "today" is then tomorrow, a day with no trip. Such a look says so in the log,
 * shows nothing and stores nothing. The check asks for its alarm two minutes ahead while the
 * probation lasts (`askForDailyAlarm`), and the look that follows it decides by a clock that
 * has been confirmed.
 *
 * The parameters are the ones [NothingRecordedCheck] is handed, and are described there.
 */
internal class NothingRecordedLook(
    private val show: () -> Boolean,
    private val withdraw: () -> Unit,
    private val settings: SettingsStore,
    private val tripsStartedBetween: suspend (fromMs: Long, untilMs: Long) -> List<Trip>,
    private val openTrip: suspend () -> Trip?,
    private val tripActivity: StateFlow<TripActivity>,
    private val whenTripsCaughtUp: (done: () -> Unit) -> Unit,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
    private val clockOnProbation: () -> Boolean,
    private val zone: () -> ZoneId,
) {
    /**
     * One look at a time: "was it shown today?" is read and then written, and two looks that
     * both read "no" would both notify. The wait before a notification is inside the lock, so
     * a look that arrives during it finds the day's notification already stored.
     */
    private val oneLookAtATime = Mutex()

    /** Whether the unreadable settings file has been written to the event log by this process. */
    private var unreadableLogged = false

    /** What one reading of the settings and the trips came to. */
    private class Asked(val moment: NothingRecordedMoment, val verdict: NothingRecordedVerdict)

    /**
     * Asks whether a trip has been recorded today, and shows or withdraws the notification.
     *
     * @param source what prompted the look, in words, for the event log.
     */
    suspend fun look(source: String) {
        val caughtUp = tripsCaughtUp()
        oneLookAtATime.withLock {
            val first = ask() ?: return
            if (first.verdict.step != NothingRecordedStep.SHOW) {
                carryOut(first, source, unanswered = !caughtUp)
                return
            }
            // No trip, and a notification would be due: a trip may be on its way.
            delay(TRIP_START_WAIT_MS)
            // The service may have reported in during the wait, and the controller may be in
            // the middle of storing its trip. A controller that did not answer the first time
            // is not waited for again: the two waits together must stay inside the broadcast.
            val caughtUpAgain = caughtUp && tripsCaughtUp()
            val second = ask() ?: return
            val tripCame = second.verdict.reason == NothingRecordedReason.TRIP_RECORDED
            carryOut(second, source, unanswered = !caughtUpAgain, afterWaitingForATrip = tripCame)
        }
    }

    /**
     * Waits until the trip controller has dealt with everything handed to it, so that a trip
     * the last process left open long ago is closed, and one whose service has just reported
     * in is stored. It does not wait for ever: this check exists for the day something in MilO
     * has stopped working, and must not depend on all of it working.
     *
     * @return false if the controller had not answered within [CAUGHT_UP_WAIT_MS].
     */
    private suspend fun tripsCaughtUp(): Boolean {
        val caughtUp = CompletableDeferred<Unit>()
        whenTripsCaughtUp { caughtUp.complete(Unit) }
        return withTimeoutOrNull(CAUGHT_UP_WAIT_MS) { caughtUp.await() } != null
    }

    /** Reads what the decision goes by, and decides. Null if the settings cannot be read. */
    private suspend fun ask(): Asked? {
        val stored =
            try {
                settings.current()
            } catch (unreadable: IOException) {
                // Without the settings there is no schedule, no time and no record of what was
                // shown. Nothing is shown rather than a notification every time MilO is opened.
                // Written once: the trip controller logs the file itself, with its stack trace.
                if (!unreadableLogged) {
                    unreadableLogged = true
                    val what = "Nothing-recorded check: the settings cannot be read, so nothing " +
                        "is checked"
                    eventLog.add(clock(), EventCategory.ERROR, what, unreadable.toString())
                }
                return null
            }
        val nowMs = clock()
        val zoneNow = zone()
        val day = localDateOf(nowMs, zoneNow)
        val shownOn = stored.nothingRecorded.shownOn?.takeUnless {
            forgottenIfAhead(it, day, nowMs)
        }
        val today = daySpan(day, zoneNow)
        val trips = tripsStartedBetween(today.fromMs, today.untilMs) + listOfNotNull(openTrip())
        val moment =
            NothingRecordedMoment(
                nowMs = nowMs,
                zone = zoneNow,
                enabled = stored.nothingRecorded.enabled,
                checkAt = stored.nothingRecorded.checkAt,
                schedule = stored.schedule,
                trips = trips.distinctBy { it.id },
                shownOn = shownOn,
            )
        return Asked(moment, judgeNothingRecorded(moment))
    }

    /**
     * A stored "shown on" that names a day after today is taken out of the settings, with one
     * line. Nothing can have been shown tomorrow: it was stored while the phone's date was set
     * ahead (until 2026-10-07 MilO went by the phone's clock, ADR-006). It counts as not shown,
     * and left in the file it would count again on the day it names: the one notification of
     * that day, used up before the day began.
     *
     * @return true if [shownOn] lies after [today], and so does not count.
     */
    private suspend fun forgottenIfAhead(
        shownOn: LocalDate,
        today: LocalDate,
        nowMs: Long,
    ): Boolean {
        if (!shownOn.isAfter(today)) return false
        val notForgotten =
            try {
                settings.forgetNothingRecordedShownOn()
                null
            } catch (notStored: IOException) {
                "It could not be taken out of the settings ($notStored)."
            }
        eventLog.add(nowMs, EventCategory.TRIP, shownAheadText(shownOn, today, notForgotten))
        return true
    }

    /** Writes the line for what was decided, and does it. */
    private suspend fun carryOut(
        asked: Asked,
        source: String,
        unanswered: Boolean,
        afterWaitingForATrip: Boolean = false,
    ) {
        val moment = asked.moment
        val verdict = asked.verdict
        if (verdict.step == NothingRecordedStep.SHOW && clockOnProbation()) {
            // Due by a clock that nothing has confirmed: see the class comment.
            val held = heldBackText(verdict, source)
            eventLog.add(moment.nowMs, EventCategory.TRIP, held, factsText(moment, verdict))
            return
        }
        val line = judgedText(verdict, moment, source, unanswered, afterWaitingForATrip)
        eventLog.add(moment.nowMs, EventCategory.TRIP, line, factsText(moment, verdict))
        when (verdict.step) {
            NothingRecordedStep.SHOW -> {
                val seen = showAndCheck()
                val notStored = rememberShown(verdict.today)
                eventLog.add(moment.nowMs, EventCategory.TRIP, shownText(verdict, seen, notStored))
            }

            NothingRecordedStep.WITHDRAW -> withdraw()

            NothingRecordedStep.LEAVE -> Unit
        }
    }

    /**
     * Posts the notification, and takes it straight back if a trip began while the trips were
     * being read: the withdrawal that follows a trip start may have run before it was posted.
     * After the wait in [look] this is the last, narrow guard, not the usual way.
     */
    private fun showAndCheck(): Boolean {
        val seen = show()
        if (tripActivity.value.trip != null) withdraw()
        return seen
    }

    /**
     * Stores the day the notification was shown, which is what keeps it to one a day. The
     * notification is posted first: if this cannot be stored, the next look shows it again,
     * and the log says so. The other order could leave a day without the notification it was
     * meant to have.
     *
     * @return null if it was stored, otherwise the sentence for the log.
     */
    private suspend fun rememberShown(day: LocalDate): String? = try {
        settings.setNothingRecordedShownOn(day)
        null
    } catch (notStored: IOException) {
        "That it was shown could not be stored ($notStored), so it may be shown again today."
    }
}
