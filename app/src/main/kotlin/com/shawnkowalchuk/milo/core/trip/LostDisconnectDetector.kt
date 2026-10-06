package com.shawnkowalchuk.milo.core.trip

/** How many "not connected" readings in a row it takes to believe a disconnect nobody reported. */
const val READINGS_TO_BELIEVE_DISCONNECT = 2

/**
 * Catches a disconnect that was never reported. While a trip is open the service reads the
 * truck's connection about once a minute ([PollPacer]), and each reading that contradicts
 * "connected" comes here, once some reading has shown the truck connected at all. One such
 * reading is not enough: the Bluetooth profile state can lag behind the link, and a trip must
 * not be put into its grace period by a lag. Two in a row are believed.
 *
 * It is deliberately kept out of [TripStateMachine]. Those rules take levels, and telling them
 * the same thing twice changes nothing; this is a counter, where the second telling is the point.
 * Not thread-safe: the trip controller is its only caller.
 */
class LostDisconnectDetector(private val readingsNeeded: Int = READINGS_TO_BELIEVE_DISCONNECT) {
    private var missedInARow = 0

    /**
     * Takes one periodic reading.
     *
     * @return true when the truck must now be taken as disconnected.
     */
    fun onReading(connected: Boolean): Boolean {
        if (connected) {
            missedInARow = 0
            return false
        }
        missedInARow++
        if (missedInARow < readingsNeeded) return false
        missedInARow = 0
        return true
    }

    /** Something trustworthy showed the truck connected: the count starts again. */
    fun reset() {
        missedInARow = 0
    }
}
