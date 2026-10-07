package com.shawnkowalchuk.milo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.NothingRecordedStored
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.setNothingRecordedEnabled
import com.shawnkowalchuk.milo.data.settings.setNothingRecordedTime
import java.io.IOException
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/** What the event log calls the look that a change of the tile prompted. */
private const val CARD_CHANGED = "the check was changed in Settings"

/**
 * What the tile of the daily check shows.
 *
 * @param enabled whether MilO says when a work day has no trip by [checkAt].
 * @param checkAt the time of day from which a work day is checked. Shown only while the check
 * is switched on; kept, and back with the switch.
 * @param couldNotSave true if the last press could not be stored. Said on the tile, where the
 * press was made.
 */
data class NothingRecordedCardState(
    val enabled: Boolean,
    val checkAt: LocalTime,
    val couldNotSave: Boolean,
)

/** The tile for the check as it is stored. Pure, so it is tested without a phone. */
fun nothingRecordedCardState(
    stored: NothingRecordedStored,
    couldNotSave: Boolean,
): NothingRecordedCardState = NothingRecordedCardState(
    enabled = stored.enabled,
    checkAt = stored.checkAt,
    couldNotSave = couldNotSave,
)

/**
 * The Settings screen's tile for the daily "nothing recorded" check: its link to the two stored
 * settings. It keeps no copy of them: what the tile shows is the settings store's own flow, and
 * every press writes to the store.
 *
 * It has a ViewModel of its own, beside the one of the other tiles, like the tile for backup,
 * export and import: that one is at its size limit, and this tile shares nothing with the
 * others but the screen.
 *
 * **A press tells the check,** as a press on the monthly reminder's tile tells the reminder.
 * The check has to ask for its daily alarm at the new time, or take the alarm back, and a
 * notification that is showing must go the moment the check is switched off.
 *
 * [state] is null until the settings have been read, and while they cannot be: the screen
 * itself says that, above the tiles.
 *
 * @param armCheck has the check ask for its alarm and look again, from the stored settings.
 * Its argument says what prompted it, for the event log.
 * @param clock wall-clock milliseconds.
 */
class NothingRecordedViewModel(
    private val settings: SettingsStore,
    private val armCheck: (source: String) -> Unit,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) : ViewModel() {
    private val couldNotSave = MutableStateFlow(false)

    val state: StateFlow<NothingRecordedCardState?> =
        combine(stored(), couldNotSave) { stored, failed ->
            stored?.let { nothingRecordedCardState(it, failed) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(KEEP_WATCHING_MS), null)

    fun onEnabled(enabled: Boolean) = change { settings.setNothingRecordedEnabled(enabled) }

    /** The time picker's answer: an hour from 0 to 23 and a minute. */
    fun onTimePicked(hour: Int, minute: Int) = change {
        settings.setNothingRecordedTime(LocalTime.of(hour, minute))
    }

    /**
     * Stores one press, and then tells the check: stored first, so that the check finds the
     * new value when it reads the settings. A settings file that cannot be written is said on
     * the tile and written to the event log; left alone, the exception would end the process,
     * and the trip service runs in it.
     */
    private fun change(write: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                write()
                couldNotSave.value = false
                armCheck(CARD_CHANGED)
            } catch (notStored: IOException) {
                couldNotSave.value = true
                val what = "The Settings screen could not store a change"
                eventLog.add(clock(), EventCategory.ERROR, what, notStored.stackTraceToString())
            }
        }
    }

    /** The stored check, or null once the settings file has turned out to be unreadable. */
    private fun stored(): Flow<NothingRecordedStored?> = settings.settings
        .map<MiloSettings, NothingRecordedStored?> { it.nothingRecorded }
        .catch { unreadable -> if (unreadable is IOException) emit(null) else throw unreadable }
}
