package com.shawnkowalchuk.milo.feature.eventlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.eventlog.EventLogEntry
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

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
 */
data class EventLogUiState(val entries: List<EventLogEntry>, val hasOlder: Boolean)

/**
 * Cuts what was read down to what is shown. One entry more than [wanted] is read on purpose:
 * if it arrives, the log has older entries, and the "Show older" button is offered.
 */
fun eventLogPage(read: List<EventLogEntry>, wanted: Int): EventLogUiState =
    EventLogUiState(entries = read.take(wanted), hasOlder = read.size > wanted)

/**
 * The event log screen's link to the stored log.
 *
 * The log is never read whole. Only the newest entries are, up to a limit that grows when Shawn
 * asks for older ones, so the screen opens as fast with ten thousand entries as with ten. The
 * list follows the log: a line written while the screen is open appears at the top.
 */
class EventLogViewModel(private val eventLog: EventLogRepository) : ViewModel() {
    private val wanted = MutableStateFlow(EVENT_LOG_PAGE_SIZE)

    /** Null until the log has been read for the first time. */
    // flatMapLatest is how a Flow switches to a new query when the limit changes. It is marked
    // experimental by the coroutines library and has no stable equivalent.
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<EventLogUiState?> =
        wanted
            .flatMapLatest { limit ->
                eventLog.observeNewest(limit + 1).map { eventLogPage(it, limit) }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), null)

    fun onShowOlder() {
        wanted.update { it + EVENT_LOG_PAGE_SIZE }
    }
}
