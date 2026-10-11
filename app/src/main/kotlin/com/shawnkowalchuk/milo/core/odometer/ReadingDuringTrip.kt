package com.shawnkowalchuk.milo.core.odometer

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.tenthsOf

// A reading typed while a trip is being recorded (2026-10-10). MilO opens a trip the moment the
// phone connects to the truck, so a reading typed in the truck before driving off is typed
// with a trip already open. Until this day such a trip was taken to be in the reading, whole,
// because it had started before it, and the drive that followed was never added: Shawn's
// odometer was 9 km short after the first trip of the day ("i typed the km to the accurate
// reading before driving today").
//
// A reading says what the dashboard showed at the moment it was typed. So the reading now
// keeps how far the open trip had gone at that moment, and the trip is cut there: what it had
// driven before is in the reading, and what it drove after is added to it.

/**
 * The trip that was being recorded when a reading was typed.
 *
 * @param tripId the trip's own number (`DrivenTrip.id`).
 * @param metres how far the trip had gone at that moment: the trip controller's running
 * figure, the one Home shows. Metres, whatever unit the reading was typed in.
 */
data class TripAtReading(val tripId: Long, val metres: Double) {
    init {
        require(metres.isFinite() && metres >= 0.0) {
            "A trip's distance must be a finite, non-negative number of metres, but was $metres"
        }
    }
}

/**
 * [trips] as the odometer counts them beside [readings]: a trip that a reading was typed
 * during is cut in two there, and each part counts as a trip of its own. The part driven
 * before the reading keeps the trip's start, so it lies before the reading; the part driven
 * after it starts at the reading, so it lies after. Every rule of `odometerAt` then holds for
 * the parts as it does for whole trips. A trip that several readings were typed during is cut
 * at each of them.
 *
 * Every other trip is handed back as it is, and counts by its start as it always did. So does
 * every trip beside a reading without a [TripAtReading]: one typed with no trip open, and
 * every reading typed before 2026-10-10.
 *
 * **A part that is printed as 0.0 km is left out,** so it is no "truck trip between": a
 * reading typed before driving off has nothing before it, and one typed after parking has
 * nothing after it, even where the GPS counted a few metres while the truck stood. Nothing
 * is lost from a sum by that: such a part adds 0.0 in either unit.
 *
 * **A trip is only cut by a reading typed after it started.** The trip's number alone is not
 * enough: after an import the numbers can be given out again, and a trip whose start Shawn
 * moved to after the reading was, by his word, driven after it. Such a trip counts whole.
 *
 * @param trips one vehicle's trips, in any order.
 * @param readings the same vehicle's readings, in any order.
 */
internal fun cutAtReadings(
    trips: List<DrivenTrip>,
    readings: List<OdometerReading>,
): List<DrivenTrip> {
    val typedDuring = readings.filter { it.duringTrip != null }.groupBy { it.duringTrip?.tripId }
    if (typedDuring.isEmpty()) return trips
    return trips.flatMap { trip ->
        val cuts = typedDuring[trip.id].orEmpty().filter { it.atMs > trip.startedAtMs }
        if (cuts.isEmpty()) listOf(trip) else trip.cutAt(cuts.sortedBy { it.atMs })
    }
}

/**
 * The parts of a trip between the readings typed during it, oldest first.
 *
 * The distance at a reading is held between the part before it and the whole trip: the closed
 * trip is measured again without what was recorded after the truck disconnected, and Shawn can
 * change a trip's distance by hand, so a trip can end shorter than it stood at the reading.
 * Nothing is then driven after that reading, and nothing is taken away either.
 */
private fun DrivenTrip.cutAt(cuts: List<OdometerReading>): List<DrivenTrip> {
    val parts = mutableListOf<DrivenTrip>()
    var from = 0.0
    var since = startedAtMs
    for (reading in cuts) {
        val upTo = (reading.duringTrip?.metres ?: from).coerceIn(from, metres)
        parts += part(since, upTo - from)
        from = upTo
        since = reading.atMs
    }
    parts += part(since, metres - from)
    return parts.filter { tenthsOf(it.metres, DistanceUnit.KILOMETRES) > 0 }
}

/** A part of this trip. It carries no number: a part is never cut a second time. */
private fun DrivenTrip.part(startedAtMs: Long, metres: Double): DrivenTrip =
    copy(startedAtMs = startedAtMs, metres = metres, id = null)
