package com.shawnkowalchuk.milo.platform.car

import android.content.Context
import com.shawnkowalchuk.milo.data.crash.CrashFileStore
import com.shawnkowalchuk.milo.data.crash.CrashRecord
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import java.util.concurrent.Executor
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Where the reports of Android Auto's connection come from, as far as the app-wide watch needs
 * it. An interface so that the watch can be tested without Android; the real one is
 * [CarConnectionSource].
 */
fun interface AndroidAutoSource {
    /**
     * Starts reporting, for as long as the process lives. Called once, on the main thread.
     *
     * @param onReport called on the main thread: once with what Android Auto says now, and
     * then for each change. The raw value is for the event log.
     */
    fun watch(onReport: (connected: Boolean, rawType: Int?) -> Unit)
}

/**
 * The app-wide watch on Android Auto, for the event log: one line for each change of Android
 * Auto's connection while no trip is being recorded.
 *
 * While a trip is being recorded the trip service has a watch of its own, and the trip
 * controller writes what that one reports. Outside a trip nothing watched, so a connection made
 * then left no trace. This class watches for the life of the process and writes a line only
 * when no trip is being recorded ([judgeAndroidAutoReport]), so no change is written twice.
 *
 * **It only writes lines.** It is given neither the trip controller nor the trip storage: all
 * it can do is ask whether a trip is being recorded. Nothing here can start, end or hold a
 * trip, and the trip rules and the trip service do not know that it exists.
 *
 * **It sees only what happens while MilO's process is alive.** `CarConnection` reports to a
 * process that is observing it. A connection that was made and ended while MilO was not running
 * leaves no line. One that is still there when MilO starts is written as that start's first
 * reading, without the time it was made. And it is told of a change only when Android Auto
 * announces one: unlike the trip service's watch it never asks again by itself, so an
 * announcement that does not arrive is a line that is not written.
 *
 * **A failure in here never ends the process,** which is the process the trip service runs in.
 * Every piece of MilO's own work here is caught and written to the event log as one `ERROR`
 * line; if the log is what failed, it goes to a crash file, as in the address lookup and the
 * driving alert. If that cannot be written either, the failure is counted and said with the
 * next line that can be written. What is not MilO's own work cannot be caught here: an
 * exception thrown inside the Car App Library's own callbacks, which run on the main thread
 * during every recorded trip already.
 *
 * @param mainThread runs work on the main thread, which the Car App Library requires.
 * @param tripBeingRecorded whether the trip controller shows a trip as being recorded.
 * @param crashFileStore where a failure goes if the event log itself cannot be written.
 * @param clock wall-clock milliseconds.
 * @param scope the application scope. One coroutine writes the lines, in the order the reports
 * arrived, for the life of the process.
 */
