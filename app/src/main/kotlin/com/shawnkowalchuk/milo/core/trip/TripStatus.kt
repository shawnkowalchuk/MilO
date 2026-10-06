package com.shawnkowalchuk.milo.core.trip

/** Where a stored trip is in its life. At most one trip is [OPEN] at any time. */
enum class TripStatus {
    /**
     * Being recorded, or waiting out the grace period after the truck disconnected. Which of the
     * two is told by the trip's grace columns, so the status and the timer cannot disagree.
     */
    OPEN,

    /** Closed and long enough to keep. This is a trip Shawn sees. */
    FINISHED,

    /**
     * Closed but under the minimum trip distance, for example moving the truck around the yard.
     * The row and its raw points are kept, not deleted, so a trip dropped because of a wrong
     * distance (a bad threshold, a phone that reports poor accuracy) can still be recovered.
     */
    DISCARDED,
}
