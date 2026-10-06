package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.trip.MAX_SPEED_KMH

// Which words the edit screen uses for what is wrong with the form and for who chose Business
// or Personal. Only the choice is made here, so that it is tested without a phone: the words
// themselves are in strings.xml.

/**
 * One sentence of the screen: a string resource, and what it quotes, if it quotes something.
 * A sentence quotes a number or a name, never both.
 *
 * @param number the limit the sentence quotes, so that the words and the rule cannot disagree.
 * @param names the string resource of something on the screen that the sentence calls by its
 * name, such as a switch, so that the two cannot be worded differently.
 */
internal data class Sentence(val text: Int, val number: Int? = null, val names: Int? = null)

/** What the screen says for one thing that is wrong with the form. */
internal fun FormProblem.sentence(): Sentence = when (this) {
    FormProblem.START_MISSING -> Sentence(R.string.trip_edit_problem_start_missing)

    FormProblem.END_MISSING -> Sentence(R.string.trip_edit_problem_end_missing)

    FormProblem.END_NOT_AFTER_START ->
        Sentence(
            R.string.trip_edit_problem_end_not_after_start,
            names = R.string.trip_edit_ends_next_day,
        )

    FormProblem.IN_THE_FUTURE -> Sentence(R.string.trip_edit_problem_future)

    FormProblem.OVERLAPS_RECORDING -> Sentence(R.string.trip_edit_problem_overlaps_recording)

    FormProblem.DISTANCE_MISSING -> Sentence(R.string.trip_edit_problem_distance_missing)

    FormProblem.DISTANCE_NOT_A_NUMBER ->
        Sentence(R.string.trip_edit_problem_distance_not_a_number)

    FormProblem.DISTANCE_NEGATIVE -> Sentence(R.string.trip_edit_problem_distance_negative)

    FormProblem.DISTANCE_TOO_LONG ->
        Sentence(R.string.trip_edit_problem_distance_too_long, MAX_TRIP_KILOMETRES)

    FormProblem.DISTANCE_TOO_FAST ->
        Sentence(R.string.trip_edit_problem_distance_too_fast, MAX_SPEED_KMH.toInt())
}

/** The line under Business and Personal that says who chose. */
internal fun KindSource.noteRes(): Int = when (this) {
    KindSource.BY_YOU -> R.string.trip_edit_kind_by_you
    KindSource.BY_SCHEDULE -> R.string.trip_edit_kind_by_schedule
    KindSource.AS_SAVED -> R.string.trip_edit_kind_as_saved
    KindSource.NEEDS_START -> R.string.trip_edit_kind_needs_start
    KindSource.NOT_KNOWN -> R.string.trip_edit_kind_not_known
}
