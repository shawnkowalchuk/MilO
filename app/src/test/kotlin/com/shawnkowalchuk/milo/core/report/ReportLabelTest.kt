package com.shawnkowalchuk.milo.core.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A trip's label, its purpose, and the vehicle it was in, on the report (2026-10-08): a line
 * under its addresses.
 */
class ReportLabelTest {
    private fun rows(vararg trips: ReportTrip): List<PrintedRow> =
        printedReport(report(trips.toList()), WORDS, FORMAT).days.flatMap { it.rows }

    @Test
    fun `a label is printed under the addresses, and a trip without one has no line`() {
        val printed =
            rows(
                trip(DAY_ONE, "08:00").copy(label = "Work"),
                trip(DAY_ONE, "09:00"),
            )

        assertEquals(listOf("Work", null), printed.map { it.detail })
    }

    @Test
    fun `the vehicle is named only when the trips were in more than one`() {
        val one =
            rows(
                trip(DAY_ONE, "08:00").copy(label = "Work", vehicle = "Van"),
                trip(DAY_ONE, "09:00").copy(vehicle = "Van"),
            )
        val two =
            rows(
                trip(DAY_ONE, "08:00").copy(label = "Work", vehicle = "Van"),
                trip(DAY_ONE, "09:00").copy(vehicle = "Work truck"),
            )

        assertEquals(listOf("Work", null), one.map { it.detail })
        assertEquals(listOf("Work · Van", "Work truck"), two.map { it.detail })
    }

    @Test
    fun `a row with a label is laid out taller than one without`() {
        val plain = layout(report(listOf(trip(DAY_ONE, "08:00"))))
        val labelled = layout(report(listOf(trip(DAY_ONE, "08:00").copy(label = "Supplier"))))

        val plainText = plain.first().items.filterIsInstance<PageItem.Text>().map { it.text }
        val labelledText = labelled.first().items.filterIsInstance<PageItem.Text>().map { it.text }
        assertFalse("Supplier" in plainText)
        assertTrue("Supplier" in labelledText)
    }

    @Test
    fun `a report without labels or vehicles prints its rows as before`() {
        assertNull(rows(trip(DAY_ONE, "08:00")).single().detail)
    }
}
