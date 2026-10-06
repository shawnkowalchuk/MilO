package com.shawnkowalchuk.milo.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.MiloIcons
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
import com.shawnkowalchuk.milo.feature.trips.TripCorrections
import com.shawnkowalchuk.milo.feature.trips.TripsScreen
import com.shawnkowalchuk.milo.feature.trips.TripsViewModel
import java.time.ZoneId
import kotlinx.serialization.Serializable

// The screens, as keys of the Navigation 3 back stack. Each is @Serializable because Navigation
// 3 saves the back stack with kotlinx.serialization when Android puts MilO away.

@Serializable
data object HomeKey : NavKey

@Serializable
data object TripsKey : NavKey

@Serializable
data object SetupKey : NavKey

@Serializable
data object LogKey : NavKey

/**
 * The truck pairing screen. Not in the bottom bar: it is opened from Setup, and from Settings.
 */
@Serializable
data object PairingKey : NavKey

/** The Settings screen. Not in the bottom bar: it is opened from Home. */
@Serializable
data object SettingsKey : NavKey

/** The four screens of the bottom navigation bar, in the order the bar shows them. */
enum class TopLevelDestination(val key: NavKey, val labelRes: Int, val icon: ImageVector) {
    HOME(HomeKey, R.string.nav_home, MiloIcons.Home),
    TRIPS(TripsKey, R.string.nav_trips, MiloIcons.Trips),
    SETUP(SetupKey, R.string.nav_setup, MiloIcons.Setup),
    LOG(LogKey, R.string.nav_log, MiloIcons.Log),
}

/**
 * The bottom-bar screen the back stack is in: the nearest one under whatever is on top. With
 * the pairing screen open from Setup that is Setup, and with Settings open (or the pairing
 * screen on top of Settings) it is Home, so the bar keeps showing where Shawn came from.
 */
internal fun topLevelOf(backStack: List<NavKey>): TopLevelDestination = backStack
    .asReversed()
    .firstNotNullOfOrNull { key -> TopLevelDestination.entries.firstOrNull { it.key == key } }
    ?: TopLevelDestination.HOME

/**
 * Switches to a screen of the bottom bar. The back stack is then Home alone, or Home with that
 * screen on top of it: Back from any of the other three leads to Home, and Back from Home
 * leaves MilO. Anything that was open on top (the pairing screen) is closed.
 *
 * Home itself is never removed and added again, so it keeps its state.
 */
internal fun <T> MutableList<T>.showTopLevel(destination: T, home: T) {
    while (size > 1) removeAt(lastIndex)
    if (isEmpty()) add(home)
    if (destination != home) add(destination)
}

/**
 * Opens a screen on top of the one showing, for a button that leads from one screen to another.
 * Not added twice if the button is pressed twice before the screen has changed: a back stack
 * may hold a key only once.
 */
internal fun <T> MutableList<T>.openOnTop(screen: T) {
    if (screen !in this) add(screen)
}

/**
 * Back: closes the screen on top. The last screen is never closed here: Navigation 3 throws on
 * an empty back stack, and a crash of the screens takes the trip service down with it. Back
 * from Home is Android's to handle, and leaves MilO.
 */
internal fun <T> MutableList<T>.closeTop() {
    if (size > 1) removeAt(lastIndex)
}

/**
 * Closes [screen] if it is the one on top, for a screen's own Back arrow.
 *
 * A screen that is being closed stays on the display for the length of the transition, and its
 * arrow can be pressed again in that time. Without the check a second press would close the
 * screen underneath as well, and a third would empty the back stack.
 */
internal fun <T> MutableList<T>.closeIfOnTop(screen: T) {
    if (lastOrNull() == screen) closeTop()
}

/**
 * Shows the screen on top of [backStack]. This is the only place that knows about more than one
 * feature: features never navigate to, or import from, each other, so a screen that leads to
 * another one is handed a plain function to call.
 *
 * It is also where each screen's ViewModel is given what it needs from the [AppContainer]. That
 * is the manual dependency injection for screens: a feature never reaches for the container
 * itself, so it does not depend on the `app` package. A ViewModel lives as long as its screen is
 * on the back stack, so the Trips screen opens on the current month every time it is entered.
 */
@Composable
fun MiloNavigation(
    container: AppContainer,
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.closeTop() },
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
                                            lookUpAddresses = container.tripAddresses::catchUp,
                                            clock = System::currentTimeMillis,
                                            zone = ZoneId::systemDefault,
                                        )
                                    }
                                },
                            ),
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
