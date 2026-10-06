package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.data.settings.GRACE_PERIOD_CHOICE
import com.shawnkowalchuk.milo.data.settings.MINIMUM_TRIP_DISTANCE_CHOICE
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.REMINDER_DAY_CHOICE
import com.shawnkowalchuk.milo.platform.trip.OwnSoundRefusal
import java.time.DayOfWeek
import java.time.LocalTime

// What the Settings screen shows, and the function that decides it from the stored settings.
// Pure, so it is tested without a phone.

/** Something the screen has to tell Shawn about a press that did not do what it said. */
enum class SettingsProblem {
    /** The picked file could not be read or copied. */
    SOUND_COULD_NOT_COPY,

    /** The picked file is larger than MilO copies. */
    SOUND_TOO_LARGE,

    /** The picked file was copied, but Android cannot play it. */
    SOUND_NOT_PLAYABLE,

    /** The phone has no app that can show a file picker. */
    NO_FILE_PICKER,

    /** The settings file could not be written or read: the change was not made. */
    COULD_NOT_SAVE,

    /** A day's hours would have ended no later than they start: the time was not stored. */
    HOURS_END_NOT_AFTER_START,
}

/**
 * One day of the work schedule as the screen shows it.
 *
 * @param tracked whether the day's switch is on. The two times are shown only then.
 * @param canCopy whether the day offers "Use these hours on every tracked day": it is tracked,
 * and another tracked day has other hours. A schedule whose days all agree shows no such
 * button.
 * @param hoursRefused whether the last press was a time for this day that would have ended
 * its hours no later than they start. The refusal is said under this day's two times, where
 * the press was made: said once at the top of the card it is off the screen for every day
 * but the first few, and the press looks as if it did nothing.
 */
data class ScheduleDay(
    val day: DayOfWeek,
    val tracked: Boolean,
    val start: LocalTime,
    val end: LocalTime,
    val canCopy: Boolean,
    val hoursRefused: Boolean,
)

/**
 * The four settings of the report for the accountant as the screen shows them: each as it is
 * stored, or empty while it is not set.
 *
 * @param accountantEmail what the address field starts with when it is built: the stored
 * address, or, while [emailRefused], the text that was typed and not stored.
 * @param emailRefused true while what stands in the address field is not an email address, and
 * so is not what is stored. The field says so, because every other field is saved as typed.
 * It is never true beside the stored address: the two come from the one refused text.
 */
data class ReportFields(
    val name: String,
    val company: String,
    val vehicle: String,
    val accountantEmail: String,
    val emailRefused: Boolean,
)

/** What the Settings screen shows. */
sealed interface SettingsUiState {
    /** The settings have not been read yet. */
    data object Reading : SettingsUiState

    /** The settings file cannot be read, so there is nothing true to show or to change. */
    data object Unreadable : SettingsUiState

    /**
     * The settings as stored.
     *
     * @param truckPaired false while no truck is stored.
     * @param truckName the truck's name as the phone gave it, or null if it has none.
     * @param canShortenGrace false at the lower end of the range, and so for the other three:
     * the button is greyed out there.
     * @param usesOwnSound true if a trip start plays the file Shawn chose, false for the
     * built-in sound.
     * @param ownSoundName what that file was called, or null if the phone gave no name.
     * @param copyingSound true while a picked file is being copied and checked. The sound
     * buttons wait.
     * @param schedule the seven days, Monday first.
     * @param ignoreOutsideSchedule true if "Ignore them" is chosen for the trips that start
     * outside the schedule, false for "Save as Personal".
     * @param drivingAlertEnabled whether MilO notifies when the phone reports driving during
     * the work hours with no trip being recorded.
     * @param report who the report for the accountant is from, and where it goes.
     * @param reminderEnabled whether MilO reminds that last month's report has not been sent.
     * @param reminderDay the day of the month from which it does, 1 to 31.
     * @param canRemindEarlier false on the 1st, and [canRemindLater] on the 31st.
     * @param problem the last press that did not work, until the next one.
     */
    data class Ready(
        val truckPaired: Boolean,
        val truckName: String?,
        val gracePeriodSeconds: Int,
        val canShortenGrace: Boolean,
        val canLengthenGrace: Boolean,
        val minimumDistanceMetres: Int,
        val canLowerMinimum: Boolean,
        val canRaiseMinimum: Boolean,
        val soundEnabled: Boolean,
        val usesOwnSound: Boolean,
        val ownSoundName: String?,
        val copyingSound: Boolean,
        val schedule: List<ScheduleDay>,
        val ignoreOutsideSchedule: Boolean,
        val drivingAlertEnabled: Boolean,
        val report: ReportFields,
        val reminderEnabled: Boolean,
        val reminderDay: Int,
        val canRemindEarlier: Boolean,
        val canRemindLater: Boolean,
        val problem: SettingsProblem?,
    ) : SettingsUiState
}

