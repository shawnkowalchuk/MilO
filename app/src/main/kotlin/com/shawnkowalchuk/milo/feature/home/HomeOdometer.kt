package com.shawnkowalchuk.milo.feature.home

import com.shawnkowalchuk.milo.core.odometer.OdometerWheels
import com.shawnkowalchuk.milo.core.odometer.wheels
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.trip.Trip
import com.shawnkowalchuk.milo.data.trip.TripSoFar
import com.shawnkowalchuk.milo.data.trip.VehicleOdometer
import com.shawnkowalchuk.milo.data.trip.vehicleOdometers
import java.time.ZoneId

/**
 * How much further the trip being recorded is taken to have gone, to find out which odometer
 * it moves ([homeOdometers]). Ten kilometres: enough to change a figure in either unit.
 */
private const val FURTHER_METRES = 10_000.0

/**
 * One odometer as Home's row of wheels shows it (since 2026-10-10).
 *
 * @param odometer the vehicle and its odometer now, as Settings shows it.
 * @param tripMetres the distance so far of the trip being recorded, while this odometer is
 * counting it, or null: no trip is open, or the trip moves another vehicle's odometer or none.
 */
internal data class HomeOdometer(val odometer: VehicleOdometer, val tripMetres: Double? = null) {
    /** Whether the trip being recorded is moving this odometer: its last wheel then turns. */
    val counting: Boolean get() = tripMetres != null

    /** What its wheels show, or null before the vehicle's first reading. */
    val wheels: OdometerWheels? get() = odometer.figure?.wheels(tripMetres)
}

/**
 * Every paired vehicle's odometer now ([vehicleOdometers], which Settings' tiles use too), and
 * with each whether the trip being recorded is moving it.
 *
 * **Which odometer a trip moves is asked of the rule, not worked out a second time here.** The
 * rule has several parts (the truck must have been seen in the trip, the trip is its
 * vehicle's, a reading typed during the trip already holds it, storage may know it as ended
 * already), and a copy of them here would drift from the original. So the odometers are worked
 * out once more with the same trip taken to be ten kilometres further on: the one whose
 * figure is different then is the one the trip moves.
 *
 * @param soFar the trip being recorded, or null when there is none.
 */
internal fun homeOdometers(
    settings: MiloSettings,
    finished: List<Trip>,
    nowMs: Long,
    zone: ZoneId,
    soFar: TripSoFar?,
): List<HomeOdometer> {
    val now = vehicleOdometers(settings, finished, nowMs, zone, soFar)
    if (soFar == null) return now.map { HomeOdometer(it) }
    val further = soFar.copy(metres = soFar.metres + FURTHER_METRES)
    val moved = vehicleOdometers(settings, finished, nowMs, zone, further)
    return now.zip(moved) { shown, ifFurther ->
        HomeOdometer(shown, soFar.metres.takeIf { shown.figure != ifFurther.figure })
    }
}
