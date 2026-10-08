package com.shawnkowalchuk.milo.feature.tripedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

// What the event log calls the occasion on which this screen asks for missing addresses.
private const val RECORDED_VALUES_RESTORED = "a trip's recorded values were restored"

/**
 * The edit screen's link to the stored trips. It holds the form while it is being filled in,
 * and nothing is stored until Save is pressed: unlike a setting, a trip's times, addresses and
 * distance only make sense together, and one save is one line in the event log.
 *
 * The trip is read once, when the screen opens. What the form then shows is that trip with
 * Shawn's changes on top, and a save writes only what he changed, so an address the lookup
 * finds while the form is open is not overwritten by a field he never touched.
 *
 * @param tripId the trip to edit, or null to add one that MilO missed.
 * @param editing checks and makes the save, the add and the restore, and logs them.
 * @param unit the unit chosen in Settings, as the whole app holds it. It is read once, when
 * the screen opens: the distance field is in that unit for as long as the form is open.
 * @param lookUpAddresses asks for the addresses that finished trips lack, with the reason in
 * words for the event log. Called after a restore, which hands typed addresses back to the
 * lookup; a plain function, like the ones for navigation.
 * @param clock and [zone] are read once, when the screen opens.
 */
class TripEditViewModel(
    private val tripId: Long?,
    private val editing: TripEditing,
    private val unit: StateFlow<DistanceUnit>,
    private val lookUpAddresses: (reason: String) -> Unit,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    /** Where the screen is: reading, with nothing to edit, or with the form open. */
    private sealed interface Model {
        data object Reading : Model

        data object NotEditable : Model

        /**
         * @param check what the last press on Save checked the form against, if it found
         * something wrong.
         * @param refusals how many presses have stored nothing so far.
         * @param busy true while a save or a restore is under way: a second press waits.
         * @param savedStartMs when the trip starts as it was stored, once [closing] is true.
         */
        data class Editing(
            val session: EditSession,
            val form: TripForm,
            val check: FormCheck? = null,
            val saveFailed: Boolean = false,
            val refusals: Int = 0,
            val busy: Boolean = false,
            val closing: Boolean = false,
            val savedStartMs: Long? = null,
        ) : Model
    }

    private val model = MutableStateFlow<Model>(Model.Reading)

    val state: StateFlow<TripEditUiState> =
        model
            .map { now ->
                when (now) {
                    Model.Reading -> TripEditUiState.Reading

                    Model.NotEditable -> TripEditUiState.NotEditable

                    is Model.Editing ->
                        tripEditUiState(
                            session = now.session,
                            form = now.form,
                            check = now.check,
                            saveFailed = now.saveFailed,
                            refusals = now.refusals,
                            closing = now.closing,
                            savedStartMs = now.savedStartMs,
                        )
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(KEEP_WATCHING_MS),
                TripEditUiState.Reading,
            )

    init {
        viewModelScope.launch { model.value = open() }
    }

    private suspend fun open(): Model {
        val zoneNow = zone()
        val nowMs = clock()
        val stored = if (tripId == null) null else editing.findEditable(tripId)
        if (tripId != null && stored == null) return Model.NotEditable
        // The unit as it is when the form opens. The form keeps it while it is open: what is
        // typed in the distance field is read in the unit its label names.
        val session =
            EditSession(stored, editing.schedule(), zoneNow, nowMs, unit = unit.value)
        return Model.Editing(session, session.openedForm())
    }

    fun onDate(date: LocalDate) = change { it.copy(date = date) }

    /** The time picker's answer for the start: an hour from 0 to 23 and a minute. */
    fun onStart(hour: Int, minute: Int) = change { it.copy(start = LocalTime.of(hour, minute)) }

    /** The time picker's answer for the end. */
    fun onEnd(hour: Int, minute: Int) = change { it.copy(end = LocalTime.of(hour, minute)) }

    /** The switch "Ended the next day": whether the trip ran past midnight. */
    fun onEndsNextDay(nextDay: Boolean) = change { it.endingNextDay(nextDay) }

    fun onFrom(text: String) = change { it.copy(from = text) }

    fun onTo(text: String) = change { it.copy(to = text) }

    fun onKilometres(text: String) = change { it.copy(kilometres = text) }

    /**
     * Shawn pressed Business or Personal. A press on the one that is already shown is not a
     * choice: it would mark the trip as set by hand without changing anything he can see.
     */
    fun onCategory(category: TripCategory) {
        model.update { now ->
            if (now !is Model.Editing || kindShown(now.form, now.session).first == category) {
                now
            } else {
                now.copy(form = now.form.copy(chosenCategory = category))
            }
        }
    }

    /** Checks the form and, if nothing is wrong with it, stores it. The screen then leaves. */
    fun onSave() = store { now ->
        val session = now.session
        val result =
            if (session.stored == null) {
                editing.add(now.form, session.schedule, session.zone)
            } else {
                editing.save(session.stored, now.form, session.schedule, session.zone)
            }
        when (result) {
            SaveResult.Stored -> Stored.Done(now.form.startedAtMs(session.stored, session.zone))
            is SaveResult.Invalid -> Stored.NotYet(result.check)
            SaveResult.Failed -> Stored.Failed
        }
    }

    /** Puts back what MilO recorded, and has the addresses it removes looked up again. */
    fun onRestore() = store { now ->
        // The button is only shown for a stored trip.
        val stored = now.session.stored ?: return@store Stored.Failed
        val restored = editing.restore(stored.id, now.session.schedule, now.session.zone)
        if (restored) lookUpAddresses(RECORDED_VALUES_RESTORED)
        // The trip is back where MilO recorded it, which can be another month than it was in.
        if (restored) Stored.Done(stored.recordedStartedAtMs) else Stored.Failed
    }

    /** What became of a press that stores. */
    private sealed interface Stored {
        /** @param startedAtMs when the trip starts as it is stored now. */
        data class Done(val startedAtMs: Long?) : Stored

        data class NotYet(val check: FormCheck) : Stored

        data object Failed : Stored
    }

    private fun change(next: (TripForm) -> TripForm) {
        model.update { now ->
            // Whatever is typed takes the last failure off the screen: it was about a press.
            if (now is Model.Editing) now.copy(form = next(now.form), saveFailed = false) else now
        }
    }

    /**
     * Runs one press that stores, one at a time: a second press while the first is under way
     * does nothing, so a double tap on Save cannot add a trip twice.
     */
    private fun store(press: suspend (Model.Editing) -> Stored) {
        val now = model.value
        if (now !is Model.Editing || now.busy || now.closing) return
        model.value = now.copy(busy = true)
        viewModelScope.launch {
            val stored = press(now)
            model.update { after ->
                if (after !is Model.Editing) {
                    after
                } else {
                    when (stored) {
                        is Stored.Done ->
                            after.copy(
                                busy = false,
                                closing = true,
                                savedStartMs = stored.startedAtMs,
                            )

                        is Stored.NotYet ->
                            after.copy(
                                busy = false,
                                check = stored.check,
                                saveFailed = false,
                                refusals = after.refusals + 1,
                            )

                        Stored.Failed ->
                            after.copy(
                                busy = false,
                                saveFailed = true,
                                refusals = after.refusals + 1,
                            )
                    }
                }
            }
        }
    }
}
