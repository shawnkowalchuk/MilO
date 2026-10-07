package com.shawnkowalchuk.milo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.schedule.withEndOn
import com.shawnkowalchuk.milo.core.schedule.withStartOn
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.GRACE_PERIOD_CHOICE
import com.shawnkowalchuk.milo.data.settings.MINIMUM_TRIP_DISTANCE_CHOICE
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.PARKED_LIMIT_CHOICE
import com.shawnkowalchuk.milo.data.settings.REMINDER_DAY_CHOICE
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.settings.SteppedChoice
import com.shawnkowalchuk.milo.data.settings.isEmailAddress
import com.shawnkowalchuk.milo.data.settings.reportTextOrNull
import com.shawnkowalchuk.milo.data.settings.setParkedLimitSeconds
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

/** What the event log calls a change of the driving alert's switch. */
private const val DRIVING_ALERT_SWITCH = "the Settings switch"

/** What the event log calls a look at the reminder that a change of its tile prompted. */
private const val REMINDER_CARD = "the reminder was changed in Settings"

/**
 * The Settings screen's link to the stored settings. It keeps no copy of them: what the screen
 * shows is the settings store's own flow, and every press writes to the store.
 *
 * Nothing here tells the trip engine about a change. The trip controller reads the grace period,
 * the parked limit and the minimum distance at every trigger, and the trip service reads the sound at every trip
 * start, so a stored value is simply the one in force from the next event on. The work schedule
 * is read the same way, at the moment a trip is closed, to sort that trip; no stored trip is
 * sorted again because the schedule changed.
 *
 * The exceptions are the driving alert's switch and the reminder's tile. The alert has no
 * trigger of its own to read the setting at: it has to ask the phone to report driving, or to
 * stop, when the switch is pressed, so it is told ([armDrivingAlert]). The monthly reminder is
 * told for a like reason ([lookAtReminder]): a reminder that is showing must go when it is
 * switched off, and not at tomorrow's look.
 *
 * @param ownSound copies and checks a picked audio file, and goes back to the built-in sound.
 * @param playSound plays the trip-start sound the way a trip start does, given the stored
 * custom sound or null for the built-in one. Called on the main thread.
 * @param armDrivingAlert has the driving alert bring its request to the phone in line with the
 * stored switch. Its argument says what prompted it, for the event log.
 * @param lookAtReminder has the monthly reminder decide again, from the stored settings,
 * whether it is shown. Its argument says what prompted it, for the event log.
 * @param clock wall-clock milliseconds.
 */
