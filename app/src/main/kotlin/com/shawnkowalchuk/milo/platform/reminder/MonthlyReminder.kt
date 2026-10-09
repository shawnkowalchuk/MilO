package com.shawnkowalchuk.milo.platform.reminder

import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.core.util.monthSpan
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.report.SentReport
import com.shawnkowalchuk.milo.data.settings.ReminderShown
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.forgetReminderShown
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.platform.clock.askForDailyAlarm
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The monthly reminder: from the reminder day on (the 1st, unless Shawn chose another), once a
 * day, a notification that last month's report has not been sent, until it is recorded as sent
 * or the reminder is switched off. A tap opens the Report screen for that month.
 *
 * **It is decided by what is true when MilO looks, not by an alarm for a date.** Every
 * condition is in [judgeReminder]. MilO looks on three occasions, and the answer is the same
 * however often it looks:
 * - once a day, when the alarm arrives ([onAlarm]), which also asks for tomorrow's;
 * - at every process start ([arm]), which asks for the alarm again as well, because Android
 *   forgets every alarm at a reboot and when an app is force-stopped. A reboot and an update of
 *   MilO both start a new process (the boot and update receiver is what Android starts it for);
 * - whenever MilO comes to the front, the Settings card is changed, or a report is recorded as
 *   sent ([look]).
 *
 * There is no WorkManager in the build (ADR-001, and the address lookup before this), so the
 * daily occasion is one inexact alarm from Android's own alarm service, which needs no
 * permission.
 *
 * **It never touches a trip.** It reads the settings, the list of sent reports and last month's
 * trips, and writes one thing: that the reminder was shown. It is handed neither the trip
 * controller nor a way to write a trip.
 *
 * **A failure in here never reaches the recording.** It runs in the application scope, in the
 * process the trip service runs in. Every piece of its work is run by [keptApart], which writes
 * a failure to the event log and lets nothing out, as the driving alert does.
 *
 * @param show posts the notification for a month, and answers false if nobody can see it.
 * @param withdraw takes the notification away. Safe to call when none is showing.
 * @param sentReports every report recorded as sent, as stored right now.
 * @param tripsStartedBetween the stored trips that started in a span of time, whatever their
 * status: last month's, from which the Business trips are picked by the report's own rule.
 * @param crashFileStore where a failure goes if the event log itself cannot be written.
 * @param clock the time of day in milliseconds: MilO's clock (`AppContainer.clock`).
 * @param phoneClockAgrees whether MilO's time is the phone's right now. Android's alarm service
 * goes by the phone's clock: asked for the real next morning while the phone's date is a day
 * ahead, the alarm would be delivered at once, and again each time it was asked for. So while
 * the two disagree it is asked for on the clock that counts from boot, due after the time that
 * is left until then (ADR-005). `ClockWatch` has it asked for in the normal way once they agree.
 * @param clockOnProbation whether MilO's time was taken from the phone's clock with nothing to
 * check it against, and has not been confirmed since (`TrustedClock.onProbation`). The phone's
 * date may then be set ahead: no reminder is shown, and the alarm is asked for two minutes
 * ahead, so that MilO asks and looks again by a confirmed clock (`askForDailyAlarm`).
 * @param scope the application scope: a look outlives the broadcast or the screen that asked.
 */
