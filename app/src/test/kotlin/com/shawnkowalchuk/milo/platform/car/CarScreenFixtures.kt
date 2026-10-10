package com.shawnkowalchuk.milo.platform.car

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.util.Locale

// A trip and a moment for the tests of the Android Auto screen: a trip that started 23 minutes
// ago and has covered 12.449 km, on a day with no finished Business trip yet.

internal const val MINUTE_MS = 60_000L
internal const val STARTED_AT_MS = 1_791_028_800_000L
internal const val NOW_MS = STARTED_AT_MS + 23 * MINUTE_MS

internal fun carTrip(metres: Double = 12_449.0, waitingForTruck: Boolean = false) = CurrentTrip(
    tripId = 7,
    startedAtMs = STARTED_AT_MS,
    startedBy = TripStartCause.TRUCK,
    distanceMetres = metres,
    waitingForTruck = waitingForTruck,
)

/** The Business row's figures as the screen is handed them, with [today] Business so far. */
internal fun businessOf(today: String = "0.0", unit: DistanceUnit = DistanceUnit.KILOMETRES) =
    BusinessFigures(
        today = today,
        monthName = "October",
        month = "412.3",
        dollars = "$289",
        unit = unit,
    )

internal fun carContent(
    activity: TripActivity,
    unit: DistanceUnit = DistanceUnit.KILOMETRES,
    business: BusinessFigures? = businessOf(unit = unit),
    setupNeedsAttention: Boolean = false,
    nowMs: Long = NOW_MS,
    locale: Locale = Locale.CANADA,
) = carScreenContent(activity, business, setupNeedsAttention, nowMs, locale, unit)
