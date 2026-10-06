package com.shawnkowalchuk.milo.platform.address

// What an address lookup did for one end of a trip, and how the event log words it. Plain
// functions, kept apart from TripAddresses so that what is logged can be read (and tested) in
// one place. Like the rest of the event log the lines are English only, and they name no
// address and no position: the address itself is on the trip's row.

private const val MINUTE_MS = 60_000L
private const val MINUTES_PER_HOUR = 60L

/** What one lookup did for one end of a trip. */
internal sealed interface PlaceOutcome {
    /** True if this end of the trip is still without an address that a later lookup could find. */
    val leavesGap: Boolean

    /** The row already held this address. Nothing was asked. */
    data object AlreadyStored : PlaceOutcome {
        override val leavesGap = false
    }

    data class Found(val address: String) : PlaceOutcome {
        override val leavesGap = false
    }

    /**
     * The geocoder answered, with nothing a person would recognise.
     *
     * @param candidates how many places it offered. None at all is a spot with no address;
     * some, with neither a street nor a town among them, is worth knowing when reading the log.
     */
    data class NothingUsable(val candidates: Int) : PlaceOutcome {
        override val leavesGap = true
    }

    /** No answer: an error, no reply in time, or no geocoder. */
    data class Failed(val reason: String) : PlaceOutcome {
        override val leavesGap = true
    }

    /** Not asked, because the lookup for the trip's other end had just failed. */
    data object NotAsked : PlaceOutcome {
        override val leavesGap = true
    }

    /** The trip has no position for this end, so there is nothing to ask about, now or later. */
    data object NoPosition : PlaceOutcome {
        override val leavesGap = false
    }

    /** Shawn emptied this address himself. It is his, and is not asked about, now or later. */
    data object LeftBlankByHand : PlaceOutcome {
        override val leavesGap = false
    }
}

private fun PlaceOutcome.inWords(): String = when (this) {
    PlaceOutcome.AlreadyStored -> "already stored"

    is PlaceOutcome.Found -> "found"

    is PlaceOutcome.NothingUsable ->
        if (candidates == 0) {
            "none (the geocoder knows no address there)"
        } else {
            "none (the geocoder offered $candidates places, none with a street or a town)"
        }

    is PlaceOutcome.Failed -> "lookup failed ($reason)"

    PlaceOutcome.NotAsked -> "not asked"

    PlaceOutcome.NoPosition -> "none (the trip has no position there)"

    PlaceOutcome.LeftBlankByHand -> "none (left empty by hand, so not looked up)"
}

/**
 * The line that records one lookup of a finished trip.
 *
 * @param failedAttempts how many lookups have now left the trip without an address, or null if
 * this one completed it.
 * @param stored false if the row took nothing because it is not a finished trip. That should
 * never happen, and is said out loud when it does.
 * @param leftWaiting how many other trips this pass did not get to, because the geocoder failed.
 * They lose no attempt, and are asked about at the next pass.
 */
internal fun lookupText(
    tripId: Long,
    start: PlaceOutcome,
    end: PlaceOutcome,
    failedAttempts: Int?,
    stored: Boolean,
    leftWaiting: Int,
): String {
    val found = "Trip $tripId: start address ${start.inWords()}; end address ${end.inWords()}."
    val attempts =
        when {
            failedAttempts == null -> ""

            failedAttempts >= MAX_ADDRESS_ATTEMPTS ->
                " Failed attempt $failedAttempts of $MAX_ADDRESS_ATTEMPTS: " +
                    "this trip is not looked up again."

            else ->
                " Failed attempt $failedAttempts of $MAX_ADDRESS_ATTEMPTS; the next one is at " +
                    "least ${waitInWords(waitAfterAttemptMs(failedAttempts))} away."
        }
    val waiting =
        when (leftWaiting) {
            0 -> ""
            1 -> " 1 other trip was not asked about."
            else -> " $leftWaiting other trips were not asked about."
        }
    val surprise = if (stored) "" else " (the row is not a finished trip: nothing was written)"
    return found + attempts + waiting + surprise
}

/** The line for a lookup of where the trip in progress started. Nothing is stored for it. */
internal fun openStartText(tripId: Long, outcome: PlaceOutcome): String =
    "Trip $tripId (in progress): start address ${outcome.inWords()}."

/** The line for a pass that asked nothing because the phone is offline. */
internal fun putOffText(reason: String, finishedWaiting: Int, openStart: OpenTripStart?): String {
    val trips = if (finishedWaiting == 1) "trip" else "trips"
    val open = openStart?.let { " and the start of trip ${it.tripId} (in progress)" }.orEmpty()
    return "Address lookup put off ($reason): no network connection. " +
        "Waiting: $finishedWaiting finished $trips$open."
}

private fun waitInWords(waitMs: Long?): String {
    val minutes = (waitMs ?: 0L) / MINUTE_MS
    return if (minutes >= MINUTES_PER_HOUR) "${minutes / MINUTES_PER_HOUR} h" else "$minutes min"
}
