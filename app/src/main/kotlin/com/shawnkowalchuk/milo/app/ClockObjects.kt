package com.shawnkowalchuk.milo.app

import com.shawnkowalchuk.milo.core.clock.TrustedClock
import com.shawnkowalchuk.milo.platform.clock.ClockWatch

/**
 * What MilO's clock (ADR-005) does beside telling the time. The time itself is the container's
 * `clock`, which every object that needs it is handed.
 *
 * Like [ReportObjects] and [CheckObjects], it is a part of the [AppContainer], which creates it
 * once and through which everything reaches it (`container.clocks`); it is a class of its own
 * only because the container is at its size limit, and it follows the container's rules.
 *
 * @param trusted the clock itself, which `MiloApplication` makes before the container.
 */
class ClockObjects(private val container: AppContainer, private val trusted: TrustedClock) {
    /**
     * Whether MilO's time is the phone's right now. The monthly reminder and the daily check
     * ask before they ask Android for an alarm: Android goes by the phone's clock.
     */
    val agreesWithPhone: () -> Boolean = trusted::agreesWithPhone

    /**
     * Whether MilO's time was taken from the phone's clock with nothing to check it against
     * and has not been confirmed since. The same two hold a notification back while it is so.
     */
    val onProbation: () -> Boolean = { trusted.onProbation }

    /**
     * Writes the line for a change of the phone's clock that MilO did not follow, and has the
     * two daily alarms asked for again once the clocks agree. It is handed the two `arm`
     * functions and the event log, and nothing of the trip engine.
     */
    val watch: ClockWatch by lazy {
        ClockWatch(
            clock = trusted,
            // Each is reached when it is first asked, not here: building the watch at process
            // start must not build the reminder and the check before their own turn.
            askAgain =
                listOf(
                    { source, done -> container.reports.reminder.arm(source, done) },
                    { source, done -> container.checks.nothingRecorded.arm(source, done) },
                ),
            eventLog = container.eventLogRepository,
            crashFileStore = container.crashFileStore,
            scope = container.applicationScope,
        )
    }
}
