package com.shawnkowalchuk.milo.feature.home

import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.platform.address.OpenTripStart
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
 * @param startAddress where the trip in progress started, if that is known already.
 * @param nowMs the time, for how long the trip in progress has been running.
 * @param unit the unit chosen in Settings: every distance of the frame is written in it.
 * [figures] are in it too, or they are not shown ([homeUi]).
 */
internal data class HomeUi(
    val date: LocalDate,
    val activity: TripActivity,
    val setupNeedsAttention: Boolean,
    val figures: HomeTrips?,
    val truck: TruckTileState,
    val reportWaiting: YearMonth?,
    val startAddress: String?,
    val nowMs: Long,
    val unit: DistanceUnit,
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
 * @param openTripStart what the address lookup knows about the start of a trip in progress.
 * It keeps the last trip's value after that trip has ended.
 */
internal data class HomeRead(
    val figures: HomeTrips?,
    val reportWaiting: YearMonth?,
    val openTripStart: OpenTripStart?,
    val nowMs: Long,
)

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
            truckName = now.stored?.truckName,
        ),
    reportWaiting = read.reportWaiting,
    startAddress = startAddressOf(now.activity.trip, read.openTripStart),
    nowMs = read.nowMs,
    unit = now.unit,
)
