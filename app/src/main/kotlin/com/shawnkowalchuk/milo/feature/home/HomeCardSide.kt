package com.shawnkowalchuk.milo.feature.home

// The card beside the truck's while a trip is recorded: today's Business kilometres, and on
// its other side the month's (Shawn, 2026-10-10: "can we have it when you click it it toggles
// between days and month"). Which side is up is two plain functions, so the rule is tested
// without a phone; the screen only keeps the one value they are handed.

/** The two sides of the card. */
internal enum class CardSide {
    /** "Today · business": the side that is up unless the month was asked for. */
    TODAY,

    /** The month's name, its Business kilometres, its share and its dollars. */
    MONTH,
}

/**
 * The side that is up during the trip [tripId].
 *
 * **Every trip starts with Today.** The month stays up only for the trip it was asked for in:
 * the card is turned to see the month once, and the next drive begins with the day's figure,
 * which is the one that changes with each trip.
 *
 * @param monthChosenIn the trip during which the card was last turned to the month and not
 * turned back, or null if it never was.
 */
internal fun cardSide(monthChosenIn: Long?, tripId: Long): CardSide =
    if (monthChosenIn == tripId) CardSide.MONTH else CardSide.TODAY

/**
 * What a press on the card during the trip [tripId] leaves to remember: that trip while the
 * month is now up, and nothing once it has been turned back to Today.
 */
internal fun afterPress(monthChosenIn: Long?, tripId: Long): Long? =
    when (cardSide(monthChosenIn, tripId)) {
        CardSide.TODAY -> tripId
        CardSide.MONTH -> null
    }