class SettingsViewModel(
    private val settings: SettingsStore,
    private val ownSound: OwnTripSound,
    private val playSound: (customSoundUri: String?) -> Unit,
    private val armDrivingAlert: (source: String) -> Unit,
    private val lookAtReminder: (source: String) -> Unit,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) : ViewModel() {
    /**
     * What the screen shows that is not a stored setting.
     *
     * @param problemDay the day of the schedule the press that did not work was about, if it
     * was about one: the screen says under that day what went wrong.
     * @param refusedEmail what stands in the address field while that is not an email address
     * and so is not stored, or null. The text is kept with the refusal, and not only that
     * there was one: the field is built again when the phone is turned or the screen comes
     * back from under another, and "not saved" must then stand under the text it was said of,
     * never under the stored address.
     */
    private data class Passing(
        val copyingSound: Boolean = false,
        val problem: SettingsProblem? = null,
        val problemDay: DayOfWeek? = null,
        val refusedEmail: String? = null,
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
                settingsUiState(
                    settings = stored,
                    copyingSound = now.copyingSound,
                    problem = now.problem,
                    problemDay = now.problemDay,
                    refusedEmail = now.refusedEmail,
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(KEEP_WATCHING_MS),
            SettingsUiState.Reading,
        )

    fun onGraceStep(longer: Boolean) = change {
        settings.setGracePeriodSeconds(GRACE_PERIOD_CHOICE.step(it.gracePeriodSeconds, longer))
    }

    fun onParkedLimitStep(longer: Boolean) = change {
        settings.setParkedLimitSeconds(PARKED_LIMIT_CHOICE.step(it.parkedLimitSeconds, longer))
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

    /** A new start for [day]'s hours, or for every day's if [day] is null: the whole week. */
    fun onDayStart(day: DayOfWeek?, hour: Int, minute: Int) = changeHours(day) {
        it.withStartOn(day, LocalTime.of(hour, minute))
    }

    /** A new end for [day]'s hours, or for every day's. */
    fun onDayEnd(day: DayOfWeek?, hour: Int, minute: Int) = changeHours(day) {
        it.withEndOn(day, LocalTime.of(hour, minute))
    }

    /** Gives every tracked day the hours [day] has. */
    fun onCopyHours(day: DayOfWeek) = change {
        settings.setSchedule(it.schedule.withHoursOfOnTrackedDays(day))
    }

    fun onIgnoreOutsideSchedule(ignore: Boolean) = change {
        settings.setIgnoreTripsOutsideSchedule(ignore)
    }

    /** Stored first, so that the alert finds the new value when it reads the switch. */
    fun onDrivingAlertEnabled(enabled: Boolean) = change {
        settings.setDrivingAlertEnabled(enabled)
        armDrivingAlert(DRIVING_ALERT_SWITCH)
    }

    /** Stored first, so that the reminder finds the new value when it looks. */
    fun onReminderEnabled(enabled: Boolean) = change {
        settings.setReminderEnabled(enabled)
        lookAtReminder(REMINDER_CARD)
    }

    /** The reminder's day of the month, one day later or earlier. */
    fun onReminderDayStep(later: Boolean) = change {
        settings.setReminderDay(REMINDER_DAY_CHOICE.step(it.reminderDay, later))
        lookAtReminder(REMINDER_CARD)
    }

    /**
     * What stands in one of the report's three free-text fields. It is stored as it is typed,
     * like every setting, without the spaces around it; an emptied field forgets the value.
     */
    fun onReportName(typed: String) = change { settings.setReportName(reportTextOrNull(typed)) }

    fun onReportCompany(typed: String) = change {
        settings.setReportCompany(reportTextOrNull(typed))
    }

    fun onReportVehicle(typed: String) = change {
        settings.setReportVehicle(reportTextOrNull(typed))
    }

    /**
     * What stands in the field for the accountant's address. An email address is stored, and
     * an emptied field forgets the one that was. Anything else is not stored, and the field
     * says so: the address stored before stays in force until a whole new one is typed.
     */
    fun onAccountantEmail(typed: String) {
        val address = typed.trim()
        val storable = address.isEmpty() || isEmailAddress(address)
        passing.update { it.copy(refusedEmail = typed.takeUnless { storable }) }
        if (storable) change { settings.setAccountantEmail(address.ifEmpty { null }) }
    }

    /** Plays the sound a trip start would play now, whether or not the sound is switched on. */
    fun onPlaySound() = change { playSound(it.customSoundUri) }

    /**
     * Shawn picked a file. It is copied and checked first; if that fails the screen says why,
     * and the sound that was in use stays.
     */
    fun onOwnSoundPicked(uri: String) {
        viewModelScope.launch {
            // What the address field says about itself is not about the sound, and stays.
            passing.update { Passing(copyingSound = true, refusedEmail = it.refusedEmail) }
            val refusal = ownSound.choose(uri)
            passing.update {
                Passing(problem = refusal?.asProblem(), refusedEmail = it.refusedEmail)
            }
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
     * A change to one of [day]'s two times, or of the whole week's. [next] works the new
     * schedule out from the stored one, or answers null for hours that would not end after
     * they start; nothing is stored then, and the screen says why, under that day.
     */
    private fun changeHours(day: DayOfWeek?, next: (WorkSchedule) -> WorkSchedule?) =
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
