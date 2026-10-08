package com.shawnkowalchuk.milo.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.shawnkowalchuk.milo.feature.trips.TripCorrections
import com.shawnkowalchuk.milo.feature.trips.TripLabelViewModel
import com.shawnkowalchuk.milo.feature.trips.TripsScreen
import com.shawnkowalchuk.milo.feature.trips.TripsViewModel
import java.time.ZoneId

/**
 * The Trips screen with its two view models: the month's trips, and since 2026-10-08 the
 * choice of a trip's label. For [MiloNavigation], which is at its size limit
 * (ENGINEERING_STANDARDS section 3).
 *
 * @param savedTripStartMs when a trip just saved on the edit screen starts, or null, and
 * [onSavedTripShown] that its month and day are on screen: MiloNavigation holds it, because
 * the edit screen sets it.
 */
@Composable
internal fun TripsEntry(
    container: AppContainer,
    backStack: NavBackStack<NavKey>,
    savedTripStartMs: Long?,
    onSavedTripShown: () -> Unit,
) {
    TripsScreen(
        viewModel =
            viewModel(
                factory = viewModelFactory {
                    initializer {
                        TripsViewModel(
                            trips = container.tripRepository,
                            corrections =
                                TripCorrections(
                                    trips = container.tripRepository,
                                    eventLog = container.eventLogRepository,
                                    clock = System::currentTimeMillis,
                                ),
                            tripActivity = container.tripController.activity,
                            openTripStart = container.tripAddresses.openTripStart,
                            unit = container.shownUnit.unit,
                            sentReports = container.sentReportRepository.observeSent(),
                            lookUpAddresses = container.tripAddresses::catchUp,
                            clock = System::currentTimeMillis,
                            zone = ZoneId::systemDefault,
                        )
                    }
                },
            ),
        labels =
            viewModel(
                factory = viewModelFactory {
                    initializer {
                        TripLabelViewModel(
                            trips = container.tripRepository,
                            eventLog = container.eventLogRepository,
                            clock = System::currentTimeMillis,
                        )
                    }
                },
            ),
        savedTripStartMs = savedTripStartMs,
        onSavedTripShown = onSavedTripShown,
        onEditTrip = { tripId -> backStack.openOnTop(TripEditKey(tripId)) },
        onAddTrip = { backStack.openOnTop(TripEditKey(tripId = null)) },
        onOpenReport = { month -> backStack.openOnTop(ReportKey(month.year, month.monthValue)) },
    )
}
