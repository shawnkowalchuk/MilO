package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.util.formatKilometres
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripEdit
import com.shawnkowalchuk.milo.data.trip.TypedAddress
import com.shawnkowalchuk.milo.data.trip.TypedTrip
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

// The edit form as plain values: what Shawn has picked and typed, and what that means as stored
// times, metres and addresses. Pure functions, so they are tested without a phone. The same
// form edits a finished trip and takes a trip that MilO missed; the difference is whether there
// is a stored trip behind it.

/** The longest address the form takes: longer than any address written on one line. */
const val MAX_ADDRESS_LENGTH = 120

/** The longest text the distance field takes: six digits, a separator and three decimals. */
const val MAX_DISTANCE_LENGTH = 10

private const val METRES_PER_KILOMETRE_DIGITS = 3

/**
 * What the form holds.
 *
 * A time is a whole minute, because that is what a clock dial gives. A field that Shawn has not
 * touched holds null, which means "as stored" when a trip is edited and "not filled in" when
 * one is added. Nothing here is rounded or reformatted on its way back to storage, so that
 * opening a trip and pressing Save changes nothing.
 *
 * @param date the day the trip started on.
 * @param start and [end] are times of day on a whole minute. Null only while a trip is being
 * added and the time has not been chosen yet.
 * @param endDayOffset how many days after [date] the trip ended: 0 for a trip that ended on the
 * day it started, 1 for one that ran past midnight. Shawn says which with a switch
 * ([endingNextDay]). A stored trip opens with the number of days it really ran over.
 * @param from and [to] are the addresses as typed, or null while the field is untouched.
 * @param kilometres the distance as typed, or null while the field is untouched.
 * @param chosenCategory the category Shawn pressed in the form, or null if he left the choice
 * alone.
 */
data class TripForm(
    val date: LocalDate,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val endDayOffset: Long = 0,
    val from: String? = null,
    val to: String? = null,
    val kilometres: String? = null,
    val chosenCategory: TripCategory? = null,
)

/**
 * The form for a trip MilO missed: empty, on today's date. No time, no address and no distance
 * is filled in for him. A guess that is saved unnoticed would be a wrong line on the report.
 */
fun blankForm(nowMs: Long, zone: ZoneId): TripForm = TripForm(date = localDateOf(nowMs, zone))

/** The form for [trip]: its day and its two times, with every field untouched. */
fun formFor(trip: Trip, zone: ZoneId): TripForm {
    val start = Instant.ofEpochMilli(trip.startedAtMs).atZone(zone)
    val end = trip.endedAtMs?.let { Instant.ofEpochMilli(it).atZone(zone) }
    return TripForm(
        date = start.toLocalDate(),
        start = start.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
        end = end?.toLocalTime()?.truncatedTo(ChronoUnit.MINUTES),
        endDayOffset =
            end?.let { ChronoUnit.DAYS.between(start.toLocalDate(), it.toLocalDate()) } ?: 0,
    )
}

/**
 * When the trip starts by the form, or null if no start time is chosen.
 *
 * The form shows whole minutes and a stored time is exact to the millisecond. While the form
 * shows the minute that [stored] has, the stored time itself is the answer: a start that was
 * not changed, or was changed and put back, stays what was recorded and is not rounded to the
 * minute.
 */
fun TripForm.startedAtMs(stored: Trip?, zone: ZoneId): Long? =
    moment(date, start ?: return null, stored?.startedAtMs, zone)

/** When the trip ends by the form, on the same terms as [startedAtMs]. */
fun TripForm.endedAtMs(stored: Trip?, zone: ZoneId): Long? =
    moment(date.plusDays(endDayOffset), end ?: return null, stored?.endedAtMs, zone)

private fun moment(day: LocalDate, time: LocalTime, storedMs: Long?, zone: ZoneId): Long {
    val shown = day.atTime(time)
    val stored = storedMs?.let { Instant.ofEpochMilli(it).atZone(zone) }
    if (stored != null && stored.toLocalDateTime().truncatedTo(ChronoUnit.MINUTES) == shown) {
        return stored.toInstant().toEpochMilli()
    }
    // On the night the clocks go forward an hour does not exist, and java.time moves a time in
    // it on by that hour. On the night they go back an hour happens twice, and a dial cannot
    // say which of the two is meant. A time typed over a stored one is read on the side of the
    // change the stored time is on: a trip that ended at the second 01:30 and is given the end
    // 01:40 ends ten minutes later, not fifty minutes earlier. Without a stored time (a trip
    // that is being added) it is the first of the two.
    return ZonedDateTime.ofLocal(shown, zone, stored?.offset).toInstant().toEpochMilli()
}

/**
 * The form with its end on the day after the start ([nextDay]), or on the day of the start.
 * It is how a trip that ran past midnight is typed in, and how one that is stored as running
 * past midnight is brought back onto one day.
 */
fun TripForm.endingNextDay(nextDay: Boolean): TripForm = copy(endDayOffset = if (nextDay) 1 else 0)

/** The day the trip ends on by the form, or null if it ends on the day it starts. */
val TripForm.laterEndDate: LocalDate?
    get() = date.plusDays(endDayOffset).takeIf { endDayOffset > 0 }

/** What the distance field holds. */
sealed interface TypedDistance {
    /** Nothing was typed. */
    data object Missing : TypedDistance

    /** Something that is not a number of kilometres. */
    data object NotANumber : TypedDistance

