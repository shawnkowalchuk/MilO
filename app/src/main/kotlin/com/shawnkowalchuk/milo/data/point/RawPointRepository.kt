package com.shawnkowalchuk.milo.data.point

/** The only way the rest of the app reads or writes raw GPS points. */
class RawPointRepository(private val dao: RawPointDao) {
    /** Stores one fix. The id on [point] is ignored; the database assigns it. */
    suspend fun add(point: RawPoint) {
        dao.insert(point)
    }

    /** Every stored fix of a trip, in the order recorded. */
    suspend fun pointsForTrip(tripId: Long): List<RawPoint> = dao.findForTrip(tripId)

    /**
     * Removes every fix of a trip that was removed for good. The points are in a database file
     * of their own, so nothing removes them with the trip's row.
     */
    suspend fun removeForTrip(tripId: Long) {
        dao.deleteForTrip(tripId)
    }
}
