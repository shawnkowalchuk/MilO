package com.shawnkowalchuk.milo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.shawnkowalchuk.milo.feature.eventlog.EventLogScreen
import com.shawnkowalchuk.milo.feature.eventlog.EventLogViewModel
import com.shawnkowalchuk.milo.feature.home.HomeScreen
import com.shawnkowalchuk.milo.feature.home.HomeViewModel
import com.shawnkowalchuk.milo.feature.pairing.PairingScreen
import com.shawnkowalchuk.milo.feature.pairing.PairingViewModel
import com.shawnkowalchuk.milo.feature.settings.SettingsScreen
import com.shawnkowalchuk.milo.feature.settings.SettingsViewModel
import com.shawnkowalchuk.milo.feature.setup.SetupScreen
import com.shawnkowalchuk.milo.feature.setup.SetupViewModel
import com.shawnkowalchuk.milo.feature.tripedit.TripEditScreen
import com.shawnkowalchuk.milo.feature.tripedit.TripEditViewModel
import com.shawnkowalchuk.milo.feature.tripedit.TripEditing
import com.shawnkowalchuk.milo.feature.trips.TripCorrections
import com.shawnkowalchuk.milo.feature.trips.TripsScreen
import com.shawnkowalchuk.milo.feature.trips.TripsViewModel
import java.time.ZoneId

/**
 * Shows the screen on top of [backStack]. This is the only place that knows about more than one
 * feature: features never navigate to, or import from, each other, so a screen that leads to
 * another one is handed a plain function to call.
 *
 * It is also where each screen's ViewModel is given what it needs from the [AppContainer]. That
 * is the manual dependency injection for screens: a feature never reaches for the container
 * itself, so it does not depend on the `app` package. A ViewModel lives as long as its screen is
 * on the back stack, so the Trips screen opens on the current month every time it is entered.
 *
 * @param leaveBack Back was pressed, Android's or a screen's own arrow. It is handed what Back
 * does and decides when: at once, or after a question if something typed would be lost.
 * @param onUnsavedWork a screen says whether it holds something typed and not saved.
 */
@Composable
fun MiloNavigation(
    container: AppContainer,
    backStack: NavBackStack<NavKey>,
    leaveBack: (atOnce: () -> Unit) -> Unit,
    onUnsavedWork: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // When the trip that was just saved on the edit screen starts, until Trips has shown the
    // month it is in. The two screens are different features, so the app carries it across.
    var savedTripStartMs by rememberSaveable { mutableStateOf<Long?>(null) }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { leaveBack { backStack.closeTop() } },
        entryDecorators =
            listOf(
                // Keeps each screen's saved state (scroll position, open rows) while it is on
                // the back stack, and gives each screen a ViewModel store of its own.
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<HomeKey> {
                    HomeScreen(
                        viewModel =
                            viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        HomeViewModel(
                                            controller = container.tripController,
                                            checklist = container.setupChecklist,
                                            trips = container.tripRepository,
                                            clock = System::currentTimeMillis,
                                            zone = ZoneId::systemDefault,
                                        )
                                    }
                                },
                            ),
                        onOpenSetup = { backStack.showTopLevel(SetupKey, HomeKey) },
                        onOpenSettings = { backStack.openOnTop(SettingsKey) },
                    )
                }
                entry<TripsKey> {
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
                                            sentReports =
                                                container.sentReportRepository.observeSent(),
                                            lookUpAddresses = container.tripAddresses::catchUp,
                                            clock = System::currentTimeMillis,
                                            zone = ZoneId::systemDefault,
                                        )
                                    }
                                },
                            ),
                        savedTripStartMs = savedTripStartMs,
                        onSavedTripShown = { savedTripStartMs = null },
                        onEditTrip = { tripId -> backStack.openOnTop(TripEditKey(tripId)) },
                        onAddTrip = { backStack.openOnTop(TripEditKey(tripId = null)) },
                        onOpenReport = { month ->
                            backStack.openOnTop(ReportKey(month.year, month.monthValue))
                        },
                    )
                }
                entry<ReportKey> { key -> ReportEntry(container, backStack, key) }
                entry<TripEditKey> { key ->
                    TripEditScreen(
                        viewModel =
                            viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        TripEditViewModel(
                                            tripId = key.tripId,
                                            editing =
                                                TripEditing(
                                                    trips = container.tripRepository,
                                                    settings = container.settingsStore,
                                                    eventLog = container.eventLogRepository,
                                                    clock = System::currentTimeMillis,
                                                ),
                                            lookUpAddresses = container.tripAddresses::catchUp,
                                            clock = System::currentTimeMillis,
                                            zone = ZoneId::systemDefault,
                                        )
                                    }
                                },
                            ),
                        onBack = { leaveBack { backStack.closeIfOnTop(key) } },
                        onSaved = { startedAtMs ->
                            // Only while this screen is the one that is left for Trips. If a
                            // button of the bottom bar got in first, Trips is not what comes
                            // next, and it opens on the current month when it is entered.
                            if (backStack.lastOrNull() == key) savedTripStartMs = startedAtMs
                            backStack.closeIfOnTop(key)
                        },
                        onUnsavedWork = onUnsavedWork,
                    )
                }
                entry<SetupKey> {
                    SetupScreen(
                        viewModel =
                            viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        SetupViewModel(
                                            container.setupChecklist,
                                            container.systemScreens,
                                        )
                                    }
                                },
                            ),
                        onOpenPairing = { backStack.openOnTop(PairingKey) },
                    )
                }
                entry<PairingKey> {
                    PairingScreen(
                        viewModel =
                            viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        PairingViewModel(
                                            pairing = container.truckPairing,
                                            settings = container.settingsStore,
                                            isLocationOn = {
                                                container.tripPreflight.facts().locationSwitchedOn
                                            },
                                            bluetoothSwitched = container.bluetoothSwitched,
                                            screens = container.systemScreens,
                                        )
                                    }
                                },
                            ),
                        onBack = { backStack.closeIfOnTop(PairingKey) },
                    )
                }
                entry<SettingsKey> {
                    SettingsScreen(
                        viewModel =
                            viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        SettingsViewModel(
                                            settings = container.settingsStore,
                                            ownSound = container.ownTripSound,
                                            playSound = container.soundPreview::play,
                                            armDrivingAlert = container.drivingAlert::arm,
                                            eventLog = container.eventLogRepository,
                                            clock = System::currentTimeMillis,
                                        )
                                    }
                                },
                            ),
                        onChangeTruck = { backStack.openOnTop(PairingKey) },
                        onBack = { backStack.closeIfOnTop(SettingsKey) },
                    )
                }
                entry<LogKey> {
                    EventLogScreen(
                        viewModel =
                            viewModel(
                                factory = viewModelFactory {
                                    initializer { EventLogViewModel(container.eventLogRepository) }
                                },
                            ),
                    )
                }
            },
    )
}
