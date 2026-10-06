package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Asks for a day with Android's usual calendar: a month at a time, and two buttons. Nothing is
 * chosen until the confirming button is pressed; pressing outside the dialog, or Back, is the
 * same as [onDismiss]. It is the companion of [TimeDialog].
 *
 * @param date the day the calendar opens on, already marked.
 * @param latest the last day that can be chosen. Later days are greyed out: MilO only asks for
 * the day of something that has happened.
 */
// Material 3's date picker is marked experimental in the release the Compose BOM pins (1.4.0),
// like its time picker, and Material has no other. The opt-in is kept to this one component.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateDialog(
    date: LocalDate,
    latest: LocalDate,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = utcMillisOf(date),
            selectableDates = NotAfter(latest),
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    // The picker lets the marked day be unmarked. Confirming nothing chooses
                    // nothing.
                    val picked = state.selectedDateMillis
                    if (picked == null) onDismiss() else onConfirm(dateOfUtcMillis(picked))
                },
            ) {
                Text(text = confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = dismissLabel) } },
    ) {
        // Material's calendar has one height and does not scroll by itself. With the phone on
        // its side the dialog is lower than the calendar: the weekday letters were then drawn
        // over the first weeks and the last week lay under the buttons (seen on an emulator,
        // 2026-10-06). Scrolling lets every day be reached there, and changes nothing where the
        // calendar fits.
        DatePicker(state = state, modifier = Modifier.verticalScroll(rememberScrollState()))
    }
}

/** Offers every day up to and including [latest], and no year after its year. */
@OptIn(ExperimentalMaterial3Api::class)
private class NotAfter(private val latest: LocalDate) : SelectableDates {
    private val latestMillis = utcMillisOf(latest)

    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= latestMillis

    override fun isSelectableYear(year: Int): Boolean = year <= latest.year
}

// Material's date picker speaks in milliseconds since 1970 at midnight UTC, whatever the
// phone's time zone: a day, not a moment. These two turn a calendar day into that and back, so
// that no time zone can move the chosen day to its neighbour.

/** [date] as the picker counts it: midnight UTC of that day. */
internal fun utcMillisOf(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** The calendar day the picker means by [utcMillis]. */
internal fun dateOfUtcMillis(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atOffset(ZoneOffset.UTC).toLocalDate()
