package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.schedule.NOT_FILED
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.schedule.refileTrip
import com.shawnkowalchuk.milo.data.trip.RecordedValues
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.editedTrip
import com.shawnkowalchuk.milo.data.trip.recordedValues
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// What the edit screen shows, worked out from what the form was opened with and what has been
// picked and typed since. A pure function, so it is tested without a phone.

/** Who chose the Business or Personal the form shows. The line under the two choices says it. */
enum class KindSource {
    /** Shawn: he pressed one in this form, or the trip was set by hand before. */
    BY_YOU,

    /** MilO, from the work schedule and the start time the form shows. */
    BY_SCHEDULE,

    /** Nobody anew: it is what the trip was saved as, and its start has not been changed. */
    AS_SAVED,

    /** Nobody yet: a trip is being added and has no start time to sort it by. */
    NEEDS_START,

    /** Nobody can: the trip is not sorted, or the work schedule cannot be read. */
    NOT_KNOWN,
}

/**
 * What the form was opened with. It stays the same for as long as the screen is open.
 *
 * @param stored the trip that is being edited, as it was read when the form opened, or null
 * when a trip is being added.
 * @param schedule the work schedule, or null if the settings could not be read.
 * @param zone the phone's time zone. The form reads and writes every time in it.
 * @param openedAtMs when the form was opened: the day no date may lie after, and where the
 * clock dial starts for a time that has not been chosen.
 */
data class EditSession(
    val stored: Trip?,
    val schedule: WorkSchedule?,
    val zone: ZoneId,
    val openedAtMs: Long,
) {
    /** The form as it is when the screen opens: the stored trip's, or the empty one. */
    fun openedForm(): TripForm =
        if (stored == null) blankForm(openedAtMs, zone) else formFor(stored, zone)
}

/** What the edit screen shows. */
sealed interface TripEditUiState {
    /** The trip is being read. */
    data object Reading : TripEditUiState

    /** There is nothing to edit: no such trip, or it is not a finished one (any more). */
    data object NotEditable : TripEditUiState

    /**
     * The form.
     *
     * @param adding true when a missed trip is typed in, false when a stored one is edited.
     * @param addedByHand true when the trip that is edited was itself added by hand.
     * @param latestDate today: the calendar offers no later day.
     * @param start and [end] are null while a time has not been chosen.
     * @param startDial and [endDial] are where the clock dial opens for each.
     * @param endsNextDay whether the switch "Ended the next day" is on: true for a trip that
     * ran past midnight.
     * @param laterEndDate the day the trip ends on, if that is not the day it starts on.
     * @param from and [to] are what the address fields start with.
     * @param kilometres what the distance field starts with if it was typed in already, or null
     * if the field is untouched: it then shows [storedMetres], written by the screen.
     * @param category the Business or Personal a save would store, and [kindSource] who chose.
     * @param problems what the last press on Save found wrong, kept up to date as the form is
     * put right. Empty before the first press. Each is said in the card it is about
     * ([timeProblems], [distanceProblem]).
     * @param saveFailed true after a save or a restore that storage did not take.
     * @param refusals how many presses on Save or on Restore have stored nothing so far. The
     * screen goes by it to tell the answer to a press from a screen that is merely drawn again.
     * @param recorded what MilO recorded of the trip, if it was edited since: the screen offers
     * to put it back. Null otherwise.
     * @param unsaved true while the form holds something picked or typed that is not what it
     * opened with and has not been stored: the app asks before the screen is left.
     * @param closing true once the form has been stored: the screen leaves.
     * @param savedStartMs when the trip starts as it was just stored, once [closing] is true.
     * The Trips screen then shows the month it is in.
     */
    data class Ready(
        val adding: Boolean,
        val addedByHand: Boolean,
        val zone: ZoneId,
        val date: LocalDate,
        val latestDate: LocalDate,
        val start: LocalTime?,
        val end: LocalTime?,
        val startDial: LocalTime,
        val endDial: LocalTime,
        val endsNextDay: Boolean,
        val laterEndDate: LocalDate?,
        val from: String,
        val to: String,
        val kilometres: String?,
        val storedMetres: Double?,
        val category: TripCategory?,
        val kindSource: KindSource,
        val problems: List<FormProblem>,
        val saveFailed: Boolean,
        val refusals: Int,
        val recorded: RecordedValues?,
        val unsaved: Boolean,
        val closing: Boolean,
        val savedStartMs: Long?,
    ) : TripEditUiState {
        /** What is wrong with the day and the two times: said in the card "When". */
        val timeProblems: List<FormProblem> get() = problems.filter { it.part == FormPart.TIMES }

        /**
         * What is wrong with the distance, of which there is never more than one thing: said
         * under the Kilometres field.
         */
        val distanceProblem: FormProblem?
            get() = problems.firstOrNull { it.part == FormPart.DISTANCE }

        /**
         * The part of the form the first thing to put right is in: where the screen moves to
         * after a press on Save that stored nothing. Null if nothing is wrong with the form.
         */
        val firstProblemPart: FormPart? get() = problems.firstOrNull()?.part
    }
}

