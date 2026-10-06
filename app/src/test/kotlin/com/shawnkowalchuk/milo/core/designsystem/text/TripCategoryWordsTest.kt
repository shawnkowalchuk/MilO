package com.shawnkowalchuk.milo.core.designsystem.text

import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which words say what a closed trip is saved as, on Home and on Trips alike. */
class TripCategoryWordsTest {
    @Test
    fun `a Business trip says so, and says when it ran past the schedule`() {
        assertEquals(R.string.trip_business, categoryWordsRes(TripCategory.BUSINESS, false))
        assertEquals(R.string.trip_business_ran_past, categoryWordsRes(TripCategory.BUSINESS, true))
    }

    @Test
    fun `a Personal trip is never said to have run past the schedule`() {
        assertEquals(R.string.trip_personal, categoryWordsRes(TripCategory.PERSONAL, false))
        assertEquals(R.string.trip_personal, categoryWordsRes(TripCategory.PERSONAL, true))
    }

    @Test
    fun `a trip that is not sorted yet is passed off as neither`() {
        assertEquals(R.string.trip_unsorted, categoryWordsRes(category = null, false))
        assertEquals(R.string.trip_unsorted, categoryWordsRes(category = null, true))
    }
}
