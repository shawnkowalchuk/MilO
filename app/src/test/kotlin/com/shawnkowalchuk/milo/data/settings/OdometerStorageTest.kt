package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.util.DistanceUnit
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import kotlinx.coroutines.flow.first
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
        val later = OdometerReading(1_791_100_000_000, 123_500, DistanceUnit.KILOMETRES)
        val earlier = OdometerReading(1_791_000_000_000, 123_456, DistanceUnit.KILOMETRES)

        store.addOdometerReading(later)
        store.addOdometerReading(earlier)

        assertEquals(listOf(earlier, later), store.current().odometerReadings)
    }

    @Test
    fun `a reading that is not one is refused, and nothing is stored`() = runTest {
        for (km in listOf(-1L, 10_000_000L)) {
            val refused =
                runCatching {
                    store.addOdometerReading(OdometerReading(1_000, km, DistanceUnit.KILOMETRES))
                }
            assertTrue("$km km", refused.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(store.current().odometerReadings.isEmpty())
    }

    // ---- A reading typed in miles (2026-10-07) ----------------------------------------------------

    @Test
    fun `a reading typed in miles comes back as it was typed, in miles`() = runTest {
        val inKilometres = OdometerReading(1_791_000_000_000, 123_456, DistanceUnit.KILOMETRES)
        val inMiles = OdometerReading(1_791_100_000_000, 76_543, DistanceUnit.MILES)

        store.addOdometerReading(inKilometres)
        store.addOdometerReading(inMiles)

        assertEquals(listOf(inKilometres, inMiles), store.current().odometerReadings)
    }

    @Test
    fun `a reading in kilometres is stored as it always was, and one in miles names its unit`() =
        runTest {
            store.addOdometerReading(OdometerReading(1_000, 123_456, DistanceUnit.KILOMETRES))
            store.addOdometerReading(OdometerReading(2_000, 76_543, DistanceUnit.MILES))

            // Pinned: the first is the entry every build before 2026-10-07 wrote and reads.
            assertEquals(
                setOf("1000:123456", "2000:76543:mi"),
                file.data.first()[stringSetPreferencesKey("odometer_readings")],
            )
        }

    @Test
    fun `every reading from before there was a choice of unit is a reading in kilometres`() =
        runTest {
            file.edit {
                it[stringSetPreferencesKey("odometer_readings")] = setOf("1791000000000:123456")
            }

            assertEquals(
                listOf(OdometerReading(1_791_000_000_000, 123_456, DistanceUnit.KILOMETRES)),
                store.current().odometerReadings,
            )
        }

    @Test
    fun `an entry with a unit MilO does not write is skipped, never read as kilometres`() =
        runTest {
            file.edit {
                it[stringSetPreferencesKey("odometer_readings")] =
                    setOf("1000:500:yd", "2000:600:", "3000:700:mi:extra", "4000:800:mi")
            }

            assertEquals(
                listOf(OdometerReading(4_000, 800, DistanceUnit.MILES)),
                store.current().odometerReadings,
            )
        }

    @Test
    fun `an entry the setter cannot have written is skipped, not guessed at`() = runTest {
        file.edit {
            it[stringSetPreferencesKey("odometer_readings")] =
                setOf("1791000000000:123456", "garbage", "12:", ":5", "-4:100", "7:-100")
        }

        assertEquals(
            listOf(OdometerReading(1_791_000_000_000, 123_456, DistanceUnit.KILOMETRES)),
            store.current().odometerReadings,
        )
    }
}
