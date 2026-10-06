package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import java.time.LocalDate
import java.time.YearMonth

/** The day of the month the reminder starts on out of the box: the 1st, as Shawn's brief says. */
const val DEFAULT_REMINDER_DAY = 1

/**
 * The days of the month the reminder can be set to. In a month that is shorter than the day
 * chosen, the reminder starts on that month's last day.
 */
val REMINDER_DAYS = 1..31

/**
 * That the monthly reminder was shown: the month it was about, and the day it was shown on.
 *
 * The reminder is shown once a day until the month is submitted, and it is looked at on several
 * occasions each day (a daily alarm, every process start, every time MilO comes to the front).
 * This is what keeps those to one notification a day. It is not a setting Shawn chooses: like
 * the time of the last driving alert, it is a small piece of state that must outlive the
 * process.
 *
 * @param onDay the calendar day in the phone's time zone at that moment.
 */
data class ReminderShown(val month: YearMonth, val onDay: LocalDate)

// How the reminder is kept in the settings file. The key names are what is written to the file;
// renaming one silently resets that value. Calendar days are stored as days since 1970-01-01,
// like the days of a sent report, so no time zone can move them.

private val ENABLED = booleanPreferencesKey("reminder_enabled")
private val DAY_OF_MONTH = intPreferencesKey("reminder_day_of_month")
private val SHOWN_FOR_MONTH = longPreferencesKey("reminder_shown_for_month_first_day")
private val SHOWN_ON_DAY = longPreferencesKey("reminder_shown_on_day")

/** Whether the reminder is switched on. On, until it has been switched off. */
internal fun Preferences.readReminderEnabled(): Boolean = this[ENABLED] ?: true

/**
 * The reminder's day of the month. A stored number that is no day of a month, which no setter
 * can write, reads as the default.
 */
internal fun Preferences.readReminderDay(): Int =
    this[DAY_OF_MONTH]?.takeIf { it in REMINDER_DAYS } ?: DEFAULT_REMINDER_DAY

/**
 * The stored record of the last reminder, or null if none was shown. The two values are
 * written together, so a file that holds only one of them, or a number that is no day, reads
 * as "none was shown": the cautious reading, which at worst shows today's reminder once more.
 */
internal fun Preferences.readReminderShown(): ReminderShown? {
    val month = dayOrNull(this[SHOWN_FOR_MONTH]) ?: return null
    val onDay = dayOrNull(this[SHOWN_ON_DAY]) ?: return null
    return ReminderShown(YearMonth.from(month), onDay)
}

internal fun MutablePreferences.writeReminderEnabled(enabled: Boolean) {
    this[ENABLED] = enabled
}

internal fun MutablePreferences.writeReminderDay(day: Int) {
    this[DAY_OF_MONTH] = day
}

internal fun MutablePreferences.writeReminderShown(shown: ReminderShown) {
    this[SHOWN_FOR_MONTH] = shown.month.atDay(1).toEpochDay()
    this[SHOWN_ON_DAY] = shown.onDay.toEpochDay()
}

/** The calendar day [epochDay] days after 1970-01-01, or null for a number that is no day. */
private fun dayOrNull(epochDay: Long?): LocalDate? = epochDay
    ?.takeIf { it in LocalDate.MIN.toEpochDay()..LocalDate.MAX.toEpochDay() }
    ?.let(LocalDate::ofEpochDay)
