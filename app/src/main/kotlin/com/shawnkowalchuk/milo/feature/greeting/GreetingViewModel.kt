package com.shawnkowalchuk.milo.feature.greeting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.clearGreetingsUnfinished
import com.shawnkowalchuk.milo.data.settings.countGreetingBegun
import java.io.IOException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The greeting's safety catch (ADR-005). The mascot is drawn by native code, and a failure
 * there ends the app with nothing to catch. MilO greets when it is opened, so without this a
 * phone the drawing fails on would lose MilO at every start.
 *
 * **Every greeting is counted before the drawing begins, and the count is cleared when the
 * mascot has been seen.** A start that finds [MOST_UNFINISHED] greetings counted and none seen
 * does not greet, and neither does any start after it: the app can die of its greeting twice,
 * and not a third time. Two and not one, because a greeting also stays counted when Android
 * ends MilO in that moment for a reason of its own, and one such accident must not cost the
 * greeting for good.
 *
 * **Nothing brings a greeting back that was switched off this way** short of clearing MilO's
 * storage. The Log says that it is off, once for every start.
 *
 * @param clock wall-clock milliseconds, for the event log.
 */
class GreetingViewModel(
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) : ViewModel() {
    /**
     * Whether the mascot may be drawn in this start, or null until the settings have been read.
     * Read once and kept: this start's own count, made in [starting], must not end its greeting.
     * Settings that cannot be read give no greeting.
     */
    val trusted: StateFlow<Boolean?> =
        flow<Boolean?> { emit(readTrusted()) }
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    /**
     * Called before the mascot's drawing begins, and returns when the count is stored. A count
     * that cannot be stored does not hold the greeting back: the catch is then not set for this
     * one start, and the Log says so.
     */
    suspend fun starting() {
        try {
            settings.countGreetingBegun()
        } catch (notStored: IOException) {
            log("MilO could not count the greeting it is about to show", notStored)
        }
    }

    /** The mascot has been seen: the drawing works here. */
    fun onShown() {
        viewModelScope.launch {
            try {
                settings.clearGreetingsUnfinished()
            } catch (notStored: IOException) {
                log("MilO could not store that the greeting was shown", notStored)
            }
        }
    }

    private suspend fun readTrusted(): Boolean = try {
        val unfinished = settings.current().greetingsUnfinished
        if (unfinished >= MOST_UNFINISHED) {
            val what = "The greeting is off: $unfinished in a row began and never showed the mascot"
            eventLog.add(clock(), EventCategory.ERROR, what, null)
        }
        unfinished < MOST_UNFINISHED
    } catch (notRead: IOException) {
        log("MilO could not read whether the greeting may be shown; it is not shown", notRead)
        false
    }

    private suspend fun log(what: String, why: IOException) {
        eventLog.add(clock(), EventCategory.ERROR, what, why.stackTraceToString())
    }

    private companion object {
        const val MOST_UNFINISHED = 2
    }
}
