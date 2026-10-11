package com.shawnkowalchuk.milo.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.shawnkowalchuk.milo.core.odometer.OdometerFigure
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.core.util.tenthsOf
import com.shawnkowalchuk.milo.data.settings.StoredVehicle
import com.shawnkowalchuk.milo.data.trip.VehicleOdometer
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Home's odometer as Android Studio draws it: the row of wheels in each state the two previews
// of the whole screen do not show, and a drive, which rolls the wheels in the interactive
// preview. An emulator has no truck, and a trip without one moves no odometer, so the drive is
// also how the turning wheels are looked at on one. The rest of Home's previews, and the
// samples these start from, are in HomeScreenPreview.kt.

// Sample values are written inline because a preview is never shown to a user or shipped.
private const val SECOND_MS = 1_000L
private val MILES = DistanceUnit.MILES

/** The reading every sample odometer starts from, and the finished trips since: 48.2 km. */
private const val SAMPLE_READING = 123_412L
private const val SAMPLE_DRIVEN_TENTHS = 482L
private const val TENTHS = 10L
private const val HALF = 5L

/**
 * An odometer as the screen is handed one, made the way `odometerAt` makes it: the reading and
 * the trips since, rounded to the whole number Settings shows.
 *
 * @param tripMetres the trip being recorded, if this odometer is counting it.
 * @param vehicle the vehicle's name, where several are paired.
 */
internal fun sampleOdometer(
    tripMetres: Double?,
    vehicle: String? = null,
    reading: Long = SAMPLE_READING,
    unit: DistanceUnit = DistanceUnit.KILOMETRES,
): HomeOdometer {
    val driven = SAMPLE_DRIVEN_TENTHS + (tripMetres?.let { tenthsOf(it, unit) } ?: 0)
    val figure =
        OdometerFigure(
            value = (reading * TENTHS + driven + HALF) / TENTHS,
            unit = unit,
            reading = OdometerReading(MORNING, reading, unit),
            drivenTenths = driven,
            estimated = true,
        )
    val paired = vehicle?.let { StoredVehicle(it, it, associationId = null, pairedAtMs = 0) }
    return HomeOdometer(VehicleOdometer(paired, named = vehicle != null, figure), tripMetres)
}

/** The states of the odometer's tile that the two previews above do not show. */
private class OdometerSamples : PreviewParameterProvider<List<HomeOdometer>> {
    private val noReading =
        HomeOdometer(VehicleOdometer(vehicle = null, named = false, figure = null))
    private val vanNoReading =
        HomeOdometer(
            VehicleOdometer(
                StoredVehicle("Van", "Van", associationId = null, pairedAtMs = 1),
                named = true,
                figure = null,
            ),
        )

    override val values =
        sequenceOf(
            // 0: before the first reading.
            listOf(noReading),
            // 1: two vehicles, the trip in the first; the second has no reading yet.
            listOf(sampleOdometer(sampleTrip.distanceMetres, "F-150"), vanNoReading),
            // 2: two vehicles, the trip in the second.
            listOf(
                sampleOdometer(null, "F-150"),
                sampleOdometer(sampleTrip.distanceMetres, "Van", reading = 50_000),
            ),
            // 3: miles.
            listOf(sampleOdometer(sampleTrip.distanceMetres, reading = 76_543, unit = MILES)),
            // 4: a figure of eight digits, which is drawn smaller on a narrow phone.
            listOf(sampleOdometer(sampleTrip.distanceMetres, reading = 9_999_999)),
            // 5: a trip the truck has not joined: the wheels stand.
            listOf(sampleOdometer(tripMetres = null)),
        )
}

/** Home while a trip is recorded, with each of those odometers. */
@Preview
@Composable
private fun HomeOdometerPreview(
    @PreviewParameter(OdometerSamples::class) odometers: List<HomeOdometer>,
) {
    val unit = odometers.firstNotNullOfOrNull { it.wheels?.unit } ?: DistanceUnit.KILOMETRES
    HomePreview(HomeShown(sampleRecording.copy(odometers = odometers, unit = unit)))
}

/** How far the script below drives each second: a truck at 90 km/h. */
private const val SCRIPT_METRES_A_SECOND = 25.0

/** Where the script's trip starts: just before the odometer's wheels carry from 469.9 to 470.0. */
private const val SCRIPT_STARTS_AT_METRES = 9_300.0
private const val SCRIPT_ENDS_AT_METRES = 10_100.0

/** A trip being driven, a position a second, over and over: the wheels roll and carry. */
private fun truckDrives(): Flow<HomeShown> = flow {
    while (true) {
        var metres = SCRIPT_STARTS_AT_METRES
        while (metres <= SCRIPT_ENDS_AT_METRES) {
            val trip = sampleTrip.copy(distanceMetres = metres)
            emit(
                HomeShown(
                    sampleRecording.copy(
                        activity = TripActivity(trip, truckConnected = true),
                        odometers = listOf(sampleOdometer(metres)),
                    ),
                ),
            )
            delay(SECOND_MS)
            metres += SCRIPT_METRES_A_SECOND
        }
    }
}

/** The odometer during a drive. It moves in the interactive preview and on a device. */
@Preview
@Composable
private fun HomeOdometerDrivesPreview() {
    val shown by remember { truckDrives() }.collectAsState(HomeShown(sampleRecording))
    HomePreview(shown)
}
