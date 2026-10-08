package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import com.shawnkowalchuk.milo.platform.trip.UnreadableSettingsFile
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The unit distances are shown in (Shawn's request of 2026-10-07): one stored setting, read
 * wherever a distance is written, and kept in memory for the surfaces that cannot wait for the
 * settings file.
 */
// runCurrent() is how a test lets the follower run. The API is marked experimental by the
// coroutines library; there is no stable equivalent.
@OptIn(ExperimentalCoroutinesApi::class)
class UnitStorageTest {
    private val file = FakeSettingsFile()
    private val store = SettingsStore(file)
    private val key = stringPreferencesKey("distance_unit")

    @Test
    fun `out of the box distances are in kilometres, and nothing is stored`() = runTest {
        assertEquals(DistanceUnit.KILOMETRES, store.current().distanceUnit)
        assertNull(file.data.first()[key])
    }

    @Test
    fun `miles are stored, and kilometres again, and nothing else is touched`() = runTest {
        store.setReportName("Sam Driver")
        val before = store.current()

        store.setDistanceUnit(DistanceUnit.MILES)

        assertEquals(before.copy(distanceUnit = DistanceUnit.MILES), store.current())

        store.setDistanceUnit(DistanceUnit.KILOMETRES)

        assertEquals(before, store.current())
    }

    @Test
    fun `the words the unit is stored under never change`() = runTest {
        // Pinned: a settings file written today must mean the same to every later version.
        store.setDistanceUnit(DistanceUnit.MILES)
        assertEquals("mi", file.data.first()[key])

        store.setDistanceUnit(DistanceUnit.KILOMETRES)
        assertEquals("km", file.data.first()[key])

        assertEquals(DistanceUnit.entries.toSet(), setOf("km", "mi").map(::distanceUnitOf).toSet())
    }

    @Test
    fun `a stored word MilO could not have written is read as kilometres, never guessed at`() =
        runTest {
            for (word in listOf("miles", "MILES", "", "yd")) {
                file.edit { it[key] = word }

                assertEquals(word, DistanceUnit.KILOMETRES, store.current().distanceUnit)
            }
        }

    // ---- The unit in memory, which every surface follows -----------------------------------------

    @Test
    fun `the unit in memory is kilometres until the file is read, then what is stored`() = runTest {
        store.setDistanceUnit(DistanceUnit.MILES)
        val shown = ShownUnit(store.settings, backgroundScope) { throw it }

        // Before the first read: the unit of a phone on which none was picked.
        assertEquals(DistanceUnit.KILOMETRES, shown.unit.value)

        shown.start()
        runCurrent()

        assertEquals(DistanceUnit.MILES, shown.unit.value)
    }

    @Test
    fun `a change in Settings reaches whoever follows the unit at once, in both directions`() =
        runTest {
            val shown = ShownUnit(store.settings, backgroundScope) { throw it }
            shown.start()
            runCurrent()

            store.setDistanceUnit(DistanceUnit.MILES)
            runCurrent()
            assertEquals(DistanceUnit.MILES, shown.unit.value)

            store.setDistanceUnit(DistanceUnit.KILOMETRES)
            runCurrent()
            assertEquals(DistanceUnit.KILOMETRES, shown.unit.value)
        }

    @Test
    fun `a settings file that cannot be read leaves kilometres, and is said once`() = runTest {
        val failures = mutableListOf<IOException>()
        val unreadable = SettingsStore(UnreadableSettingsFile)
        val shown = ShownUnit(unreadable.settings, backgroundScope) { failures += it }

        shown.start()
        runCurrent()

        assertEquals(DistanceUnit.KILOMETRES, shown.unit.value)
        assertEquals(1, failures.size)
        assertTrue(failures.single().message.orEmpty().contains("damaged"))
    }
}