class AndroidAutoLog(
    private val source: AndroidAutoSource,
    private val mainThread: Executor,
    private val tripBeingRecorded: () -> Boolean,
    private val eventLog: EventLogRepository,
    private val crashFileStore: CrashFileStore,
    private val clock: () -> Long,
    scope: CoroutineScope,
) {
    private val pending = Channel<Pending>(Channel.UNLIMITED)

    // Touched on the main thread only.
    private var started = false
    private var lastKnown: Boolean? = null

    /** Failures that could be written nowhere. Touched by the writing coroutine only. */
    private var unrecorded = 0

    init {
        scope.launch { for (entry in pending) write(entry) }
    }

    /**
     * Begins watching. `MiloApplication` calls it once per process; a second call does nothing.
     * Safe to call from any thread, and it returns at once: the watch itself begins on the main
     * thread.
     */
    fun start() {
        keptApart(HANDING_OVER) {
            mainThread.execute {
                keptApart(STARTING) {
                    if (started) return@keptApart
                    started = true
                    source.watch(::onReport)
                }
            }
        }
    }

    /**
     * One report, on the main thread. Whether it becomes a line is decided here, at the moment
     * it arrives; the line is dated then as well, and written by the coroutine.
     */
    private fun onReport(connected: Boolean, rawType: Int?) {
        keptApart(TAKING_A_REPORT) {
            val note = judgeAndroidAutoReport(lastKnown, connected, tripBeingRecorded())
            lastKnown = connected
            val text = androidAutoLogText(note, connected, rawType) ?: return@keptApart
            pending.trySend(Pending.Line(clock(), text))
        }
    }

    /** Runs [work]. A failure is handed to the writing coroutine and goes no further. */
    private fun keptApart(doing: String, work: () -> Unit) {
        try {
            work()
        } catch (failure: Exception) {
            // Every kind of failure, on purpose: see the class comment.
            pending.trySend(Pending.Failure(clock(), doing, failure))
        }
    }

    private suspend fun write(entry: Pending) {
        when (entry) {
            is Pending.Failure -> report(entry.atMs, entry.doing, entry.failure)

            is Pending.Line ->
                try {
                    eventLog.add(entry.atMs, EventCategory.ANDROID_AUTO, entry.text, lostBefore())
                    unrecorded = 0
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    report(entry.atMs, WRITING_A_LINE, failure)
                }
        }
    }

    /**
     * Writes a failure to the event log. If the log is what failed, the failure goes to a
     * crash file, which reaches the log at a later start (the route of the address lookup and
     * the driving alert).
     */
    private suspend fun report(atMs: Long, doing: String, failure: Exception) {
        val message = androidAutoLogFailedText(doing)
        try {
            val detail = listOfNotNull(lostBefore(), failure.stackTraceToString())
            eventLog.add(atMs, EventCategory.ERROR, message, detail.joinToString("\n"))
            unrecorded = 0
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (logFailure: Exception) {
            val unlogged = IllegalStateException(message, failure)
            unlogged.addSuppressed(logFailure)
            toCrashFile(atMs, unlogged)
        }
    }

    /**
     * The last resort. The classes this one is modelled on let a failure of the crash file out,
     * which ends the process. This one does not: it only keeps a log, and a line of the log is
     * never worth the process the trip service runs in.
     */
    private fun toCrashFile(atMs: Long, unlogged: Exception) {
        try {
            crashFileStore.write(CrashRecord.from(atMs, Thread.currentThread().name, unlogged))
        } catch (nowhere: Exception) {
            // Neither the event log nor the crash folder can be written, so there is nowhere
            // left to put this. It is counted, and the next line that can be written says so.
            unlogged.addSuppressed(nowhere)
            unrecorded++
        }
    }

    /** What the next line says about failures that could be written nowhere, if there are any. */
    private fun lostBefore(): String? = if (unrecorded == 0) {
        null
    } else {
        "Before this line, $unrecorded failure(s) of this watch could be written neither to " +
            "the event log nor to a crash file."
    }

    /** What waits to be written. Dated when it happened, not when it is written. */
    private sealed interface Pending {
        class Line(val atMs: Long, val text: String) : Pending

        class Failure(val atMs: Long, val doing: String, val failure: Exception) : Pending
    }

    // What the watch was doing when it failed. Each finishes "… failed while".
    private companion object {
        const val HANDING_OVER = "handing its start to the main thread"
        const val STARTING = "starting to watch"
        const val TAKING_A_REPORT = "taking a report"
        const val WRITING_A_LINE = "writing a line to the event log"
    }
}

/**
 * Android Auto's connection as the Car App Library's `CarConnection` reports it, through the
 * [AndroidAutoWatcher] the trip service uses as well. Each of the two has a watcher of its own.
 */
private class CarConnectionSource(private val context: Context) : AndroidAutoSource {
    /** Held for the life of the process: this watch is never stopped. */
    private var watcher: AndroidAutoWatcher? = null

    override fun watch(onReport: (connected: Boolean, rawType: Int?) -> Unit) {
        watcher = AndroidAutoWatcher(context, onReport).also { it.start() }
    }
}

/**
 * Builds the watch on the phone's own Android Auto. Called once, by the `AppContainer`'s part
 * for it (`CarObjects`).
 */
fun buildAndroidAutoLog(
    context: Context,
    tripBeingRecorded: () -> Boolean,
    eventLog: EventLogRepository,
    crashFileStore: CrashFileStore,
    clock: () -> Long,
    scope: CoroutineScope,
): AndroidAutoLog {
    val appContext = context.applicationContext
    return AndroidAutoLog(
        source = CarConnectionSource(appContext),
        mainThread = appContext.mainExecutor,
        tripBeingRecorded = tripBeingRecorded,
        eventLog = eventLog,
        crashFileStore = crashFileStore,
        clock = clock,
        scope = scope,
    )
}
