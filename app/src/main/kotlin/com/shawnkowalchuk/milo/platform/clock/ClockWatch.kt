package com.shawnkowalchuk.milo.platform.clock

import com.shawnkowalchuk.milo.core.clock.ClockNews
import com.shawnkowalchuk.milo.core.clock.TrustedClock
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * While MilO does not believe the phone's clock, it looks at it again this often. Each look is
 * a reading, and the clock itself then sees the change gone, or held long enough to be followed.
 * Well inside `SEEN_AGAIN_WITHIN_MS`, so that a change which really holds is seen to hold.
 *
 * It looks as often while its anchor is on probation (`PROBATION_MS`): a MilO that started
 * while the date was set ahead then sees the date come back without being told, and an anchor
 * that was right is proven at the first look two minutes or more after it was taken.
 *
 * **Only while MilO's process is awake.** The look is a wait inside the process, and Android
 * freezes a process that it started and that has nothing running after about ten seconds (seen
 * on an emulator on 2026-10-09: two and a half minutes after such a start the process was
 * frozen and its anchor still on probation). So it is not what MilO relies on. While the
 * anchor is on probation the two daily alarms are asked for two minutes ahead
 * (`askForDailyAlarm`), and an alarm wakes a frozen process; while MilO does not believe the
 * phone, they count from boot to the real time.
 */
internal const val CLOCK_WATCH_EVERY_MS = 30_000L

/**
 * How long the watch waits for the daily alarms to have been asked for again before it goes on.
 * The eight seconds a manifest broadcast is held open for (`holdUntilHandled`).
 */
internal const val ASKED_AGAIN_WAIT_MS = 8_000L

/**
 * What MilO does about its clock beside telling the time (ADR-006): it writes the one line for
 * each change of the phone's clock it saw and did not follow at once, keeps looking while it
 * does not believe the phone or its own anchor is on probation, and has the two daily alarms
 * asked for again once the clocks agree.
 *
 * **Why the alarms.** Android's alarm service goes by the phone's clock. The moment the date is
 * set a day ahead it delivers both daily alarms, and while the date is ahead an alarm asked for
 * a time on MilO's clock would be delivered at once, again and again. So while the clocks
 * disagree the monthly reminder and the daily check ask for their alarm on the clock that
 * counts from boot, which the date does not move, and this class has them ask in the normal
 * way as soon as the clocks agree again: the new request takes the place of the other. If no
 * process of MilO is there to do that, the alarm that counts from boot is what remains, and it
 * comes at the right time. Their looks, which run on MilO's clock, were right all along.
 *
 * A clock that is set BACK delivers no alarm, so nothing has the two ask: the watch does it
 * itself when it sets such a change aside. Left alone, both alarms would wait for a time of
 * day on a clock MilO does not believe, and come late by as much as the clock was set back.
 *
 * **It never touches a trip,** and a failure in here never reaches the recording: every piece
 * of its work is caught and written to the event log, as in the monthly reminder.
 *
 * Everything is worked off by one coroutine, in the order it happened.
 *
 * @param askAgain the functions that ask for a daily alarm again and look: the monthly
 * reminder's and the daily check's `arm`. Each calls `done` when it has finished.
 * @param crashFileStore where a failure goes if the event log itself cannot be written.
 * @param scope the application scope.
 */
