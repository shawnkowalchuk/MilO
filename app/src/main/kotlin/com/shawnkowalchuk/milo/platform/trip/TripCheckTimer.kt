package com.shawnkowalchuk.milo.platform.trip

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The timer the trip rules ask for (`TripStateMachine.nextCheckAtMs`): the end of a grace period,
 * the confirmation deadline of a companion start, the parked limit of a trip, or the limit on
 * watching a parked truck. When it runs out the controller reads the truck's connection, and the
 * rules decide. A part of [TripService], in a file of its own only to keep the service readable.
 * The service also times the changes of how GPS is read beside a parked truck with one
 * (`ParkedGps`).
 *
 * TODO(debt): this timer and the service's minute timer are coroutines, and a coroutine's delay
 *  does not count time the phone spends asleep. With the screen off they can fire late. A GPS
 *  fix stands in for a late timer (a deadline in TripWorker.onFix, the minute check in
 *  TripService.onFix), but with no fixes arriving a trip can stay open past its grace period or
 *  its parked limit, and a disconnect that was never reported is noticed late. The trip is
 *  still cut at the right moment; only its closing is late. The cure, if the phone shows it is
 *  needed, is a wake lock for the length of a trip. See docs/FINDINGS_LOG.md.
 *
 * Not thread-safe: the service calls [set] on the main thread only.
 *
 * @param scope the service's scope, so the timer ends when the service does.
 * @param clock the time of day in milliseconds: MilO's clock, the one the deadlines were worked
 * out on. Read once, when the timer is set; the wait itself is a plain delay.
 * @param onDue called with the deadline once it has passed.
 */
internal class TripCheckTimer(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val onDue: (deadlineMs: Long) -> Unit,
) {
    private var timer: Job? = null
    private var setFor: Long? = null

    /**
     * Sets the timer for [checkAtMs], a time on MilO's clock, or takes it away with null. Setting it
     * for the time it is already set for changes nothing: the controller says so after every
     * GPS fix.
     */
    fun set(checkAtMs: Long?) {
        if (checkAtMs == setFor) return
        setFor = checkAtMs
        timer?.cancel()
        if (checkAtMs == null) return
        timer =
            scope.launch {
                delay((checkAtMs - clock()).coerceAtLeast(0))
                onDue(checkAtMs)
            }
    }
}
