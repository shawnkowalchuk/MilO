package com.shawnkowalchuk.milo.core.trip

/**
 * Android Auto alone (the truck's Bluetooth gone) holds a trip open for this long at most. After
 * that its "connected" is taken for a stuck value. 12 hours is longer than any working day, so
 * a real drive is never cut; the value is a judgement, and Shawn's to change.
 */
const val ANDROID_AUTO_ALONE_LIMIT_MS = 12L * 60L * 60L * 1000L

/**
 * Stops a stuck Android Auto value from holding a trip open for ever. The value comes from the
 * Android Auto app, and nothing guarantees that it ever goes back to "not connected".
 *
 * While the truck is connected, Android Auto's word changes nothing, so it is simply passed on.
 * Once Android Auto alone has held the trip for [limitMs], it is no longer believed, and it
 * stays unbelieved until it has been seen to report "not connected" at least once: a value that
 * was stuck is still stuck after the truck's next visit.
 *
 * Not thread-safe: the trip controller is its only caller.
 */
class AndroidAutoHoldGuard(private val limitMs: Long = ANDROID_AUTO_ALONE_LIMIT_MS) {
    private var aloneSinceMs: Long? = null
    private var stuck = false

    /**
     * @param reported what Android Auto says now.
     * @param truckConnected what the trip rules believe about the truck now.
     * @return what to tell the trip rules about Android Auto.
     */
    fun believed(reported: Boolean, truckConnected: Boolean, atMs: Long): Boolean {
        if (!reported) {
            aloneSinceMs = null
            stuck = false
            return false
        }
        if (stuck) return false
        if (truckConnected) {
            aloneSinceMs = null
            return true
        }
        val since = aloneSinceMs ?: atMs.also { aloneSinceMs = it }
        stuck = atMs - since >= limitMs
        return !stuck
    }
}
