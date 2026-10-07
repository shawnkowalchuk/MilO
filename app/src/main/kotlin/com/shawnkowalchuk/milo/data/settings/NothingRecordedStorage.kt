package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** The time of day from which the check is made out of the box: noon. */
val DEFAULT_NOTHING_RECORDED_TIME: LocalTime = LocalTime.NOON

/**
 * What the settings file holds for the "nothing recorded" check: on a work day, if no trip has
 * been recorded by a set time, MilO says so with a notification
 * (`platform/nothingrecorded/`).
 *
 * The first two are settings Shawn chooses. The third is not: like the record of the last
 * monthly reminder, it is a small piece of state that must outlive the process, and it is what
 * keeps the notification to one a day however often MilO looks.
 *
 * @param enabled the Settings switch. On, until it has been switched off.
 * @param checkAt the time of day from which a work day is checked, on a whole minute. A day
 * whose work hours start later than this is checked from their start.
 * @param shownOn the calendar day, in the phone's time zone at that moment, on which the
 * notification was last shown, or null if it never was.
 */
data class NothingRecordedStored(
    val enabled: Boolean = true,
    val checkAt: LocalTime = DEFAULT_NOTHING_RECORDED_TIME,
    val shownOn: LocalDate? = null,
)

// How the check is kept in the settings file. The key names are what is written to the file;
// renaming one silently resets that value. The time is stored as whole minutes since midnight,
// like the hours of the work schedule, and the day as days since 1970-01-01, like the day of
// the last monthly reminder, so that no time zone can move it.
//
// TODO(debt): the switch and the time do not travel in an export file (`TransferStorage.kt`),
// although they mean the same on any phone: adding them changes the form of the file, which
// needs a new format version that still reads the old one. An import leaves this phone's own
// two values as they are (FINDINGS_LOG, 2026-10-06, "The check's two settings are not in an
// export file"). Android's own backup takes the whole settings file, these keys included.

private val ENABLED = booleanPreferencesKey("nothing_recorded_enabled")
private val MINUTE_OF_DAY = intPreferencesKey("nothing_recorded_minute_of_day")
private val SHOWN_ON_DAY = longPreferencesKey("nothing_recorded_shown_on_day")

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

/**
 * The stored check. A value that was never written reads as its default, and so does one that
 * no setter can write (a number that is no minute of a day, or no day): the cautious reading,
 * which at worst checks at noon and shows today's notification once more.
 */
internal fun Preferences.readNothingRecorded(): NothingRecordedStored {
    val defaults = NothingRecordedStored()
    return NothingRecordedStored(
        enabled = this[ENABLED] ?: defaults.enabled,
        checkAt = timeOfMinute(this[MINUTE_OF_DAY]) ?: defaults.checkAt,
        shownOn = dayOrNull(this[SHOWN_ON_DAY]),
    )
}

/** The time of day [minute] minutes after midnight, or null for a value that is no such time. */
private fun timeOfMinute(minute: Int?): LocalTime? = minute
    ?.takeIf { it in 0 until MINUTES_PER_DAY }
    ?.let { LocalTime.of(it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) }

/** The calendar day [epochDay] days after 1970-01-01, or null for a number that is no day. */
private fun dayOrNull(epochDay: Long?): LocalDate? = epochDay
    ?.takeIf { it in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay() }
    ?.let(LocalDate::ofEpochDay)

/** Switches the "nothing recorded" check on or off. */
suspend fun SettingsStore.setNothingRecordedEnabled(enabled: Boolean) {
    dataStore.edit { it[ENABLED] = enabled }
}

/**
 * Stores the time of day from which a work day is checked. A time with seconds in it is
 * refused: the time picker gives whole minutes, and whole minutes are what is stored.
 */
suspend fun SettingsStore.setNothingRecordedTime(time: LocalTime) {
    require(time == time.truncatedTo(ChronoUnit.MINUTES)) {
        "The check's time must be on a whole minute: $time"
    }
    dataStore.edit { it[MINUTE_OF_DAY] = time.hour * MINUTES_PER_HOUR + time.minute }
}

/** Stores that the notification was shown on [day], so that the day brings no second one. */
suspend fun SettingsStore.setNothingRecordedShownOn(day: LocalDate) {
    dataStore.edit { it[SHOWN_ON_DAY] = day.toEpochDay() }
}
