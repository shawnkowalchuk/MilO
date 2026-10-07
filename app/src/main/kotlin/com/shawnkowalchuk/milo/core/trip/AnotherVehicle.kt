package com.shawnkowalchuk.milo.core.trip

/**
 * How far a trip that a parked truck's moving started must get with the truck still connected
 * before it counts as the truck's (Shawn's decision of 2026-10-07: "1 km").
 *
 * The case it is for, in his words: "in rare occasions I am connected to my truck at work and I
 * may use another vehicle". The phone is still connected to the parked truck when the other
 * vehicle drives off, so the watch sees a drive and starts a trip; then the truck's Bluetooth is
 * lost as the phone leaves its range. That happens within a few hundred metres: the truck's
 * radio reaches tens of metres, and Android notices the lost link a few seconds later. In the
 * truck itself the connection stays up for the whole drive, and a drop is mended by the grace
 * period. A kilometre leaves room for a fast start and a slow notice, and is less than any trip
 * worth recording from a site.
 */
const val ANOTHER_VEHICLE_WITHIN_METRES = 1_000.0

/**
 * Whether a trip that is being closed was a drive in another vehicle, and is removed for good
 * (Shawn's choice of 2026-10-07: "Delete for good", over keeping it as discarded). All of this
 * must hold:
 * - it started because the parked, connected truck moved ([TripEffect.StartTrip.fromParked]),
 *   so no new link to the truck and no button vouches for it;
 * - it ends because the truck's connection was lost for good: its grace period ran out
 *   ([TripEndReason.GRACE_EXPIRED]). A trip the parked rule closes, with the truck still
 *   connected, was the truck, however short; so is one ended with End;
 * - up to the moment the truck was found gone, which is where the trip is cut, it came less
 *   than [ANOTHER_VEHICLE_WITHIN_METRES].
 *
 * Android Auto holds a trip as the truck does, so a trip with Android Auto connected never gets
 * here: its grace period does not start while Android Auto is there.
 *
 * @param closed the trip as [TripClosing] worked it out, cut where the truck was found gone.
 */
fun leftInAnotherVehicle(
    startedFromParked: Boolean,
    reason: TripEndReason,
    closed: ClosedTrip,
): Boolean = startedFromParked &&
    reason == TripEndReason.GRACE_EXPIRED &&
    closed.distance.metres < ANOTHER_VEHICLE_WITHIN_METRES
