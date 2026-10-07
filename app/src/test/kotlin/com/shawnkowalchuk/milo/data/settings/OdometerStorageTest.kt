package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The odometer readings Shawn typed in, kept in the settings file, corrections and all. */
class OdometerStorageTest {
    private val file = FakeSettingsFile()
    private val store = SettingsStore(file)

    @Test
    fun `out of the box there is no reading`() = runTest {
        assertTrue(store.current().odometerReadings.isEmpty())
    }

    @Test
    fun `every reading is kept, and they read back oldest first`() = runTest {
        val later = OdometerReading(1_791_100_000_000, 123_500)
        val earlier = OdometerReading(1_791_000_000_000, 123_456)

        store.addOdometerReading(later)
        store.addOdometerReading(earlier)

        assertEquals(listOf(earlier, later), store.current().odometerReadings)
    }

    @Test
    fun `a reading that is not one is refused, and nothing is stored`() = runTest {
        for (km in listOf(-1L, 10_000_000L)) {
            val refused = runCatching { store.addOdometerReading(OdometerReading(1_000, km)) }
            assertTrue("$km km", refused.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(store.current().odometerReadings.isEmpty())
    }

    @Test
    fun `an entry the setter cannot have written is skipped, not guessed at`() = runTest {
        file.edit {
            it[stringSetPreferencesKey("odometer_readings")] =
                setOf("1791000000000:123456", "garbage", "12:", ":5", "-4:100", "7:-100")
        }

        assertEquals(
            listOf(OdometerReading(1_791_000_000_000, 123_456)),
            store.current().odometerReadings,
        )
    }
}
