package com.shawnkowalchuk.milo.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shawnkowalchuk.milo.core.util.monthOf
import com.shawnkowalchuk.milo.core.util.monthSpan
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripRepository
import com.shawnkowalchuk.milo.platform.address.OpenTripStart
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** How long the state stays current after the screen stopped watching it (a rotation). */
private const val KEEP_WATCHING_MS = 5_000L

/**
 * What the Trips screen shows.
 *
 * @param month the month on screen.
 * @param zone the time zone the month and its days are worked out in.
 * @param canStepForward false on the current month: there is nothing later to show.
 * @param summary the month's trips, or null while they are being read.
 */
data class TripsUiState(
    val month: YearMonth,
    val zone: ZoneId,
    val canStepForward: Boolean,
    val showDiscarded: Boolean,
    val summary: MonthSummary?,
)

/**
 * The Trips screen's link to the stored trips. It opens on the current month and steps one
 * month at a time. Only the month on screen is read from storage, by its span of time, so a
 * year of trips costs no more than a week of them.
 *
 * @param tripActivity the trip controller's state, for the running distance of a trip in
 * progress.
 * @param openTripStart where the trip in progress started, from the address lookup.
 * @param lookUpAddresses asks for the addresses that finished trips still lack. Called each
 * time the screen comes to the front; a plain function, like the ones for navigation.
 * @param clock and [zone] are read again each time the screen comes to the front: the phone can
 * cross midnight, the end of a month or a time zone while MilO is open.
 */
class TripsViewModel(
    private val trips: TripRepository,
    tripActivity: StateFlow<TripActivity>,
    openTripStart: StateFlow<OpenTripStart?>,
    private val lookUpAddresses: () -> Unit,
    private val clock: () -> Long,
    private val zone: () -> ZoneId,
) : ViewModel() {
    /** What Shawn has chosen, and the month and zone it is measured against. */
    private data class Choice(
        val shown: YearMonth,
        val current: YearMonth,
        val zone: ZoneId,
        val showDiscarded: Boolean = false,
    )

    /** The trips of one month, labelled with the month and zone they were read for. */
    private data class MonthTrips(val month: YearMonth, val zone: ZoneId, val trips: List<Trip>)

    private val choice = MutableStateFlow(openingChoice())

    // flatMapLatest is how a Flow switches to a new query when the month changes. It is marked
    // experimental by the coroutines library and has no stable equivalent.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthTrips: Flow<MonthTrips> =
        choice
            .map { it.shown to it.zone }
            .distinctUntilChanged()
            .flatMapLatest { (month, zone) ->
                val span = monthSpan(month, zone)
                trips
                    .observeTripsStartedBetween(span.fromMs, span.untilMs)
                    .map { MonthTrips(month, zone, it) }
            }

    val state: StateFlow<TripsUiState> =
        combine(choice, monthTrips, tripActivity, openTripStart) { chosen, read, activity, start ->
            chosen.toUiState(
                // Right after a step the trips in hand are still the previous month's. They are
                // not shown under the new month's name: the screen says it is reading.
                summary =
                    if (read.month == chosen.shown && read.zone == chosen.zone) {
                        monthSummary(
                            trips = read.trips,
                            zone = chosen.zone,
                            showDiscarded = chosen.showDiscarded,
                            liveTripId = activity.trip?.tripId,
                            liveDistanceMetres = activity.trip?.distanceMetres,
                            liveStart = start,
                        )
                    } else {
                        null
                    },
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(KEEP_WATCHING_MS),
            choice.value.toUiState(summary = null),
        )

    fun onPreviousMonth() {
        choice.update { it.copy(shown = stepMonth(it.shown, months = -1, current = it.current)) }
    }

    fun onNextMonth() {
        choice.update { it.copy(shown = stepMonth(it.shown, months = 1, current = it.current)) }
    }

    fun onShowDiscarded(show: Boolean) {
        choice.update { it.copy(showDiscarded = show) }
    }

    /**
     * Works out again which month is the current one; the month on screen stays where it is.
     * Also has the missing addresses looked up: this screen is where they are read, and the
     * phone may have had no network when the trips ended.
     */
    fun onCameToFront() {
        lookUpAddresses()
        val now = openingChoice()
        choice.update {
            it.copy(current = now.current, zone = now.zone, shown = minOf(it.shown, now.current))
        }
    }

    private fun openingChoice(): Choice {
        val zoneNow = zone()
        val current = monthOf(clock(), zoneNow)
        return Choice(shown = current, current = current, zone = zoneNow)
    }

    private fun Choice.toUiState(summary: MonthSummary?) = TripsUiState(
        month = shown,
        zone = zone,
        canStepForward = canStepForward(shown, current),
        showDiscarded = showDiscarded,
        summary = summary,
    )
}