    /** A distance. Negative if a minus sign was typed, which the form refuses by name. */
    data class Metres(val metres: Double) : TypedDistance
}

/**
 * Reads a distance in kilometres as it is typed: digits, with a dot or a comma before up to
 * three decimals, so that it reads the same whichever of the two the phone's keyboard offers.
 * No thousands separator is taken: "1,234" is one and a bit kilometres, never a thousand.
 */
fun parseKilometres(text: String): TypedDistance {
    val typed = text.trim()
    if (typed.isEmpty()) return TypedDistance.Missing
    if (!DISTANCE_AS_TYPED.matches(typed)) return TypedDistance.NotANumber
    val kilometres = BigDecimal(typed.replace(',', '.'))
    return TypedDistance.Metres(
        kilometres.movePointRight(METRES_PER_KILOMETRE_DIGITS).toDouble(),
    )
}

private val DISTANCE_AS_TYPED = Regex("""-?(\d{1,6}([.,]\d{0,3})?|[.,]\d{1,3})""")

/**
 * The distance the form stands for. An untouched field stands for the stored distance, exact to
 * the metre. So does a field in which the figure the form showed was typed again: the form
 * shows one decimal, and "12.3" typed over a stored 12 344 m is not a change of 44 m.
 */
fun TripForm.distance(stored: Trip?): TypedDistance {
    val typed =
        kilometres
            ?: return stored?.let { TypedDistance.Metres(it.distanceMetres) }
                ?: TypedDistance.Missing
    val parsed = parseKilometres(typed)
    val storedMetres = stored?.distanceMetres
    // A stored distance that cannot be written as kilometres (storage should never hold one)
    // was not shown either, so nothing typed can be "the figure that was shown".
    val wasShown = storedMetres != null && storedMetres.isFinite() && storedMetres >= 0.0
    if (parsed is TypedDistance.Metres && storedMetres != null && wasShown) {
        val shown = parseKilometres(formatKilometres(storedMetres, Locale.ROOT))
        if (parsed == shown) return TypedDistance.Metres(storedMetres)
    }
    return parsed
}

/** An address as it is stored: trimmed, on one line, and null when nothing was typed. */
fun addressAsStored(typed: String): String? =
    typed.replace(WHITESPACE, " ").trim().take(MAX_ADDRESS_LENGTH).trim().ifEmpty { null }

private val WHITESPACE = Regex("""\s+""")

/**
 * What a save of the form asks storage for, when a stored trip is edited: only what differs
 * from [stored]. A form that was opened and saved untouched asks for nothing.
 *
 * Call it for a form that has no problem ([formProblems]); a time or a distance that is missing
 * or unreadable is left out here, not guessed.
 */
fun TripForm.toEdit(stored: Trip, zone: ZoneId): TripEdit {
    val start = startedAtMs(stored, zone)
    val end = endedAtMs(stored, zone)
    val metres = (distance(stored) as? TypedDistance.Metres)?.metres
    return TripEdit(
        startedAtMs = start.takeIf { start != stored.startedAtMs },
        endedAtMs = end.takeIf { end != stored.endedAtMs },
        distanceMetres = metres.takeIf { metres != stored.distanceMetres },
        startAddress = addressEdit(field = from, stored = stored.startAddress),
        endAddress = addressEdit(field = to, stored = stored.endAddress),
        category = chosenCategory,
    )
}

/** The typed address as an edit, or null if the field is untouched or holds what is stored. */
private fun addressEdit(field: String?, stored: String?): TypedAddress? {
    val text = addressAsStored(field ?: return null)
    return if (text == stored) null else TypedAddress(text)
}

/**
 * The trip the form describes, when one is added, or null while a time or the distance is
 * missing or unreadable.
 */
fun TripForm.toTypedTrip(zone: ZoneId): TypedTrip? {
    val metres = (distance(stored = null) as? TypedDistance.Metres)?.metres ?: return null
    return TypedTrip(
        startedAtMs = startedAtMs(stored = null, zone) ?: return null,
        endedAtMs = endedAtMs(stored = null, zone) ?: return null,
        distanceMetres = metres,
        startAddress = from?.let(::addressAsStored),
        endAddress = to?.let(::addressAsStored),
        category = chosenCategory,
    )
}

/**
 * Whether the form holds something that is lost if the screen is left now: a value picked or
 * typed that is not what the form opened with. The screen asks before it is left while this is
 * true.
 *
 * What is typed is compared by what it means, as a save would read it. A time that is changed
 * and put back, an address typed again as it stood, and the distance the form showed typed
 * again are nothing to lose. A distance field that was emptied or holds letters is: a save
 * would refuse it, but it is still something Shawn typed.
 *
 * @param opened the form as it was when the screen opened.
 * @param stored the trip that is being edited, or null when one is being added.
 */
fun TripForm.holdsUnsavedWork(opened: TripForm, stored: Trip?): Boolean = date != opened.date ||
    start != opened.start ||
    end != opened.end ||
    endDayOffset != opened.endDayOffset ||
    chosenCategory != opened.chosenCategory ||
    distance(stored) != opened.distance(stored) ||
    addressMeant(from, stored?.startAddress) != stored?.startAddress ||
    addressMeant(to, stored?.endAddress) != stored?.endAddress

/** The address a field stands for: what is stored while it is untouched, else what is typed. */
private fun addressMeant(field: String?, stored: String?): String? =
    if (field == null) stored else addressAsStored(field)