/**
 * The screen for the settings as they are stored. The two figures are shown as stored, even if
 * a value is outside what the screen offers; the first press of a button brings it inside.
 *
 * @param problemDay the day of the schedule the press that did not work was about, or null
 * if it was about none. It places refused hours under their day.
 * @param refusedEmail what stands in the address field while that is not an email address and
 * so was not stored, or null while the field holds what is stored.
 */
fun settingsUiState(
    settings: MiloSettings,
    copyingSound: Boolean,
    problem: SettingsProblem?,
    problemDay: DayOfWeek?,
    refusedEmail: String? = null,
): SettingsUiState.Ready = SettingsUiState.Ready(
    truckPaired = settings.truckAddress != null,
    truckName = settings.truckName,
    gracePeriodSeconds = settings.gracePeriodSeconds,
    canShortenGrace = GRACE_PERIOD_CHOICE.canStepDown(settings.gracePeriodSeconds),
    canLengthenGrace = GRACE_PERIOD_CHOICE.canStepUp(settings.gracePeriodSeconds),
    minimumDistanceMetres = settings.minimumTripDistanceMetres,
    canLowerMinimum = MINIMUM_TRIP_DISTANCE_CHOICE.canStepDown(settings.minimumTripDistanceMetres),
    canRaiseMinimum = MINIMUM_TRIP_DISTANCE_CHOICE.canStepUp(settings.minimumTripDistanceMetres),
    soundEnabled = settings.soundEnabled,
    usesOwnSound = settings.customSoundUri != null,
    ownSoundName = settings.customSoundName,
    copyingSound = copyingSound,
    schedule =
        DayOfWeek.entries.map { day ->
            val hours = settings.schedule.on(day)
            ScheduleDay(
                day = day,
                tracked = hours.tracked,
                start = hours.start,
                end = hours.end,
                canCopy = settings.schedule.canCopyHoursOf(day),
                hoursRefused =
                    problem == SettingsProblem.HOURS_END_NOT_AFTER_START && day == problemDay,
            )
        },
    ignoreOutsideSchedule = settings.ignoreTripsOutsideSchedule,
    drivingAlertEnabled = settings.drivingAlertEnabled,
    report =
        ReportFields(
            name = settings.reportName.orEmpty(),
            company = settings.reportCompany.orEmpty(),
            vehicle = settings.reportVehicle.orEmpty(),
            accountantEmail = refusedEmail ?: settings.accountantEmail.orEmpty(),
            emailRefused = refusedEmail != null,
        ),
    reminderEnabled = settings.reminderEnabled,
    reminderDay = settings.reminderDay,
    canRemindEarlier = REMINDER_DAY_CHOICE.canStepDown(settings.reminderDay),
    canRemindLater = REMINDER_DAY_CHOICE.canStepUp(settings.reminderDay),
    problem = problem,
)

/** What the screen says when a picked file was refused. */
fun OwnSoundRefusal.asProblem(): SettingsProblem = when (this) {
    OwnSoundRefusal.COULD_NOT_COPY -> SettingsProblem.SOUND_COULD_NOT_COPY
    OwnSoundRefusal.TOO_LARGE -> SettingsProblem.SOUND_TOO_LARGE
    OwnSoundRefusal.NOT_PLAYABLE -> SettingsProblem.SOUND_NOT_PLAYABLE
}
