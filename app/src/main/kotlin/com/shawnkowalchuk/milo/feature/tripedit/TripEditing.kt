package com.shawnkowalchuk.milo.feature.tripedit

import com.shawnkowalchuk.milo.core.schedule.WorkSchedule
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.data.trip.ByHandOutcome
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.data.trip.addedText
import com.shawnkowalchuk.milo.data.trip.canBeEdited
import com.shawnkowalchuk.milo.data.trip.editText
import com.shawnkowalchuk.milo.data.trip.restoreText
import java.io.IOException
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

/** What became of a press on the form's Save button. */
sealed interface SaveResult {
    /** The form was stored, or it asked for nothing and so nothing needed storing. */
    data object Stored : SaveResult

    /**
     * Nothing was stored: the form has [problems]. [check] is what it was checked against, so
     * the screen can go on checking against the same moment while the form is put right.
     */
    data class Invalid(val problems: List<FormProblem>, val check: FormCheck) : SaveResult

    /** Nothing was stored: storage refused or failed. The event log says which. */
    data object Failed : SaveResult
}

/**
 * What the form takes from the settings when it opens.
 *
 * @param schedule the work schedule, for sorting a trip whose start is typed or changed.
 * @param unit the unit chosen in Settings, which the distance field is in. It comes from the
 * settings file itself and not from the unit the app holds in memory, which is kilometres for
 * the first moment of a process: a form keeps its unit for as long as it is open, so one
 * opened in that moment would stay in kilometres with miles chosen.
 */
data class FormSettings(val schedule: WorkSchedule, val unit: DistanceUnit)

/**
 * Carries out what the edit screen asks for (save an edit, add a trip, restore the recorded
 * values) and writes each one to the event log. A trip's figures decide what accounts pays, so
 * every change by hand leaves a line that names each value before and after, and an attempt
 * that changed nothing leaves a line too.
 *
 * The form is checked here, at the moment of the save and against the clock and the trip that
 * is being recorded as they are then, not when the form was opened.
 *
 * @param clock wall-clock milliseconds.
 */
class TripEditing(
    private val trips: TripRepository,
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) {
    /**
     * The trip with this id if it can be edited: a finished one. Null for any other trip, for
     * none, and if storage fails, which is written to the event log.
     */
    suspend fun findEditable(tripId: Long): Trip? = guarded("Trip $tripId: could not be read") {
        trips.findTrip(tripId)?.takeIf { it.canBeEdited }
    }

    /**
     * What the form takes from the settings as they are now, in one read. Null if the settings
     * cannot be read: the form then lets Shawn choose Business or Personal himself, and a trip
     * he does not choose for is left unsorted for the catch-up.
     */
    suspend fun formSettings(): FormSettings? = try {
        settings.current().let { FormSettings(it.schedule, it.distanceUnit) }
    } catch (unreadable: IOException) {
        val what = "The edit screen could not read the work schedule and the unit"
        eventLog.add(clock(), EventCategory.ERROR, what, unreadable.stackTraceToString())
        null
    }

    /** Saves [form] over [stored], the trip the form was opened for. */
    suspend fun save(
        stored: Trip,
        form: TripForm,
        schedule: WorkSchedule?,
        zone: ZoneId,
    ): SaveResult {
        val failure = "Trip ${stored.id}: edit failed in storage"
        return checkedAndStored(form, stored, zone, failure) {
            val edit = form.toEdit(stored, zone)
            val outcome =
                guarded(failure) { trips.editByHand(stored.id, edit, schedule, zone) }
                    ?: return@checkedAndStored false
            eventLog.add(clock(), EventCategory.TRIP, editText(stored.id, outcome, zone))
            outcome !is ByHandOutcome.Refused
        }
    }

    /** Adds the trip [form] describes: one that MilO missed. */
    suspend fun add(form: TripForm, schedule: WorkSchedule?, zone: ZoneId): SaveResult {
        val failure = "A trip typed in by hand failed in storage"
        return checkedAndStored(form, stored = null, zone, failure) {
            // A form without a problem has both times and a distance, so this is never null.
            val typed = checkNotNull(form.toTypedTrip(zone)) { "A checked form lacks a value" }
            val added =
                guarded(failure) { trips.addByHand(typed, schedule, zone) }
                    ?: return@checkedAndStored false
            eventLog.add(clock(), EventCategory.TRIP, addedText(added, zone))
            true
        }
    }

    /**
     * Puts back what MilO recorded of an edited trip.
     *
     * @return true if it was done. False if storage refused (the trip is not an edited,
     * finished one any more) or failed; the event log says which, and nothing was changed.
     */
    suspend fun restore(tripId: Long, schedule: WorkSchedule?, zone: ZoneId): Boolean {
        val outcome =
            guarded("Trip $tripId: restore recorded values failed in storage") {
                trips.restoreRecorded(tripId, schedule, zone)
            } ?: return false
        eventLog.add(clock(), EventCategory.TRIP, restoreText(tripId, outcome, zone))
        return outcome !is ByHandOutcome.Refused
    }

    /**
     * Checks [form] against the clock and the trip that is being recorded, as they are at this
     * moment, and only if nothing is wrong with it runs [store].
     *
     * @param failure what the event log says if the trips cannot be read for the check.
     * @param store makes the write and logs it; false if storage refused or failed.
     */
    private suspend fun checkedAndStored(
        form: TripForm,
        stored: Trip?,
        zone: ZoneId,
        failure: String,
        store: suspend () -> Boolean,
    ): SaveResult {
        val check =
            guarded(failure) { FormCheck(clock(), trips.findOpenTrip()?.startedAtMs) }
                ?: return SaveResult.Failed
        val problems = formProblems(form, stored, check, zone)
        return when {
            problems.isNotEmpty() -> SaveResult.Invalid(problems, check)
            store() -> SaveResult.Stored
            else -> SaveResult.Failed
        }
    }

    /**
     * Runs [work], which reads or writes the trips. If that fails, whatever it threw, the
     * failure is written to the event log under [failure] and the answer is null. Left alone,
     * the exception would end the process, and the trip service runs in it.
     *
     * Only the trips are guarded. The line that records a change is written after it and
     * outside this: a change that was made is never reported to the screen as not made (a
     * second press on "Add this trip" would add the trip twice), and if the log itself cannot
     * be written, that does end the process, on purpose: a failure must not vanish.
     */
    private suspend fun <T : Any> guarded(failure: String, work: suspend () -> T?): T? = try {
        work()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        // Every kind of failure, for the reason given above.
        eventLog.add(clock(), EventCategory.ERROR, failure, failed.stackTraceToString())
        null
    }
}
