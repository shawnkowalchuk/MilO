package com.shawnkowalchuk.milo.platform.car

import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.data.trip.TodaySession
import com.shawnkowalchuk.milo.data.trip.TodayTrips
import com.shawnkowalchuk.milo.platform.trip.CurrentTrip
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.util.Locale

// A trip and a moment for the tests of the Android Auto screen: a trip that started 23 minutes
// ago and has covered 12.449 km, on a day with no finished trip yet.

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

/** Today's finished trips, one for each distance given. Their times do not matter to the car. */
internal fun todayOf(vararg metres: Double) = TodayTrips(
    metres.mapIndexed { index, distance ->
        TodaySession(index + 1L, STARTED_AT_MS, STARTED_AT_MS + MINUTE_MS, distance)
    },
)

internal fun carContent(
    activity: TripActivity,
    today: TodayTrips? = todayOf(),
    setupNeedsAttention: Boolean = false,
    nowMs: Long = NOW_MS,
    locale: Locale = Locale.CANADA,
) = carScreenContent(activity, today, setupNeedsAttention, nowMs, locale)
