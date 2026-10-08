package com.shawnkowalchuk.milo.app

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.MiloIcons
import kotlinx.serialization.Serializable

// The screens, as keys of the Navigation 3 back stack. Each is @Serializable because Navigation
// 3 saves the back stack with kotlinx.serialization when Android puts MilO away.

@Serializable
data object HomeKey : NavKey

@Serializable
data object TripsKey : NavKey

/**
 * The Settings screen, a screen of the bottom bar since 2026-10-07 (it was opened from Home
 * before). It is also opened on top of the Report screen, by its "Open Settings".
 */
@Serializable
data object SettingsKey : NavKey

@Serializable
data object LogKey : NavKey

/**
 * The Setup checklist. Not in the bottom bar since 2026-10-07, when Settings took its place:
 * it is opened from the tile at the top of Settings, and from Home's warning.
 */
@Serializable
data object SetupKey : NavKey

/**
 * The truck pairing screen. Not in the bottom bar: it is opened from Setup, from Settings, and
 * from Home's truck tile while no truck is paired.
 */
@Serializable
data object PairingKey : NavKey

/**
 * The edit screen. Not in the bottom bar: it is opened from Trips.
 *
 * @param tripId the finished trip to edit, or null to type in a trip that MilO missed.
 */
@Serializable
data class TripEditKey(val tripId: Long?) : NavKey

/**
 * The Report screen, where the report for the accountant is made and sent. Not in the bottom
 * bar: it is opened from Trips, and from Home's tile for a report that has not been sent.
 *
 * @param year and [month] (1 to 12) name the month the screen opens with: the one the Trips
 * screen was showing, or the one Home's tile is about. Two plain numbers, because that is what
 * the back stack saves.
 */
@Serializable
data class ReportKey(val year: Int, val month: Int) : NavKey

/**
 * The What's new screen (2026-10-08). Not in the bottom bar: it is opened from the Version tile
 * on Settings, and once by itself after an update.
 */
@Serializable
data object WhatsNewKey : NavKey

/** The four screens of the bottom navigation bar, in the order the bar shows them. */
enum class TopLevelDestination(val key: NavKey, val labelRes: Int, val icon: ImageVector) {
    HOME(HomeKey, R.string.nav_home, MiloIcons.Home),
    TRIPS(TripsKey, R.string.nav_trips, MiloIcons.Trips),
    SETTINGS(SettingsKey, R.string.nav_settings, MiloIcons.Settings),
    LOG(LogKey, R.string.nav_log, MiloIcons.Log),
}

/**
 * The bottom-bar screen the back stack is in: the one directly on top of Home, which is where
 * a button of the bar puts its screen ([showTopLevel]), or Home itself. With Setup or the
 * pairing screen open from Settings that is Settings, and with the edit screen or the Report
 * screen open from Trips it is Trips, so the bar keeps showing where Shawn came from. Settings
 * opened from that Report screen keeps it on Trips too: Settings is a screen of the bar, but
 * there it is opened on top of another one, as the pairing screen, Setup and the Report screen
 * opened from Home's own tiles keep it on Home.
 */
internal fun topLevelOf(backStack: List<NavKey>): TopLevelDestination = backStack
    .getOrNull(1)
    ?.let { key -> TopLevelDestination.entries.firstOrNull { it.key == key } }
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
 * Shows the Report screen for a month from wherever MilO is, for a tap on the monthly reminder.
 * The back stack is then what it is when the screen is opened by hand: Home, Trips, and the
 * report on top, so Back leads to Trips and from there to Home. Whatever was open is closed.
 */
internal fun MutableList<NavKey>.showReport(report: ReportKey) {
    showTopLevel(TripsKey, HomeKey)
    add(report)
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
 * Takes a way out of the screen on top, once nothing stands in the way any more: to a screen
 * of the bottom bar, or Back to the screen underneath.
 *
 * @param to the bottom-bar screen, or null for Back.
 */
internal fun MutableList<NavKey>.leaveTop(to: TopLevelDestination?) {
    if (to == null) closeTop() else showTopLevel(to.key, HomeKey)
}
