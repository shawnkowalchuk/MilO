package com.shawnkowalchuk.milo.core.trip

/** ADR-002, amendment 6: the truck's connection is read again about once a minute. */
const val POLL_INTERVAL_MS = 60_000L

/**
 * A GPS fix stands in for the minute timer once the timer is this late. Two fixes of margin, so
 * the timer goes first when it is working.
 */
const val POLL_LATE_AFTER_MS = 10_000L

/**
 * Decides when the truck's connection is read again during a trip.
 *
 * The trip service has a timer for it. A timer in the service can stall while the phone sleeps
 * between GPS fixes, and the fixes themselves keep arriving, so a fix stands in for a timer
 * that is late. The readings must stay about a minute apart whichever of the two prompts them:
 * two readings of "not connected" in a row are believed ([LostDisconnectDetector]), and two
 * taken seconds apart would be worth no more than one.
 *
 * Times are elapsed-realtime milliseconds, a clock that keeps counting while the phone sleeps
 * and never jumps. Not thread-safe: the trip service calls it on the main thread only.
 */
class PollPacer(private val intervalMs: Long = POLL_INTERVAL_MS) {
    private var lastAtMs = 0L

    /** Recording has begun. The first reading is due one interval from now. */
    fun start(nowMs: Long) {
        lastAtMs = nowMs
    }

    /**
     * The timer ran out.
     *
     * @return false if a fix stood in for it less than half an interval ago: the timer was late,
     * and a reading now would come too soon after that one.
     */
    fun timerFired(nowMs: Long): Boolean = readIfDue(nowMs, after = intervalMs / 2)

    /**
     * A GPS fix arrived.
     *
     * @return true if the timer is late and the truck must be read now.
     */
    fun fixArrived(nowMs: Long): Boolean = readIfDue(nowMs, after = intervalMs + POLL_LATE_AFTER_MS)

    private fun readIfDue(nowMs: Long, after: Long): Boolean {
        if (nowMs - lastAtMs < after) return false
        lastAtMs = nowMs
        return true
    }
}
