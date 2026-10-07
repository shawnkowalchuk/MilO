package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.shawnkowalchuk.milo.core.util.formatMinutes
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parked rule in the settings file: the limit Shawn sets, and the wait beside a parked truck
 * that has to outlive the process.
 */
class ParkedStorageTest {
    private val file = FakeSettingsFile()
    private val store = SettingsStore(file)

    private val place = ParkedPlace(1_791_030_000_000, 53.5461, -113.4938, 6.5f)

    // ---- The limit ------------------------------------------------------------------------------

    @Test
    fun `out of the box a trip ends after ten minutes without movement`() = runTest {
        assertEquals(600, store.current().parkedLimitSeconds)
        assertEquals(DEFAULT_PARKED_LIMIT_SECONDS, MiloSettings().parkedLimitSeconds)
    }

    @Test
    fun `the limit is stored, and a limit that is not positive is refused`() = runTest {
        store.setParkedLimitSeconds(900)
        assertEquals(900, store.current().parkedLimitSeconds)

        for (seconds in listOf(0, -60)) {
            val refused = runCatching { store.setParkedLimitSeconds(seconds) }
            assertTrue("$seconds s", refused.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(900, store.current().parkedLimitSeconds)
    }

    @Test
    fun `a stored limit that is not positive reads as the default`() = runTest {
        // No setter writes one; with no limit a trip would never close.
        file.edit { it[intPreferencesKey("parked_limit_seconds")] = 0 }

        assertEquals(DEFAULT_PARKED_LIMIT_SECONDS, store.current().parkedLimitSeconds)
    }

    @Test
    fun `the Settings screen offers five to thirty minutes, in steps of five`() {
        val choice = PARKED_LIMIT_CHOICE
        val offered = (choice.min..choice.max step choice.step).toList()

        assertEquals(
            listOf("5", "10", "15", "20", "25", "30"),
            offered.map { formatMinutes(it, Locale.CANADA) },
        )
        assertTrue(DEFAULT_PARKED_LIMIT_SECONDS in offered)
        assertEquals(900, choice.stepUp(DEFAULT_PARKED_LIMIT_SECONDS))
        assertEquals(300, choice.stepDown(DEFAULT_PARKED_LIMIT_SECONDS))
    }

    // ---- The wait --------------------------------------------------------------------------------

    @Test
    fun `MilO is not waiting until it is told that it is`() = runTest {
        assertNull(store.current().parkedTruck)
    }

    @Test
    fun `a wait is stored with the place the truck is parked at, and read back the same`() =
        runTest {
            val parked = ParkedTruck(sinceMs = 1_791_030_600_000, place = place)

            store.setParkedTruck(parked)

            assertEquals(parked, store.current().parkedTruck)
        }

    @Test
    fun `a wait with no known place is a wait all the same`() = runTest {
        store.setParkedTruck(ParkedTruck(sinceMs = 1_791_030_600_000, place = null))

        assertEquals(ParkedTruck(1_791_030_600_000, null), store.current().parkedTruck)
    }

    @Test
    fun `a later wait without a place does not keep the place of the one before`() = runTest {
        store.setParkedTruck(ParkedTruck(sinceMs = 1_000, place = place))

        store.setParkedTruck(ParkedTruck(sinceMs = 2_000, place = null))

        assertEquals(ParkedTruck(2_000, null), store.current().parkedTruck)
    }

    @Test
    fun `ending the wait takes it out of the file, place and all`() = runTest {
        store.setParkedTruck(ParkedTruck(sinceMs = 1_000, place = place))

        store.setParkedTruck(null)

        assertNull(store.current().parkedTruck)
        assertEquals(MiloSettings(), store.current())
    }

    @Test
    fun `a file that holds only part of a place reads as a wait with no known place`() = runTest {
        store.setParkedTruck(ParkedTruck(sinceMs = 1_000, place = place))
        file.edit { it.remove(doublePreferencesKey("parked_place_longitude")) }

        assertEquals(ParkedTruck(1_000, null), store.current().parkedTruck)
    }

    @Test
    fun `a wait with a negative time is refused`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { store.setParkedTruck(ParkedTruck(-1, null)) }
        }
    }

    @Test
    fun `a restore by Android does not bring a wait from the other installation`() = runTest {
        store.setParkedTruck(ParkedTruck(sinceMs = 1_000, place = place))
        store.setParkedLimitSeconds(1_200)

        store.forgetOtherInstallation(dropAssociation = false, dropOwnSound = false)

        assertNull(store.current().parkedTruck)
        // The limit is a setting, and comes along.
        assertEquals(1_200, store.current().parkedLimitSeconds)
    }
}
