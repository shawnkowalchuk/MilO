package com.shawnkowalchuk.milo.core.trip

/**
 * What started a trip. It is stored with the trip, and it decides which ending rules apply
 * (ADR-002): a trip the truck never joined cannot end on a Bluetooth disconnect, so it has the
 * no-movement guard instead.
 */
enum class TripStartCause {
    /** The truck was seen connected: by a Bluetooth event, or by a check at boot or launch. */
    TRUCK,

    /** Shawn pressed Start, on the phone or on the Android Auto screen. */
    MANUAL,
}
