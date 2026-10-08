package com.shawnkowalchuk.milo.core.designsystem.text

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.util.DistanceUnit

// Which words a distance is written with in the unit chosen in Settings (since 2026-10-07).
// Only the choice is made here, so that it is tested without a phone; the words themselves are
// in strings.xml. In one place because every screen, the Android Auto screen, the widget, the
// notification and the report write distances, and none of them may name a unit by a string
// of its own: a "km" written beside a figure in miles would be a wrong figure.
//
// A sentence that holds a distance takes the distance as one argument, already written with
// its unit by [distanceRes] ("48.2 km"), and so never names a unit itself.

/** The unit's short word, where the design writes it small after a large figure: "km", "mi". */
fun unitShortRes(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETRES -> R.string.unit_km
    DistanceUnit.MILES -> R.string.unit_mi
}

/**
 * A figure with its unit, as it is written: "12.4 km", "7.7 mi". The one argument is the
 * figure, from `formatDistance` for one trip and from `formatTenths` for a total.
 */
fun distanceRes(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETRES -> R.string.distance_km
    DistanceUnit.MILES -> R.string.distance_mi
}

/**
 * The same as a screen reader says it, with the unit's whole word: "12.4 kilometres", "7.7
 * miles". For a figure that stands by itself; "mi" read out as it is written is no word.
 */
fun distanceSpokenRes(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETRES -> R.string.distance_km_spoken
    DistanceUnit.MILES -> R.string.distance_mi_spoken
}

/** The unit's whole word inside a sentence: "kilometres", "miles". */
fun unitNameRes(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETRES -> R.string.unit_km_name
    DistanceUnit.MILES -> R.string.unit_mi_name
}

/** A speed with its unit, for a limit a sentence names: "180 km/h", "111 mph". */
fun speedRes(unit: DistanceUnit): Int = when (unit) {
    DistanceUnit.KILOMETRES -> R.string.speed_kmh
    DistanceUnit.MILES -> R.string.speed_mph
}
