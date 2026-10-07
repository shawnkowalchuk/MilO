package com.shawnkowalchuk.milo.feature.eventlog

import com.shawnkowalchuk.milo.data.eventlog.EventCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which chip of the Log screen each of the log's categories is found under, and its tag. */
class LogGroupsTest {
    /**
     * Where every category belongs, written out. **A new category makes this test fail** until
     * it is added here, which is the moment to decide which chip its lines are found under.
     */
    private val placed: Map<EventCategory, LogGroup?> =
        mapOf(
            EventCategory.TRIP to LogGroup.TRIPS,
            EventCategory.GRACE to LogGroup.TRIPS,
            EventCategory.ADDRESS to LogGroup.TRIPS,
            EventCategory.TRIGGER to LogGroup.BLUETOOTH,
            EventCategory.PAIRING to LogGroup.BLUETOOTH,
            EventCategory.ANDROID_AUTO to LogGroup.ANDROID_AUTO,
            EventCategory.ERROR to LogGroup.ERRORS,
            EventCategory.CRASH to LogGroup.ERRORS,
            // Under "All" only.
            EventCategory.PROCESS to null,
            EventCategory.SERVICE to null,
            EventCategory.LOCATION to null,
            EventCategory.DRIVING to null,
            EventCategory.REPORT to null,
        )

    @Test
    fun `every category of the log has been placed`() {
        assertEquals(
            "A category was added to the log and not placed under a chip, or under All only",
            EventCategory.entries.toSet(),
            placed.keys,
        )
    }

    @Test
    fun `each category is found under the chip it was placed under`() {
        for ((category, group) in placed) {
            assertEquals(category.name, group, category.logGroup())
        }
    }

    @Test
    fun `a chip reads exactly the categories placed under it`() {
        for (group in LogGroup.entries) {
            assertEquals(
                group.name,
                placed.filterValues { it == group }.keys,
                group.categories().toSet(),
            )
        }
    }

    @Test
    fun `no chip is empty and no category is under two chips`() {
        val all = LogGroup.entries.flatMap { it.categories() }

        for (group in LogGroup.entries) assertTrue(group.name, group.categories().isNotEmpty())
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `the tags carry the design's short words, and otherwise the category's own name`() {
        assertEquals("TRIP", EventCategory.TRIP.tagWord())
        assertEquals("BT", EventCategory.TRIGGER.tagWord())
        assertEquals("AUTO", EventCategory.ANDROID_AUTO.tagWord())
        assertEquals("SERVICE", EventCategory.SERVICE.tagWord())
        assertEquals("ERROR", EventCategory.ERROR.tagWord())

        val shortened = setOf(EventCategory.TRIGGER, EventCategory.ANDROID_AUTO)
        for (category in EventCategory.entries - shortened) {
            assertEquals(category.name, category.tagWord())
        }
    }

    @Test
    fun `no two categories carry the same word`() {
        val words = EventCategory.entries.map { it.tagWord() }

        assertEquals(words.size, words.toSet().size)
    }
}
