package com.shawnkowalchuk.milo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.GRACE_PERIOD_CHOICE
import com.shawnkowalchuk.milo.data.settings.MINIMUM_TRIP_DISTANCE_CHOICE
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.SteppedChoice
import com.shawnkowalchuk.milo.platform.trip.OwnTripSound
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * The Settings screen's link to the stored settings. It keeps no copy of them: what the screen
 * shows is the settings store's own flow, and every press writes to the store.
 *
 * Nothing here tells the trip engine about a change. The trip controller reads the grace period
 * and the minimum distance at every trigger, and the trip service reads the sound at every trip
 * start, so a stored value is simply the one in force from the next event on. The work schedule
 * is read the same way, at the moment a trip is closed, to sort that trip; no stored trip is
 * sorted again because the schedule changed.
 *
 * @param ownSound copies and checks a picked audio file, and goes back to the built-in sound.
 * @param playSound plays the trip-start sound the way a trip start does, given the stored
 * custom sound or null for the built-in one. Called on the main thread.
 * @param clock wall-clock milliseconds.
 */
class SettingsViewModel(
    private val settings: SettingsStore,
    private val ownSound: OwnTripSound,
    private val playSound: (customSoundUri: String?) -> Unit,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) : ViewModel() {
    /**
     * What the screen shows that is not a stored setting.
     *
     * @param problemDay the day of the schedule the press that did not work was about, if it
     * was about one: the screen says under that day what went wrong.
     */
    private data class Passing(
        val copyingSound: Boolean = false,
        val problem: SettingsProblem? = null,
        val problemDay: DayOfWeek? = null,
    )

    private val passing = MutableStateFlow(Passing())

    /**
     * One change at a time. A step reads the stored value and writes the next one, and two
     * quick presses must not both read the same value.
     */
    private val oneChangeAtATime = Mutex()

    val state: StateFlow<SettingsUiState> =
        combine(storedSettings(), passing) { stored, now ->
            if (stored == null) {
                SettingsUiState.Unreadable
            } else {
                settingsUiState(stored, now.copyingSound, now.problem, now.problemDay)
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(KEEP_WATCHING_MS),
            SettingsUiState.Reading,
        )

    fun onGraceStep(longer: Boolean) = change {
        settings.setGracePeriodSeconds(GRACE_PERIOD_CHOICE.step(it.gracePeriodSeconds, longer))
    }

    fun onMinimumDistanceStep(longer: Boolean) = change {
        val metres = MINIMUM_TRIP_DISTANCE_CHOICE.step(it.minimumTripDistanceMetres, longer)
        settings.setMinimumTripDistanceMetres(metres)
    }

    fun onSoundEnabled(enabled: Boolean) = change { settings.setSoundEnabled(enabled) }

    fun onUseBuiltInSound() = change { ownSound.useBuiltIn() }

    fun onDayTracked(day: DayOfWeek, tracked: Boolean) = change {
        settings.setSchedule(it.schedule.withTracked(day, tracked))
    }

    /** The time picker's answer for the start of [day]'s hours. */
    fun onDayStart(day: DayOfWeek, hour: Int, minute: Int) = changeHours(day) {
        it.withStart(day, LocalTime.of(hour, minute))
    }

    /** The time picker's answer for the end of [day]'s hours. */
    fun onDayEnd(day: DayOfWeek, hour: Int, minute: Int) = changeHours(day) {
        it.withEnd(day, LocalTime.of(hour, minute))
    }

    /** Gives every tracked day the hours [day] has. */
    fun onCopyHours(day: DayOfWeek) = change {
        settings.setSchedule(it.schedule.withHoursOfOnTrackedDays(day))
    }

    fun onIgnoreOutsideSchedule(ignore: Boolean) = change {
        settings.setIgnoreTripsOutsideSchedule(ignore)
    }

    /** Plays the sound a trip start would play now, whether or not the sound is switched on. */
    fun onPlaySound() = change { playSound(it.customSoundUri) }

    /**
     * Shawn picked a file. It is copied and checked first; if that fails the screen says why,
     * and the sound that was in use stays.
     */
    fun onOwnSoundPicked(uri: String) {
        viewModelScope.launch {
            passing.value = Passing(copyingSound = true)
            val refusal = ownSound.choose(uri)
            passing.value = Passing(problem = refusal?.asProblem())
        }
    }

    /** The phone could not show a file picker at all. */
    fun onNoFilePicker() {
        passing.update { it.copy(problem = SettingsProblem.NO_FILE_PICKER, problemDay = null) }
    }

    /**
     * Runs one press against the settings as they are stored at that moment. A settings file
     * that cannot be read or written is said on the screen and written to the event log; left
     * alone, the exception would end the process, and the trip service runs in it.
     */
    private fun change(write: suspend (stored: MiloSettings) -> Unit) = changeUnlessRefused {
        write(it)
        null
    }

    /**
     * A change to one of [day]'s two times. [next] works the new schedule out from the stored
     * one, or answers null for hours that would not end after they start; nothing is stored
     * then, and the screen says why, under that day.
     */
    private fun changeHours(day: DayOfWeek, next: (WorkSchedule) -> WorkSchedule?) =
        changeUnlessRefused(day) {
            val schedule = next(it.schedule)
            if (schedule == null) {
                SettingsProblem.HOURS_END_NOT_AFTER_START
            } else {
                settings.setSchedule(schedule)
                null
            }
        }

    /**
     * @param day the day of the schedule the press is about, if it is about one.
     * @param write makes the change, or returns why it was not made. Whatever it returns is
     * shown until the next press.
     */
    private fun changeUnlessRefused(
        day: DayOfWeek? = null,
        write: suspend (stored: MiloSettings) -> SettingsProblem?,
    ) {
        viewModelScope.launch {
            var failure: IOException? = null
            val problem =
                try {
                    oneChangeAtATime.withLock { write(settings.current()) }
                } catch (notStored: IOException) {
                    failure = notStored
                    SettingsProblem.COULD_NOT_SAVE
                }
            passing.update {
                it.copy(problem = problem, problemDay = day.takeIf { problem != null })
            }
            failure?.let {
                val what = "The Settings screen could not store a change"
                eventLog.add(clock(), EventCategory.ERROR, what, it.stackTraceToString())
            }
        }
    }

    /** The stored settings, or null once the file has turned out to be unreadable. */
    private fun storedSettings(): Flow<MiloSettings?> = settings.settings
        .map<MiloSettings, MiloSettings?> { it }
        .catch { unreadable ->
            if (unreadable !is IOException) throw unreadable
            val what = "The Settings screen could not read the settings"
            eventLog.add(clock(), EventCategory.ERROR, what, unreadable.stackTraceToString())
            emit(null)
        }
}

private fun SteppedChoice.step(value: Int, up: Boolean): Int =
    if (up) stepUp(value) else stepDown(value)
