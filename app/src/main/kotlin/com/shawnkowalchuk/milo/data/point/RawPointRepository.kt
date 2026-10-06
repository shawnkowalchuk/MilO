package com.shawnkowalchuk.milo.data.point

/** The only way the rest of the app reads or writes raw GPS points. */
class RawPointRepository(private val dao: RawPointDao) {
    /** Stores one fix. The id on [point] is ignored; the database assigns it. */
    suspend fun add(point: RawPoint) {
        dao.insert(point)
    }

    /** Every stored fix of a trip, in the order recorded. */
    suspend fun pointsForTrip(tripId: Long): List<RawPoint> = dao.findForTrip(tripId)
}
