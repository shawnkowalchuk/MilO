package com.shawnkowalchuk.milo.feature.trips

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which days of the Trips screen show their trips: every day starts closed, a press on its
 * heading opens or closes it, another month starts closed again, and the day of a trip that
 * was just saved is opened.
 */
class OpenDaysTest {
    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)
    private val monday = LocalDate.of(2026, 10, 5)
    private val tuesday = LocalDate.of(2026, 10, 6)

    @Test
    fun `a press opens a closed day and leaves the others as they are`() {
        assertEquals(setOf(monday), dayToggled(emptySet(), monday))
        assertEquals(setOf(monday, tuesday), dayToggled(setOf(monday), tuesday))
    }

    @Test
    fun `a second press closes the day again`() {
        assertEquals(emptySet<LocalDate>(), dayToggled(dayToggled(emptySet(), monday), monday))
        assertEquals(setOf(tuesday), dayToggled(setOf(monday, tuesday), monday))
    }

    @Test
    fun `the open days stay open while the month on screen is the same`() {
        // A step forward on the current month goes nowhere, and must not close anything.
        assertEquals(setOf(monday), openDaysIn(october, was = october, open = setOf(monday)))
    }

    @Test
    fun `another month starts with every day closed`() {
        val open = setOf(monday, tuesday)

        assertEquals(emptySet<LocalDate>(), openDaysIn(september, was = october, open = open))
        // Back again as well: what was open in October is not remembered.
        assertEquals(emptySet<LocalDate>(), openDaysIn(october, was = september, open = open))
    }

    @Test
    fun `after a save the trip's day is open, beside the days that were`() {
        assertEquals(
            setOf(monday, tuesday),
            openDaysAfterSave(october, was = october, open = setOf(monday), savedDay = tuesday),
        )
        // A day that was open already stays open: a save is not a press.
        assertEquals(
            setOf(monday),
            openDaysAfterSave(october, was = october, open = setOf(monday), savedDay = monday),
        )
    }

    @Test
    fun `a save that leads to another month opens the trip's day there and nothing else`() {
        val inSeptember = LocalDate.of(2026, 9, 30)

        assertEquals(
            setOf(inSeptember),
            openDaysAfterSave(september, was = october, open = setOf(monday), inSeptember),
        )
    }

    @Test
    fun `a saved day outside the month on screen is not opened`() {
        // The month shown never goes past the current one. A trip that starts later than
        // that (a clock set back since) is not on screen, so there is no tile to open.
        val inNovember = LocalDate.of(2026, 11, 1)

        assertEquals(
            setOf(monday),
            openDaysAfterSave(october, was = october, open = setOf(monday), inNovember),
        )
    }
}
