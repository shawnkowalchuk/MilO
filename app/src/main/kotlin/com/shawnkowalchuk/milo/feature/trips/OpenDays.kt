package com.shawnkowalchuk.milo.feature.trips

import java.time.LocalDate
import java.time.YearMonth

// Which days of the month on screen show their trips. Each day is a tile that is closed until
// its heading is pressed (the owner's own addition to his drawing, 2026-10-06: "each day
// expand when clicked ... shows the total day driven and when clicked expands and shows each
// individual trip"). The rules are pure functions, so they are tested without a phone; the
// ViewModel keeps the set, which is why it outlives a turn of the phone and the edit screen.

/** A press on a day's heading: an open day is closed, and a closed one is opened. */
internal fun dayToggled(open: Set<LocalDate>, day: LocalDate): Set<LocalDate> =
    if (day in open) open - day else open + day

/**
 * The open days once [month] is on screen where [was] stood. Another month starts with every
 * day closed, as the screen itself does; the same month keeps what was open.
 */
internal fun openDaysIn(month: YearMonth, was: YearMonth, open: Set<LocalDate>): Set<LocalDate> =
    if (month == was) open else emptySet()

/**
 * The open days after a trip was saved on the edit screen: as [openDaysIn] has them, and the
 * day the saved trip is in now is opened too. Shut in a closed day, a trip that was just added
 * or changed would be out of sight, and look as if it had not been saved.
 *
 * @param savedDay the day the saved trip starts on. A day outside [month] is not opened: its
 * tile is not on screen.
 */
internal fun openDaysAfterSave(
    month: YearMonth,
    was: YearMonth,
    open: Set<LocalDate>,
    savedDay: LocalDate,
): Set<LocalDate> {
    val kept = openDaysIn(month, was, open)
    return if (YearMonth.from(savedDay) == month) kept + savedDay else kept
}
