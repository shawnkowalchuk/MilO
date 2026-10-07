package com.shawnkowalchuk.milo.feature.eventlog

import com.shawnkowalchuk.milo.data.eventlog.EventCategory

// How the Log screen sorts the log's thirteen categories into the four chips of the owner's
// design, and which word a line's tag carries. Pure functions: the words on the chips are in
// the string resources, and the colours are the theme's.

/** The four kinds of line the Log screen's chips narrow the list to. "All" is no group. */
enum class LogGroup { TRIPS, BLUETOOTH, ANDROID_AUTO, ERRORS }

/**
 * The chip a category's lines are found under, or null for a category that is shown under
 * "All" only.
 *
 * Every category is named and there is no "else": a new one does not compile until it has been
 * placed here, and `LogGroupsTest` fails until the test says where it belongs.
 *
 * - **Trips:** a trip starting, ending or being changed, the grace period that decides when one
 *   ends, and the lookup of its two addresses.
 * - **Bluetooth:** every look at the truck's connection (a trigger line says what was found,
 *   whatever caused the look), and the truck's pairing.
 * - **Android Auto:** its connection.
 * - **Errors:** a failure that was caught, and a crash.
 * - **Under "All" only:** the process, the trip service, GPS, the driving alert and the report.
 *   None of them is a trip, the truck's connection, Android Auto or a failure.
 */
fun EventCategory.logGroup(): LogGroup? = when (this) {
    EventCategory.TRIP, EventCategory.GRACE, EventCategory.ADDRESS -> LogGroup.TRIPS

    EventCategory.TRIGGER, EventCategory.PAIRING -> LogGroup.BLUETOOTH

    EventCategory.ANDROID_AUTO -> LogGroup.ANDROID_AUTO

    EventCategory.ERROR, EventCategory.CRASH -> LogGroup.ERRORS

    EventCategory.PROCESS,
    EventCategory.SERVICE,
    EventCategory.LOCATION,
    EventCategory.DRIVING,
    EventCategory.REPORT,
    -> null
}

/** The categories whose lines a chip shows. */
fun LogGroup.categories(): List<EventCategory> =
    EventCategory.entries.filter { it.logGroup() == this }

// The two short words of the design that are not a category's own name.
private const val TAG_BLUETOOTH = "BT"
private const val TAG_ANDROID_AUTO = "AUTO"

/**
 * The word on a line's tag: short and in capitals, as the design draws it. English and not
 * translated, like the line itself: the log is evidence.
 *
 * Two categories carry the design's short word in place of their stored name. A trigger line
 * is tagged "BT": each one is a look at the truck's Bluetooth connection. An Android Auto line
 * is tagged "AUTO". Every other category carries the name it is stored under, which is also
 * the word the shared text file uses ("TRIP", "SERVICE", "ERROR", "PAIRING", ...).
 */
fun EventCategory.tagWord(): String = when (this) {
    EventCategory.TRIGGER -> TAG_BLUETOOTH
    EventCategory.ANDROID_AUTO -> TAG_ANDROID_AUTO
    else -> name
}
