package com.shawnkowalchuk.milo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
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
import com.shawnkowalchuk.milo.feature.home.HomeSources
import com.shawnkowalchuk.milo.feature.home.HomeViewModel
import com.shawnkowalchuk.milo.feature.pairing.PairingScreen
import com.shawnkowalchuk.milo.feature.pairing.PairingViewModel
import com.shawnkowalchuk.milo.feature.settings.SettingsScreen
import com.shawnkowalchuk.milo.feature.settings.SettingsViewModel
import com.shawnkowalchuk.milo.feature.setup.SetupLinkTile
import com.shawnkowalchuk.milo.feature.setup.SetupScreen
import com.shawnkowalchuk.milo.feature.setup.SetupViewModel
import com.shawnkowalchuk.milo.feature.tripedit.TripEditScreen
import com.shawnkowalchuk.milo.feature.tripedit.TripEditViewModel
import com.shawnkowalchuk.milo.feature.tripedit.TripEditing
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
 * @param onSetupDone Setup's "Done, go to Settings", while the first start is being gone
 * through (2026-10-08); null otherwise.
 */
@Composable
fun MiloNavigation(
    container: AppContainer,
    backStack: NavBackStack<NavKey>,
    leaveBack: (atOnce: () -> Unit) -> Unit,
    onUnsavedWork: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSetupDone: (() -> Unit)? = null,
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
                                            sources = homeSources(container),
                                            clock = System::currentTimeMillis,
                                            zone = ZoneId::systemDefault,
                                        )
                                    }
                                },
                            ),
                        // On top of Home, as the bar keeps showing: Back leads to Home.
                        onOpenSetup = { backStack.openOnTop(SetupKey) },
                        onOpenPairing = { backStack.openOnTop(PairingKey) },
                        onOpenTrips = { backStack.showTopLevel(TripsKey, HomeKey) },
                        onOpenReport = { month ->
                            backStack.openOnTop(ReportKey(month.year, month.monthValue))
                        },
                    )
                }
                entry<TripsKey> {
                    TripsEntry(
                        container = container,
                        backStack = backStack,
                        savedTripStartMs = savedTripStartMs,
                        onSavedTripShown = { savedTripStartMs = null },
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
                                            unit = container.shownUnit.unit,
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
                        viewModel = viewModel(factory = setupViewModelFactory(container)),
                        onOpenPairing = { backStack.openOnTop(PairingKey) },
                        onBack = { backStack.closeIfOnTop(SetupKey) },
                        onDone = onSetupDone,
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
                    // The bar's screen directly on Home; opened on top of the Report screen by
                    // "Open Settings" anywhere higher, and then it shows the way back. While it
                    // slides away it is off the back stack, and keeps what it showed.
                    val place = backStack.indexOf(SettingsKey)
                    val lastPlace = remember { mutableIntStateOf(place) }
                    SideEffect { if (place != -1) lastPlace.intValue = place }
                    val openedOnTop = (if (place != -1) place else lastPlace.intValue) > 1
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
                                            lookAtReminder = container.reports.reminder::look,
                                            eventLog = container.eventLogRepository,
                                            clock = System::currentTimeMillis,
                                        )
                                    }
                                },
                            ),
                        dataViewModel = viewModel(factory = dataViewModelFactory(container)),
                        checkViewModel =
                            viewModel(factory = nothingRecordedViewModelFactory(container)),
                        odometerViewModel =
                            viewModel(factory = odometerViewModelFactory(container)),
                        widgetViewModel =
                            viewModel(factory = homeWidgetViewModelFactory(container)),
                        onChangeTruck = { backStack.openOnTop(PairingKey) },
                        onBack =
                            if (openedOnTop) {
                                { backStack.closeIfOnTop(SettingsKey) }
                            } else {
                                null
                            },
                        setupTile = {
                            SetupLinkTile(
                                viewModel = viewModel(factory = setupViewModelFactory(container)),
                                onOpenSetup = { backStack.openOnTop(SetupKey) },
                            )
                        },
                        versionTile = { VersionTileEntry(container, backStack) },
                    )
                }
                entry<WhatsNewKey> { WhatsNewEntry(container, backStack) }
                entry<LogKey> {
                    EventLogScreen(
                        viewModel =
                            viewModel(
                                factory = viewModelFactory {
                                    initializer {
                                        EventLogViewModel(
                                            eventLog = container.eventLogRepository,
                                            files = container.eventLogFiles,
                                            shareRequest = container.reports.handOff::toShareText,
                                            clock = System::currentTimeMillis,
                                            zone = ZoneId::systemDefault,
                                        )
                                    }
                                },
                            ),
                    )
                }
            },
    )
}

/** The Setup checklist's view model, for the Setup screen and for its tile on Settings. */
private fun setupViewModelFactory(container: AppContainer): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { SetupViewModel(container.setupChecklist, container.systemScreens) }
    }

/**
 * What the home screen reads besides the trip controller, the checklist and the stored trips:
 * the settings, the sent reports and the address lookup, each handed over as the plain flow
 * or function Home needs of it.
 */
private fun homeSources(container: AppContainer) = HomeSources(
    settings = container.settingsStore.settings,
    sentReports = container.sentReportRepository.observeSent(),
    openTripStart = container.tripAddresses.openTripStart,
    unit = container.shownUnit.unit,
    lookUpAddresses = container.tripAddresses::catchUp,
)