/**
 * The Business or Personal a save of [form] would store, and who chose it. It is worked out by
 * the functions the save itself uses (`editedTrip`, `refileTrip`), so the form cannot show one
 * thing and store another.
 */
internal fun kindShown(form: TripForm, session: EditSession): Pair<TripCategory?, KindSource> {
    val stored = session.stored
    val zone = session.zone
    val start = form.startedAtMs(stored, zone)
    if (stored != null) {
        val after = editedTrip(stored, form.toEdit(stored, zone), session.schedule, zone)
        val source =
            when {
                after.categorySetByHand -> KindSource.BY_YOU
                after.category == null -> KindSource.NOT_KNOWN
                start != stored.startedAtMs -> KindSource.BY_SCHEDULE
                else -> KindSource.AS_SAVED
            }
        return after.category to source
    }
    val chosen = form.chosenCategory
    if (chosen != null) return chosen to KindSource.BY_YOU
    if (start == null) return null to KindSource.NEEDS_START
    val filed =
        refileTrip(
            stored = NOT_FILED,
            startChanged = true,
            endChanged = true,
            startedAtMs = start,
            endedAtMs = form.endedAtMs(stored = null, zone),
            chosen = null,
            schedule = session.schedule,
            zone = zone,
        )
    val source = if (filed.category == null) KindSource.NOT_KNOWN else KindSource.BY_SCHEDULE
    return filed.category to source
}

/**
 * The form as the screen shows it.
 *
 * @param check what the last press on Save checked the form against, or null before the first.
 * @param savedStartMs when the trip starts as it was stored, or null while the form has not
 * been stored. Once it has, nothing in it is unsaved any more.
 */
internal fun tripEditUiState(
    session: EditSession,
    form: TripForm,
    check: FormCheck?,
    saveFailed: Boolean,
    refusals: Int,
    closing: Boolean,
    savedStartMs: Long? = null,
): TripEditUiState.Ready {
    val stored = session.stored
    val opened = Instant.ofEpochMilli(session.openedAtMs).atZone(session.zone)
    val openedTime = opened.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
    val (category, source) = kindShown(form, session)
    return TripEditUiState.Ready(
        adding = stored == null,
        addedByHand = stored?.addedByHand == true,
        zone = session.zone,
        date = form.date,
        latestDate = opened.toLocalDate(),
        start = form.start,
        end = form.end,
        startDial = form.start ?: openedTime,
        // An end that is not chosen yet is most likely a little after the start.
        endDial = form.end ?: form.start ?: openedTime,
        endsNextDay = form.endDayOffset > 0,
        laterEndDate = form.laterEndDate,
        from = form.from ?: stored?.startAddress.orEmpty(),
        to = form.to ?: stored?.endAddress.orEmpty(),
        kilometres = form.kilometres,
        storedMetres = stored?.distanceMetres,
        category = category,
        kindSource = source,
        problems = check?.let { formProblems(form, stored, it, session.zone) }.orEmpty(),
        saveFailed = saveFailed,
        refusals = refusals,
        recorded = stored?.recordedValues,
        unsaved = !closing && form.holdsUnsavedWork(session.openedForm(), stored),
        closing = closing,
        savedStartMs = savedStartMs,
    )
}
