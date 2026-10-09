package com.shawnkowalchuk.milo.data.trip

import com.shawnkowalchuk.milo.core.odometer.OdometerFigure
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.odometer.drivenIn
import com.shawnkowalchuk.milo.core.odometer.odometerAt
import com.shawnkowalchuk.milo.core.odometer.ofVehicle
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.localDateOf
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.StoredVehicle
import com.shawnkowalchuk.milo.data.settings.pairedVehicles
import java.time.ZoneId

/**
 * One odometer as it stands now: a paired vehicle's (since 2026-10-08), or, while none is
 * paired, the one of every reading and trip. Shared by Settings' odometer tiles and Home's
 * (2026-10-09), so the two can never show different figures.
 *
 * @param vehicle the paired vehicle, or null while none is.
 * @param named true where several vehicles are paired, so that each figure needs its name.
 * @param figure the odometer now, or null before its first reading.
 */
data class VehicleOdometer(
    val vehicle: StoredVehicle?,
    val named: Boolean,
    val figure: OdometerFigure?,
) {
    /** The vehicle's name as the phone shows it, or its address, where it needs one at all. */
    val shownName: String? get() = vehicle?.let { it.name ?: it.address }?.takeIf { named }
}

/**
 * The odometer at [nowMs]: the closest reading and the trips that moved the odometer since
 * (`odometerAt`), in [unit]. Pure, so it is tested without a phone.
 *
 * @param finished every finished trip; the ones that moved the odometer are picked out here.
 * @param address the vehicle whose odometer it is, or null for every reading and trip (no
 * vehicle paired). [isFirst] says whether it is the first vehicle, whose odometer also takes the
 * readings and trips that name none (`ofVehicle`).
 */
fun odometerNow(
    readings: List<OdometerReading>,
    finished: List<Trip>,
    nowMs: Long,
    zone: ZoneId,
    unit: DistanceUnit,
    address: String? = null,
    isFirst: Boolean = true,
): OdometerFigure? {
    val driven = drivenTrips(finished)
    return odometerAt(
        nowMs,
        localDateOf(nowMs, zone),
        zone,
        if (address == null) readings else readings.ofVehicle(address, isFirst),
        if (address == null) driven else driven.drivenIn(address, isFirst),
        unit,
    )
}

/**
 * Every paired vehicle's odometer now, the first first, each named where there are several; one
 * unnamed odometer of every reading and trip while none is paired. In the unit chosen in
 * Settings.
 */
fun vehicleOdometers(
    settings: MiloSettings,
    finished: List<Trip>,
    nowMs: Long,
    zone: ZoneId,
): List<VehicleOdometer> {
    val readings = settings.odometerReadings
    val unit = settings.distanceUnit
    val vehicles = settings.pairedVehicles()
    if (vehicles.isEmpty()) {
        return listOf(
            VehicleOdometer(null, false, odometerNow(readings, finished, nowMs, zone, unit)),
        )
    }
    return vehicles.mapIndexed { index, vehicle ->
        VehicleOdometer(
            vehicle = vehicle,
            named = vehicles.size > 1,
            figure =
                odometerNow(
                    readings,
                    finished,
                    nowMs,
                    zone,
                    unit,
                    address = vehicle.address,
                    isFirst = index == 0,
                ),
        )
    }
}