class MonthlyReminder(
    private val alarm: ReminderAlarm,
    private val show: (YearMonth) -> Boolean,
    private val withdraw: () -> Unit,
    private val settings: SettingsStore,
    private val sentReports: suspend () -> List<SentReport>,
    private val tripsStartedBetween: suspend (fromMs: Long, untilMs: Long) -> List<Trip>,
    private val eventLog: EventLogRepository,
    private val crashFileStore: CrashFileStore,
    private val clock: () -> Long,
    private val phoneClockAgrees: () -> Boolean,
    private val clockOnProbation: () -> Boolean,
    private val zone: () -> ZoneId,
    private val scope: CoroutineScope,
) {
    /** One look at a time: "was it shown today?" is read and then written. */
    private val oneLookAtATime = Mutex()

    /** What the line last written to the event log said, so that a repeat is not written again. */
    private var logged: List<Any?>? = null

    /** Whether the unreadable settings file has been written to the event log by this process. */
    private var unreadableLogged = false

    /**
     * Process start: asks for the daily alarm, which a reboot or a force stop took away, and
     * looks at the reminder.
     *
     * @param source what prompted the call, in words, for the event log.
     * @param done called when both are finished, whatever happened.
     */
    fun arm(source: String, done: () -> Unit = {}) {
        scope.launch {
            try {
                keptApart("asking for the daily alarm ($source)") { askForAlarm(source) }
                lookKeptApart(source)
            } finally {
                done()
            }
        }
    }

    /**
     * The daily alarm has arrived: asks for tomorrow's, and looks at the reminder.
     *
     * @param done called when both are finished, whatever happened. The receiver keeps its
     * broadcast open until then, so that Android does not freeze a process the alarm started.
     */
    fun onAlarm(done: () -> Unit) {
        scope.launch {
            try {
                keptApart("asking for the next daily alarm") { askForAlarm(DAILY_ALARM) }
                lookKeptApart(DAILY_ALARM)
            } finally {
                done()
            }
        }
    }

    /**
     * Looks at whether the reminder is due, and shows or withdraws it. Safe to call as often
     * as anything asks: at most one reminder is shown for a month on one day.
     *
     * @param source what prompted the look, in words, for the event log.
     */
    fun look(source: String) {
        scope.launch { lookKeptApart(source) }
    }

    private suspend fun askForAlarm(source: String) {
        val nowMs = clock()
        val zoneNow = zone()
        val atMs = nextDailyLookMs(nowMs, zoneNow)
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
        eventLog.add(nowMs, EventCategory.REPORT, alarmAskedText(asked, atMs, zoneNow, source))
    }

    private suspend fun lookKeptApart(source: String) {
        oneLookAtATime.withLock {
            keptApart("looking at whether the reminder is due ($source)") { judge(source) }
        }
    }

    private suspend fun judge(source: String) {
        val stored =
            try {
                settings.current()
            } catch (unreadable: IOException) {
                // Without the settings there is no reminder day and no record of what was
                // shown. Nothing is shown rather than a reminder every time MilO is opened.
                // Written once: the trip controller logs the file itself, with its stack trace.
                if (!unreadableLogged) {
                    unreadableLogged = true
                    val what = "Monthly reminder: the settings cannot be read, so nothing is shown"
                    eventLog.add(clock(), EventCategory.ERROR, what, unreadable.toString())
                }
                return
            }
        val nowMs = clock()
        val zoneNow = zone()
        forgetShownAhead(stored.reminderShown, localDateOf(nowMs, zoneNow), nowMs)
        val span = monthSpan(monthToRemindOf(nowMs, zoneNow), zoneNow)
        // Gathered by the function the home screen's tile uses too (`reminderMoment`), so the
        // notification and the tile cannot disagree.
        val moment =
            reminderMoment(
                nowMs = nowMs,
                zone = zoneNow,
                stored = stored,
                sent = sentReports(),
                lastMonthTrips = tripsStartedBetween(span.fromMs, span.untilMs),
            )
        val verdict = judgeReminder(moment)
        if (verdict.step == ReminderStep.SHOW && clockOnProbation()) {
            // Due by a clock that nothing has confirmed: the phone's date may be set ahead, and
            // the month may not have ended. Not shown and not stored. The alarm that is asked
            // for during a probation has MilO look again by a confirmed clock.
            if (verdict.said(heldBack = true) != logged) {
                eventLog.add(nowMs, EventCategory.REPORT, heldBackText(verdict, source))
                logged = verdict.said(heldBack = true)
            }
            return
        }
        when (verdict.step) {
            ReminderStep.SHOW -> {
                val seen = show(verdict.month)
                val notStored = rememberShown(ReminderShown(verdict.month, verdict.today))
                val line = judgedText(verdict, moment, source, seen, notStored)
                eventLog.add(nowMs, EventCategory.REPORT, line)
                // The next look today finds "already shown today", which the line just written
                // says well enough: it is remembered as written, and not written again.
                logged = verdict.copy(reason = ReminderReason.SHOWN_TODAY).said()
            }

            ReminderStep.WITHDRAW, ReminderStep.LEAVE -> {
                if (verdict.step == ReminderStep.WITHDRAW) withdraw()
                // MilO looks every time it is opened. The line is written when the answer is
                // not the one this process wrote last, and not every time.
                if (verdict.said() != logged) {
                    eventLog.add(nowMs, EventCategory.REPORT, judgedText(verdict, moment, source))
                    logged = verdict.said()
                }
            }
        }
    }

    /**
     * Takes a record of a reminder "shown" on a day after today out of the settings, and says
     * so once. `reminderMoment` already counts it as not shown; left in the file it would count
     * again on the day it names, and that day would get no reminder.
     */
    private suspend fun forgetShownAhead(shown: ReminderShown?, today: LocalDate, nowMs: Long) {
        if (shown == null || !shownAhead(shown, today)) return
        val notForgotten =
            try {
                settings.forgetReminderShown()
                null
            } catch (notStored: IOException) {
                "It could not be taken out of the settings ($notStored)."
            }
        eventLog.add(nowMs, EventCategory.REPORT, shownAheadText(shown, today, notForgotten))
    }

    /**
     * Stores that the reminder was shown, which is what keeps it to once a day. The
     * notification is posted first: if this cannot be stored, the next look shows it again,
     * and the log says so. The other order could leave a day without the reminder it was
     * meant to have.
     *
     * @return null if it was stored, otherwise the sentence for the log.
     */
    private suspend fun rememberShown(shown: ReminderShown): String? = try {
        settings.setReminderShown(shown)
        null
    } catch (notStored: IOException) {
        "That it was shown could not be stored ($notStored), so it may be shown again today."
    }

    /**
     * Runs [work]. A failure is written to the event log as one `ERROR` line with its stack
     * trace, and is not passed on: left alone it would end the process, and the trip service
     * runs in it. Being cancelled is not a failure and is passed on.
     *
     * @param doing what the work is, in words that finish "The monthly reminder failed while".
     */
    private suspend fun keptApart(doing: String, work: suspend () -> Unit) {
        try {
            work()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Every kind of failure, on purpose: see the class comment.
            report(doing, failure)
        }
    }

    /**
     * If the log is what failed, the failure goes to a crash file, which reaches the log at a
     * later start (the same route as the driving alert's).
     */
    private suspend fun report(doing: String, failure: Exception) {
        val message = "The monthly reminder failed while $doing"
        val atMs = clock()
        try {
            eventLog.add(atMs, EventCategory.ERROR, message, failure.stackTraceToString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (logFailure: Exception) {
            val unlogged = IllegalStateException(message, failure)
            unlogged.addSuppressed(logFailure)
            crashFileStore.write(CrashRecord.from(atMs, Thread.currentThread().name, unlogged))
        }
    }

    private companion object {
        /** What the event log calls a look that the daily alarm prompted. */
        const val DAILY_ALARM = "the daily alarm"
    }
}
