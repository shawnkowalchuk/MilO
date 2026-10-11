package com.shawnkowalchuk.milo.feature.home

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.platform.bluetooth.sameAddress
import com.shawnkowalchuk.milo.platform.bluetooth.trucks
import com.shawnkowalchuk.milo.platform.trip.TripActivity
import java.time.LocalDate
import java.time.YearMonth

/**
 * Everything the home screen draws, as plain values. It is put together in one step
 * ([homeUi]), so that the tiles of one frame are all made from the same trip state: the truck's
 * tile never says "Not connected" beside a trip that the same moment shows as recording with
 * the truck.
 *
 * @param date which day is today.
 * @param activity the trip controller's state: the trip in progress, and a start that failed.
 * @param figures today's and the month's trips, or null while they are being read.
 * @param reportWaiting the month whose report has not been sent, or null.
 * @param nowMs the time, for how long the trip in progress has been running.
 * @param unit the unit chosen in Settings: every distance of the frame is written in it.
 * [figures] are in it too, or they are not shown ([homeUi]).
 * @param odometers each paired vehicle's odometer now, as Settings shows it (since 2026-10-09),
 * and whether the trip being recorded is moving it (since 2026-10-10), or null while the
 * settings and the trips are being read, or the settings cannot be.
 * @param centsPerKm the rate the month's Business dollars are priced at, the one set in
 * Settings for the widget, or null while the settings have not been read or cannot be.
 */
internal data class HomeUi(
    val date: LocalDate,
    val activity: TripActivity,
    val setupNeedsAttention: Boolean,
    val figures: HomeTrips?,
    val truck: TruckTileState,
    val reportWaiting: YearMonth?,
    val nowMs: Long,
    val unit: DistanceUnit,
    val odometers: List<HomeOdometer>? = null,
    val centsPerKm: Int? = null,
)

/**
 * What MilO knows at this moment without reading the trips.
 *
 * @param stored the settings, or null while they have not been read, or cannot be.
 * @param unit the unit chosen in Settings, as the whole app holds it.
 */
internal data class HomeNow(
    val date: LocalDate,
    val activity: TripActivity,
    val setupNeedsAttention: Boolean,
    val stored: MiloSettings?,
    val unit: DistanceUnit,
)

/**
 * What has been read and worked out so far.
 *
 * @param figures null while the trips are being read. They may still be those of the day
 * before for a moment after midnight; [homeUi] does not show them then.
 * @param odometers each paired vehicle's odometer now, or null while they are being read.
 */
internal data class HomeRead(
    val figures: HomeTrips?,
    val reportWaiting: YearMonth?,
    val nowMs: Long,
    val odometers: List<HomeOdometer>? = null,
)

/**
 * The name to show on the truck's tile (since 2026-10-08): the paired vehicle the trip is about
 * or that is connected, and otherwise the first one, as the tile always showed.
 */
internal fun MiloSettings.vehicleName(address: String?): String? {
    val vehicles = trucks()
    val vehicle =
        vehicles.firstOrNull { sameAddress(it.address, address) } ?: vehicles.firstOrNull()
    return vehicle?.name
}

/** Puts the screen together. The one place where the parts meet. */
internal fun homeUi(now: HomeNow, read: HomeRead): HomeUi = HomeUi(
    date = now.date,
    activity = now.activity,
    setupNeedsAttention = now.setupNeedsAttention,
    // Right after midnight the trips in hand are still yesterday's. They are not shown under
    // today's date: the tiles say that they are reading. The same right after the unit was
    // changed in Settings: figures that were added up in the other unit are not shown under
    // this one's name.
    figures = read.figures?.takeIf { it.date == now.date && it.unit == now.unit },
    truck =
        TruckTileState(
            state = truckState(truckFacts(now.stored, now.activity, now.setupNeedsAttention)),
            truckName = now.stored?.vehicleName(now.activity.vehicle),
        ),
    reportWaiting = read.reportWaiting,
    nowMs = read.nowMs,
    unit = now.unit,
    odometers = read.odometers,
    centsPerKm = now.stored?.homeWidgetCentsPerKm,
)
