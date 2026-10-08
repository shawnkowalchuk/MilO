package com.shawnkowalchuk.milo.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.trip.LabelChoices
import com.shawnkowalchuk.milo.core.trip.cleanLabel
import com.shawnkowalchuk.milo.core.trip.labelChoices
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.trip.TripRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The label being chosen for one trip.
 *
 * @param current the label the trip has, or null.
 * @param choices the labels used before, the one used where this trip ended first.
 */
data class LabelPick(val tripId: Long, val current: String?, val choices: LabelChoices) {
    /**
     * What the dialog starts on: the trip's own label, or else the one used where it ended.
     * That one is only chosen in the dialog: the trip has it once Save is pressed (Shawn's
     * answer of 2026-10-08, "Only suggest it").
     */
    val initial: String? get() = current ?: choices.suggested
}

/**
 * @param pick the dialog's choices while it is open, or null.
 * @param saveFailed true after a label that could not be read or saved. The Trips screen says
 * so as it does for a failed change, and the Log screen says why.
 */
data class TripLabelUiState(val pick: LabelPick? = null, val saveFailed: Boolean = false)

/**
 * The label of a trip on the Trips screen (Shawn's request of 2026-10-08): a dialog with the
 * labels used before, the one used where the trip ended first and already chosen, and a field
 * for a new one. A view model of its own, beside [TripsViewModel], which is at its size limit.
 *
 * Every label that is saved, refused or failed leaves one line in the event log, as every
 * other change by hand on this screen does (`TripCorrections`).
 *
 * @param clock wall-clock milliseconds.
 */
class TripLabelViewModel(
    private val trips: TripRepository,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TripLabelUiState())
    val state: StateFlow<TripLabelUiState> = mutableState.asStateFlow()

    /** "Label" was pressed under a trip: its choices are read, and the dialog opens. */
    fun onLabel(tripId: Long) {
        viewModelScope.launch {
            val pick =
                try {
                    readPick(tripId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    // Every kind of failure, for the reason TripCorrections gives.
                    val what = "Trip $tripId: the labels to choose from could not be read"
                    eventLog.add(clock(), EventCategory.ERROR, what, failure.stackTraceToString())
                    null
                }
            mutableState.value = TripLabelUiState(pick = pick, saveFailed = pick == null)
        }
    }

    /**
     * Save was pressed with [chosen]: a label from the list, one typed in, or null for none.
     * A typed label that is one of the list's but for its capitals is saved as the list writes
     * it, so that one place does not end up with two labels. Nothing is written if the trip
     * already has it.
     */
    fun onSave(chosen: String?) {
        val pick = mutableState.value.pick ?: return
        mutableState.update { it.copy(pick = null) }
        val cleaned = chosen?.let(::cleanLabel)
        val label = pick.choices.used.firstOrNull { it.equals(cleaned, ignoreCase = true) }
            ?: cleaned
        if (label == pick.current) return
        viewModelScope.launch {
            val saved = save(pick, label)
            mutableState.update { it.copy(saveFailed = !saved) }
        }
    }

    /** Cancel, Back or a press outside the dialog: nothing changes. */
    fun onDismiss() {
        mutableState.update { it.copy(pick = null) }
    }

    private suspend fun readPick(tripId: Long): LabelPick? {
        val trip = trips.findTrip(tripId) ?: return null
        val choices = labelChoices(trip.endLatitude, trip.endLongitude, trips.labelledTrips())
        return LabelPick(tripId, trip.label, choices)
    }

    private suspend fun save(pick: LabelPick, label: String?): Boolean {
        val saved =
            try {
                trips.setLabel(pick.tripId, label)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val what = "Trip ${pick.tripId}: the label failed in storage"
                eventLog.add(clock(), EventCategory.ERROR, what, failure.stackTraceToString())
                return false
            }
        eventLog.add(clock(), EventCategory.TRIP, labelledText(pick, label, saved))
        return saved
    }
}

/** The event-log line for one label saved by hand, or refused. */
internal fun labelledText(pick: LabelPick, label: String?, saved: Boolean): String {
    val id = pick.tripId
    if (!saved) {
        return "Trip $id: label refused on the Trips screen: the trip is still being recorded, " +
            "or no longer stored. Nothing changed"
    }
    val was = pick.current?.let { "\"$it\"" } ?: "none"
    val where =
        when (pick.choices.suggested) {
            null -> ""
            label -> ", the label used where it ended before"
            else -> ", not the label used where it ended before"
        }
    return if (label == null) {
        "Trip $id: label taken away on the Trips screen (was $was)"
    } else {
        "Trip $id: labelled \"$label\" on the Trips screen (was $was$where)"
    }
}
