package com.shawnkowalchuk.milo.platform.trip

import com.shawnkowalchuk.milo.core.schedule.FilingRules
import com.shawnkowalchuk.milo.core.trip.TripRules
import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import com.shawnkowalchuk.milo.data.eventlog.EventLogRepository
import com.shawnkowalchuk.milo.data.settings.DEFAULT_GRACE_PERIOD_SECONDS
import com.shawnkowalchuk.milo.data.settings.DEFAULT_MINIMUM_TRIP_DISTANCE_METRES
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import java.io.IOException

private const val MILLIS_PER_SECOND = 1000L

/**
 * The settings the trip controller runs with, read again at every trigger so that a changed
 * value applies from the next event.
 *
 * An unreadable settings file is never reset (see `buildSettingsStore`), and it must not stop
 * trips either: the defaults are used, and the failure is logged once per process.
 *
 * The work schedule is read here too, for one use only: sorting a trip into Business or
 * Personal at the moment it is closed ([filingRules]). Nothing that decides whether a trip
 * starts, carries on or ends ever looks at it.
 *
 * Only the controller's worker calls [read]. [rules] is also read from other threads, so it is
 * volatile.
 */
internal class TripRuleSettings(
    private val settings: SettingsStore,
    private val eventLog: EventLogRepository,
    private val clock: () -> Long,
) {
    @Volatile
    var rules = TripRules(gracePeriodMs = DEFAULT_GRACE_PERIOD_SECONDS * MILLIS_PER_SECOND)
        private set

    var minimumTripDistanceMetres = DEFAULT_MINIMUM_TRIP_DISTANCE_METRES
        private set

    /**
     * What a closing trip is sorted by, or null while the settings cannot be read. There is no
     * default to fall back on here: a trip sorted by hours that are not Shawn's would be wrong
     * for good, while one left unsorted is sorted by the catch-up once the settings can be read.
     */
    var filingRules: FilingRules? = null
        private set

    private var failureLogged = false

    /**
     * Reads the settings and brings [rules], [minimumTripDistanceMetres] and [filingRules] up
     * to date.
     */
    suspend fun read(): MiloSettings {
        val now =
            try {
                settings.current().also {
                    filingRules = FilingRules(it.schedule, it.ignoreTripsOutsideSchedule)
                }
            } catch (unreadable: IOException) {
                filingRules = null
                if (!failureLogged) {
                    failureLogged = true
                    val what = "The settings cannot be read. Trip recording is using the defaults"
                    eventLog.add(
                        clock(),
                        EventCategory.ERROR,
                        what,
                        unreadable.stackTraceToString(),
                    )
                }
                MiloSettings()
            }
        rules = TripRules(gracePeriodMs = now.gracePeriodSeconds * MILLIS_PER_SECOND)
        minimumTripDistanceMetres = now.minimumTripDistanceMetres
        return now
    }
}
