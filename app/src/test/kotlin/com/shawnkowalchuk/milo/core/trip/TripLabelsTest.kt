package com.shawnkowalchuk.milo.core.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The shop's yard, and a spot about 150 m east of it. */
private const val SHOP_LATITUDE = 53.5461
private const val SHOP_LONGITUDE = -113.4938
private const val NEAR_SHOP_LONGITUDE = -113.4916

/** A supplier across town, kilometres away. */
private const val SUPPLIER_LATITUDE = 53.4800
private const val SUPPLIER_LONGITUDE = -113.5100

/**
 * The labels offered for a trip (2026-10-08): every label used, newest first, with the one used
 * before where this trip ended at the top.
 */
class TripLabelsTest {
    private val work = LabelledTrip("Work", SHOP_LATITUDE, SHOP_LONGITUDE, startedAtMs = 1_000)
    private val supplier =
        LabelledTrip("Supplier", SUPPLIER_LATITUDE, SUPPLIER_LONGITUDE, startedAtMs = 3_000)
    private val handTyped = LabelledTrip("Bank", null, null, startedAtMs = 2_000)

    @Test
    fun `at a place labelled before, that label is suggested and comes first`() {
        val choices =
            labelChoices(SHOP_LATITUDE, NEAR_SHOP_LONGITUDE, listOf(work, supplier, handTyped))

        assertEquals("Work", choices.suggested)
        assertEquals(listOf("Work", "Supplier", "Bank"), choices.used)
    }

    @Test
    fun `at a new place nothing is suggested, and the labels are newest first`() {
        val choices = labelChoices(53.6, -113.2, listOf(work, supplier, handTyped))

        assertNull(choices.suggested)
        assertEquals(listOf("Supplier", "Bank", "Work"), choices.used)
    }

    @Test
    fun `just past 200 m is another place`() {
        // About 0.0035 degrees of longitude here is 230 m.
        val choices = labelChoices(SHOP_LATITUDE, SHOP_LONGITUDE + 0.0035, listOf(work))

        assertNull(choices.suggested)
    }

    @Test
    fun `of two labels used at one place, the newer is suggested`() {
        val later = work.copy(label = "Yard", startedAtMs = 5_000)

        val choices = labelChoices(SHOP_LATITUDE, SHOP_LONGITUDE, listOf(work, later))

        assertEquals("Yard", choices.suggested)
        assertEquals(listOf("Yard", "Work"), choices.used)
    }

    @Test
    fun `a trip without a position has nothing suggested`() {
        assertNull(labelChoices(null, null, listOf(work)).suggested)
    }

    @Test
    fun `labels that differ in capitals are one, written as last used`() {
        val older = work.copy(label = "work", startedAtMs = 500)

        assertEquals(listOf("Work"), labelChoices(null, null, listOf(older, work)).used)
    }

    @Test
    fun `a typed label is tidied, cut to its length, and empty takes it away`() {
        assertEquals("Site 4", cleanLabel("  Site   4 "))
        assertNull(cleanLabel("   "))
        assertEquals(MAX_LABEL_LENGTH, cleanLabel("x".repeat(60))?.length)
    }
}
