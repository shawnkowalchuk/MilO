package com.shawnkowalchuk.milo.core.trip

import java.util.Locale

// A trip's label (Shawn's request of 2026-10-08: "add the ability to add a label for each trip
// pick from a list of previously used ones. have them from a gps quaridinate so that if im at
// work and labeled work before that is the one that is choosen from the list"). His answers:
// the place is where the trip ended, within 200 m; the label used there before is only
// suggested, never set by itself; the report prints it as the trip's purpose; the phone only.
// Pure functions, so the rule is tested without a phone.

/** How close to where a labelled trip ended counts as the same place (Shawn's choice). */
const val LABEL_PLACE_RADIUS_METRES = 200.0

/** The longest label taken: a few words, which fit under a trip's addresses on the report. */
const val MAX_LABEL_LENGTH = 40

/**
 * A trip that has a label, as the choice of labels needs it: the label, where the trip ended
 * (null for a trip typed in by hand, which has no position), and when it started.
 */
data class LabelledTrip(
    val label: String,
    val endLatitude: Double?,
    val endLongitude: Double?,
    val startedAtMs: Long,
)

/**
 * The labels to pick from for one trip.
 *
 * @param suggested the label of the newest labelled trip that ended within
 * [LABEL_PLACE_RADIUS_METRES] of where this one did, or null if none did.
 * @param used every label used so far, each once, newest first, with [suggested] at the top.
 */
data class LabelChoices(val suggested: String?, val used: List<String>)

/**
 * The labels to pick from for a trip that ended at [endLatitude], [endLongitude] (null for one
 * with no position: then nothing is suggested). Labels that differ only in capitals are one
 * label, written as it was last used.
 *
 * @param labelled the trips that have a label: the trip itself among them or not.
 */
fun labelChoices(
    endLatitude: Double?,
    endLongitude: Double?,
    labelled: List<LabelledTrip>,
    radiusMetres: Double = LABEL_PLACE_RADIUS_METRES,
): LabelChoices {
    val newestFirst = labelled.sortedByDescending { it.startedAtMs }
    val used = newestFirst.map { it.label }.distinctBy { it.lowercase(Locale.ROOT) }
    val suggested =
        if (endLatitude == null || endLongitude == null) {
            null
        } else {
            newestFirst.firstOrNull { trip ->
                val latitude = trip.endLatitude
                val longitude = trip.endLongitude
                latitude != null &&
                    longitude != null &&
                    haversineMetres(endLatitude, endLongitude, latitude, longitude) <= radiusMetres
            }?.label?.let { label ->
                used.first { it.equals(label, ignoreCase = true) }
            }
        }
    val ordered = listOfNotNull(suggested) + used.filterNot { it == suggested }
    return LabelChoices(suggested, ordered)
}

/**
 * A label as it is stored: without spaces at its ends or runs of them inside, at most
 * [MAX_LABEL_LENGTH] characters; null for one that is empty, which takes the label away.
 */
fun cleanLabel(typed: String): String? =
    typed.trim().replace(Regex("\\s+"), " ").take(MAX_LABEL_LENGTH).trimEnd().ifEmpty { null }
