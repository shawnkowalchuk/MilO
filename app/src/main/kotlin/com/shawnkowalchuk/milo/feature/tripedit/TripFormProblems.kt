package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.trip.MAX_SPEED_KMH
import com.shawnkowalchuk.milo.data.trip.Trip
import java.time.ZoneId

// What stops a form from being saved. A pure function, so every rule is tested without a phone.
// The form is the last point before a typed value becomes a line on the report accounts reads,
// so everything typed is checked here, and nothing is put right silently.

/**
 * The longest distance the form takes for one trip. A day's driving in a work truck is far
 * below it; a figure above it is a typing slip, such as a lost decimal separator.
 */
const val MAX_TRIP_KILOMETRES = 2_000

private const val METRES_PER_KILOMETRE = 1_000.0
private const val MILLIS_PER_HOUR = 3_600_000.0

/** The part of the form a problem is about: the screen says it there, beside what is wrong. */
enum class FormPart {
    /** The day and the two times: the tile "When". */
    TIMES,

    /** The kilometres: the tile "Distance, km". */
    DISTANCE,
}

/**
 * One thing to put right before the form can be saved. Listed in the order of the form.
 *
 * @param part where on the form it is put right, and so where the screen says it.
 */
enum class FormProblem(val part: FormPart) {
    /** A trip that is being added has no start time yet. */
    START_MISSING(FormPart.TIMES),

    /** A trip that is being added has no end time yet. */
    END_MISSING(FormPart.TIMES),

    /** The end is not later than the start. */
    END_NOT_AFTER_START(FormPart.TIMES),

    /** The start or the end has not happened yet. */
    IN_THE_FUTURE(FormPart.TIMES),

    /** The trip would still be running when the trip that is being recorded began. */
    OVERLAPS_RECORDING(FormPart.TIMES),

    DISTANCE_MISSING(FormPart.DISTANCE),

    DISTANCE_NOT_A_NUMBER(FormPart.DISTANCE),

    DISTANCE_NEGATIVE(FormPart.DISTANCE),

    /** More than [MAX_TRIP_KILOMETRES]. */
    DISTANCE_TOO_LONG(FormPart.DISTANCE),

    /** More than the truck can cover between the start and the end ([MAX_SPEED_KMH]). */
    DISTANCE_TOO_FAST(FormPart.DISTANCE),
}

/**
 * What a form is checked against besides itself, read at the moment Save is pressed.
 *
 * @param nowMs the time on the phone's clock.
 * @param recordingSinceMs when the trip that is being recorded started, or null if none is.
 */
data class FormCheck(val nowMs: Long, val recordingSinceMs: Long?)

/**
 * Everything that is wrong with [form], or an empty list if it can be saved.
 *
 * - The end must be later than the start. A trip of no length, or one that ends before it
 *   starts, is a slip of the dial, or a trip that ran past midnight without the form having
 *   been told so. It is never put on the next day by itself: the refusal names the switch.
 * - Neither time may lie in the future: a trip is added after it was driven.
 * - **It may not overlap the trip that is being recorded.** That trip runs from its start until
 *   some moment still to come, so a trip overlaps it exactly when it ends after the recording
 *   started. Trips that have finished are not compared with each other.
 * - The distance must be a number that is not negative, not above [MAX_TRIP_KILOMETRES], and
 *   not further than a truck can go in the time: more than [MAX_SPEED_KMH] on average over the
 *   whole trip catches a lost decimal separator ("123" for "12.3") that the first limit lets
 *   through. Zero is taken: a trip that went nowhere is still Shawn's to enter.
 *
 * The times and the distance are checked whether or not they were changed. A recorded trip
 * whose stored times make no sense (the phone corrected its clock during it) is therefore not
 * saved again as it is: its times have to be put right first.
 *
 * @param stored the trip that is being edited, or null when one is being added.
 */
fun formProblems(form: TripForm, stored: Trip?, check: FormCheck, zone: ZoneId): List<FormProblem> {
    val start = form.startedAtMs(stored, zone)
    val end = form.endedAtMs(stored, zone)
    // How long the trip lasted, or null while it has no two times in the right order.
    val lastedMs = if (start != null && end != null && end > start) end - start else null
    return buildList {
        if (start == null) add(FormProblem.START_MISSING)
        if (end == null) add(FormProblem.END_MISSING)
        if (start != null && end != null && lastedMs == null) add(FormProblem.END_NOT_AFTER_START)
        val latest = listOfNotNull(start, end).maxOrNull()
        if (latest != null && latest > check.nowMs) add(FormProblem.IN_THE_FUTURE)
        val recordingSinceMs = check.recordingSinceMs
        if (latest != null && recordingSinceMs != null && latest > recordingSinceMs) {
            add(FormProblem.OVERLAPS_RECORDING)
        }
        distanceProblem(form.distance(stored), lastedMs)?.let(::add)
    }
}

/**
 * @param lastedMs how long the trip lasted, or null while its times are not in order. The speed
 * is then not judged: the times are the thing to put right first.
 */
private fun distanceProblem(distance: TypedDistance, lastedMs: Long?): FormProblem? =
    when (distance) {
        TypedDistance.Missing -> FormProblem.DISTANCE_MISSING

        TypedDistance.NotANumber -> FormProblem.DISTANCE_NOT_A_NUMBER

        is TypedDistance.Metres -> {
            val metres = distance.metres
            // The furthest the truck gets in the time at the top speed. Multiplied out and not
            // divided, so that a trip exactly at the limit is not thrown over it by rounding.
            val reachableMetresTimesHour = MAX_SPEED_KMH * METRES_PER_KILOMETRE * (lastedMs ?: 0)
            when {
                metres < 0 -> FormProblem.DISTANCE_NEGATIVE

                metres > MAX_TRIP_KILOMETRES * METRES_PER_KILOMETRE ->
                    FormProblem.DISTANCE_TOO_LONG

                lastedMs != null && metres * MILLIS_PER_HOUR > reachableMetresTimesHour ->
                    FormProblem.DISTANCE_TOO_FAST

                else -> null
            }
        }
    }
