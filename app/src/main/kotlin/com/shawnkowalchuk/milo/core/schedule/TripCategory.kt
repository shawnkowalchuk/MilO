package com.shawnkowalchuk.milo.core.schedule

/**
 * What a trip is saved as. The monthly report is made of the Business trips only, so this is
 * the one fact about a trip that decides whether accounts ever sees it.
 *
 * Stored with the trip by name, so a constant can be added but never renamed without a
 * migration. A trip that has not been sorted yet has no category at all (null in storage):
 * one that is still being recorded, and one recorded before MilO had a work schedule, until the
 * catch-up at the next process start has looked at it.
 */
enum class TripCategory {
    /** Driven for work: it started inside the work schedule, or Shawn marked it so. */
    BUSINESS,

    /** Not for work. Listed and added up apart from the Business trips. */
    PERSONAL,
}
