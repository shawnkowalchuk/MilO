package com.shawnkowalchuk.milo.feature.eventlog

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogFiles
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import java.io.File
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How many entries are read at first, and how many more each press of "Show older" adds. A day
 * of driving writes a few dozen lines per trip, so the first page covers the last few days.
 */
const val EVENT_LOG_PAGE_SIZE = 200

/** How long the list stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * What the event log screen shows.
 *
 * @param entries newest first.
 * @param hasOlder true if the log holds entries older than the ones shown.
 * @param filter the kind of line the list is narrowed to, or null for every line.
 */
data class EventLogUiState(
    val entries: List<EventLogEntry>,
    val hasOlder: Boolean,
    val filter: LogGroup? = null,
)

/**
 * Cuts what was read down to what is shown. One entry more than [wanted] is read on purpose:
 * if it arrives, the log has older entries, and the "Show older" button is offered.
 *
 * @param filter the kind of line the entries were read for, or null for all of them.
 */
fun eventLogPage(
    read: List<EventLogEntry>,
    wanted: Int,
    filter: LogGroup? = null,
): EventLogUiState =
    EventLogUiState(entries = read.take(wanted), hasOlder = read.size > wanted, filter = filter)

/** Why a press of "Share the log" did not lead to the share sheet. */
enum class LogShareProblem {
    /** The file could not be written. The log has a line that says why. */
    COULD_NOT_WRITE,

    /** The phone has no app to share a file with. */
    NO_SHARE_APP,
}

/**
 * The share sheet to open, which only the screen can do: it is opened on MilO's own activity.
 *
 * @param id tells one request from the next, so that each is carried out once.
 */
data class LogShareLaunch(val id: Int, val intent: Intent)

/**
 * Where a press of "Share the log" stands.
 *
 * @param working true while the file is being written. The button waits.
 * @param problem the last press that did not work, until the next one.
 * @param launch the share sheet for the screen to open, or null.
 */
data class LogShare(
    val working: Boolean = false,
    val problem: LogShareProblem? = null,
    val launch: LogShareLaunch? = null,
)

/**
 * The event log screen's link to the stored log.
 *
 * The log is never read whole for the screen. Only the newest entries are, up to a limit that
 * grows when Shawn asks for older ones, so the screen opens as fast with ten thousand entries
 * as with ten. The list follows the log: a line written while the screen is open appears at the
 * top. Narrowed to one kind of line, the same holds for the lines of that kind.
 *
 * @param files writes the whole log to a text file, for sharing.
 * @param shareRequest builds the request that offers that file to Android's share sheet, under
 * a title. A plain function, like the ones for navigation; the screen starts the request.
 * @param clock wall-clock milliseconds, and [zone] the phone's time zone: for the file.
 */
class EventLogViewModel(
    private val eventLog: EventLogRepository,
    private val files: EventLogFiles,
    private val shareRequest: (file: File, title: String) -> Intent,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    /** Which lines are wanted, and how many of them. */
    private data class Wanted(val group: LogGroup?, val limit: Int)

    private val wanted = MutableStateFlow(Wanted(group = null, limit = EVENT_LOG_PAGE_SIZE))
    private val sharing = MutableStateFlow(LogShare())
    private var launches = 0

    /** Null until the log has been read for the first time. */
    // flatMapLatest is how a Flow switches to a new query when the limit or the filter
    // changes. It is marked experimental by the coroutines library and has no stable equivalent.
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<EventLogUiState?> =
        wanted
            .flatMapLatest { (group, limit) ->
                // The filter is part of the query, not a sieve over the lines in hand, so a
                // kind's lines are found however far back they are.
                eventLog
                    .observeNewest(limit + 1, group?.categories())
                    .map { eventLogPage(it, limit, group) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), null)

    /** Where a press of "Share the log" stands. */
    val share: StateFlow<LogShare> = sharing

    fun onShowOlder() {
        wanted.update { it.copy(limit = it.limit + EVENT_LOG_PAGE_SIZE) }
    }

    /**
     * Narrows the list to one kind of line, or with null shows every line again. The list
     * starts over at one page: "older" means something else among other lines.
     */
    fun onFilter(group: LogGroup?) {
        wanted.value = Wanted(group, EVENT_LOG_PAGE_SIZE)
    }

    /**
     * Writes the whole log to a text file and has the share sheet opened with it. One at a
     * time: a second press while the file is being written does nothing.
     *
     * A file that cannot be written is said on the screen and written to the log, whatever was
     * thrown: left alone, the exception would end the process, and the trip service runs in it.
     *
     * @param title what the share sheet and the receiving app are told the file is.
     */
    fun onShare(title: String) {
        if (sharing.value.working) return
        sharing.value = LogShare(working = true)
        viewModelScope.launch {
            sharing.value =
                try {
                    val file = files.write(clock(), zone())
                    LogShare(launch = LogShareLaunch(++launches, shareRequest(file, title)))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val what = "The event log could not be written to a file for sharing"
                    eventLog.add(clock(), EventCategory.ERROR, what, failure.stackTraceToString())
                    LogShare(problem = LogShareProblem.COULD_NOT_WRITE)
                }
        }
    }

    /**
     * The screen has tried to open the share sheet.
     *
     * @param opened false if the phone has no app that takes the file.
     */
    fun onShareLaunched(launch: LogShareLaunch, opened: Boolean) {
        sharing.update { now ->
            if (now.launch?.id != launch.id) {
                now
            } else {
                LogShare(problem = LogShareProblem.NO_SHARE_APP.takeUnless { opened })
            }
        }
    }
}