class ClockWatch(
    private val clock: TrustedClock,
    private val askAgain: List<(source: String, done: () -> Unit) -> Unit>,
    private val eventLog: EventLogRepository,
    private val crashFileStore: CrashFileStore,
    private val scope: CoroutineScope,
) {
    private sealed interface Work {
        /** The clock noticed something about the phone's clock. */
        class News(val news: ClockNews) : Work

        /** Android said the phone's clock was set, and MilO has read it since. */
        class PhoneClockSet(val done: () -> Unit) : Work

        /** Time to look at a clock that is not believed, or at an anchor on probation. */
        data object LookAgain : Work

        /** This phone gives no boot number, so the clock is not kept between two processes. */
        data object NoBootNumber : Work
    }

    private val inbox = Channel<Work>(Channel.UNLIMITED)
    private val started = AtomicBoolean(false)

    // Both are touched by the one coroutine that works the inbox off.
    private var lookingAgain = false
    private var askedAgainSinceSet = false

    /**
     * Begins: from here on the clock's news is acted on, what it noticed before included. Called
     * once per process, at its start.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            for (work in inbox) keptApart(doing(work)) { carryOut(work) }
        }
        // The listener only hands the news over: it runs on whichever thread read the clock.
        clock.tellNewsTo { inbox.trySend(Work.News(it)) }
        if (!clock.keptAcrossStarts) inbox.trySend(Work.NoBootNumber)
        // A reading, so that a process Android started while the date is set ahead sees that
        // now and begins to look again, whether or not anything else reads the clock.
        clock.now()
        // An anchor on probation is looked at until it is proven, or found to have been
        // taken while the date was set ahead.
        if (clock.onProbation) inbox.trySend(Work.LookAgain)
    }

    /**
     * Android says the phone's clock was set (`ClockChangeReceiver`). One reading is all it
     * takes: the clock compares, and tells what it found.
     *
     * @param done called when everything the reading led to is finished. The receiver keeps its
     * broadcast open until then, so that Android does not freeze the process in between.
     */
    fun onPhoneClockSet(done: () -> Unit) {
        clock.now()
        inbox.trySend(Work.PhoneClockSet(done))
    }

    private suspend fun carryOut(work: Work) {
        when (work) {
            is Work.News -> carryOut(work.news)

            is Work.PhoneClockSet ->
                try {
                    // A change that MilO follows at once leaves no news, and neither does a
                    // process this broadcast started. The alarms are asked for all the same:
                    // the request is the same one, and harmless to repeat.
                    if (!askedAgainSinceSet && clock.agreesWithPhone()) askForAlarmsAgain(CLOCK_SET)
                    askedAgainSinceSet = false
                } finally {
                    work.done()
                }

            Work.LookAgain -> {
                lookingAgain = false
                // The reading comes first: it is what ends a probation.
                val agrees = clock.agreesWithPhone()
                if (!agrees || clock.onProbation) lookAgainLater()
            }

            Work.NoBootNumber -> eventLog.add(clock.now(), EventCategory.ERROR, NO_BOOT_NUMBER)
        }
    }

    private suspend fun carryOut(news: ClockNews) {
        // Kept apart from what follows: a log that cannot be written must not leave the
        // alarms unasked for.
        clockNewsText(news)?.let { line ->
            keptApart("writing down what the phone's clock did") {
                eventLog.add(clock.now(), EventCategory.PROCESS, line)
            }
        }
        when (news) {
            is ClockNews.SetAside -> {
                lookAgainLater()
                // Set ahead, Android delivers both alarms and each asks again by itself. Set
                // back, nothing is delivered: see the class comment.
                if (news.offsetMs < 0) askForAlarmsAgain(CLOCK_SET_BACK)
            }

            is ClockNews.ChangedAgain -> Unit

            is ClockNews.CameBack -> askForAlarmsAgain(CLOCK_BACK)

            is ClockNews.Followed -> askForAlarmsAgain(CLOCK_FOLLOWED)

            // Until this reading MilO went by a date that was set ahead: both alarms were
            // asked for by it and both checks looked by it. Settled like any change that
            // came back.
            is ClockNews.StartedAhead -> askForAlarmsAgain(CLOCK_BACK)
        }
    }

    /** One look is waiting at a time, however many changes are seen. */
    private fun lookAgainLater() {
        if (lookingAgain) return
        lookingAgain = true
        scope.launch {
            delay(CLOCK_WATCH_EVERY_MS)
            inbox.trySend(Work.LookAgain)
        }
    }

    /**
     * Has both daily alarms asked for again, and both checks look, and waits for them, but not
     * for ever: a look that hangs must not hold up the next change of the clock.
     */
    private suspend fun askForAlarmsAgain(source: String) {
        askedAgainSinceSet = true
        val asked =
            askAgain.map { ask ->
                CompletableDeferred<Unit>().also { done -> ask(source) { done.complete(Unit) } }
            }
        withTimeoutOrNull(ASKED_AGAIN_WAIT_MS) { asked.awaitAll() }
    }

    private fun doing(work: Work): String = when (work) {
        is Work.News -> "acting on what the phone's clock did"
        is Work.PhoneClockSet -> "looking at the phone's clock after it was set"
        Work.LookAgain -> "looking at the phone's clock again"
        Work.NoBootNumber -> "writing down that the phone gives no boot number"
    }

    /**
     * Runs [work]. A failure is written to the event log as one `ERROR` line with its stack
     * trace, and is not passed on: left alone it would end the process, and the trip service
     * runs in it. Being cancelled is not a failure and is passed on.
     *
     * @param doing what the work is, in words that finish "MilO's clock watch failed while".
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

    /** If the log is what failed, the failure goes to a crash file, as the reminder's does. */
    private suspend fun report(doing: String, failure: Exception) {
        val message = "MilO's clock watch failed while $doing"
        val atMs = clock.now()
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
}
