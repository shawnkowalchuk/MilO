package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.shawnkowalchuk.milo.core.odometer.OdometerReading
import com.shawnkowalchuk.milo.core.odometer.TripAtReading
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

    // ---- A reading typed while a trip is being recorded (2026-10-10) ------------------------------

    private val readingsKey = stringSetPreferencesKey("odometer_readings")
    private val tripsKey = stringSetPreferencesKey("odometer_reading_trips")
    private val truck = "AA:BB:CC:DD:EE:FF"

    @Test
    fun `a reading typed during a trip comes back with the trip and how far it had gone`() =
        runTest {
            val beforeDrivingOff =
                OdometerReading(
                    1_791_000_000_000,
                    102_720,
                    DistanceUnit.KILOMETRES,
                    truck,
                    TripAtReading(tripId = 41, metres = 0.0),
                )
            val onTheWay =
                OdometerReading(
                    1_791_000_480_000,
                    63_827,
                    DistanceUnit.MILES,
                    truck,
                    TripAtReading(tripId = 41, metres = 4_012.345678901234),
                )
            val withNoTripOpen =
                OdometerReading(1_791_100_000_000, 102_900, DistanceUnit.KILOMETRES)

            store.addOdometerReading(onTheWay)
            store.addOdometerReading(withNoTripOpen)
            store.addOdometerReading(beforeDrivingOff)

            // The metres to the last digit: they are compared with the trip's own distance.
            assertEquals(
                listOf(beforeDrivingOff, onTheWay, withNoTripOpen),
                store.current().odometerReadings,
            )
        }

    @Test
    fun `the reading's own entry is what it always was, and the trip stands in a second set`() =
        runTest {
            store.addOdometerReading(
                OdometerReading(
                    1_000,
                    102_720,
                    DistanceUnit.KILOMETRES,
                    truck,
                    TripAtReading(41, 0.0),
                ),
            )
            store.addOdometerReading(
                OdometerReading(
                    2_000,
                    63_827,
                    DistanceUnit.MILES,
                    null,
                    TripAtReading(41, 4_000.5),
                ),
            )
            store.addOdometerReading(OdometerReading(3_000, 102_900, DistanceUnit.KILOMETRES))

            val stored = file.data.first()
            // Pinned: these are the entries a build from before 2026-10-10 writes and reads. It
            // skips an entry of any other form, so nothing may be added to them.
            assertEquals(
                setOf("1000:102720|$truck", "2000:63827:mi", "3000:102900"),
                stored[readingsKey],
            )
            assertEquals(setOf("1000:41:0.0", "2000:41:4000.5"), stored[tripsKey])
        }

    @Test
    fun `a file from before 2026-10-10 has no trips, and its readings are read as they were`() =
        runTest {
            file.edit { it[readingsKey] = setOf("1000:102720|$truck", "2000:63827:mi") }

            assertEquals(
                listOf(
                    OdometerReading(1_000, 102_720, DistanceUnit.KILOMETRES, truck),
                    OdometerReading(2_000, 63_827, DistanceUnit.MILES),
                ),
                store.current().odometerReadings,
            )
        }

    @Test
    fun `a reading an older build added to this build's file is one typed with no trip open`() =
        runTest {
            store.addOdometerReading(
                OdometerReading(
                    1_000,
                    102_720,
                    DistanceUnit.KILOMETRES,
                    truck,
                    TripAtReading(41, 0.0),
                ),
            )
            // The older build adds its entry to the readings and knows nothing of the second set,
            // which the settings file carries along untouched.
            file.edit { it[readingsKey] = it[readingsKey].orEmpty() + "5000:102800|$truck" }

            assertEquals(
                listOf(
                    OdometerReading(
                        1_000,
                        102_720,
                        DistanceUnit.KILOMETRES,
                        truck,
                        TripAtReading(41, 0.0),
                    ),
                    OdometerReading(5_000, 102_800, DistanceUnit.KILOMETRES, truck),
                ),
                store.current().odometerReadings,
            )
        }

    @Test
    fun `a trip note the setter cannot have written is skipped, and the reading stays`() = runTest {
        file.edit {
            it[readingsKey] = setOf("1000:100", "2000:200", "3000:300", "4000:400", "5000:500")
            it[tripsKey] =
                setOf(
                    "1000:41:-5.0",
                    "2000:41:NaN",
                    "3000:41",
                    "4000:x:12.0",
                    "garbage",
                    "5000:41:12.5",
                    // For a reading that is not there: it stands for nothing.
                    "9000:41:1.0",
                )
        }

        val read = store.current().odometerReadings

        assertEquals(
            listOf(null, null, null, null, TripAtReading(41, 12.5)),
            read.map {
                it.duringTrip
            },
        )
        assertEquals(listOf(100L, 200L, 300L, 400L, 500L), read.map { it.value })
    }

    @Test
    fun `two notes for one reading cannot both be right, and neither is used`() = runTest {
        file.edit {
            it[readingsKey] = setOf("1000:100")
            it[tripsKey] = setOf("1000:41:12.5", "1000:42:0.0")
        }

        assertEquals(
            listOf(OdometerReading(1_000, 100, DistanceUnit.KILOMETRES)),
            store.current().odometerReadings,
        )
    }

    @Test
    fun `a distance that cannot be one is refused before anything is stored`() = runTest {
        for (metres in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val refused = runCatching { TripAtReading(41, metres) }

            assertTrue("$metres m", refused.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun `a reading given its vehicle afterwards keeps the trip it was typed during`() = runTest {
        // Typed with no vehicle paired; the first vehicle is filled in once one is.
        store.addOdometerReading(
            OdometerReading(
                1_000,
                102_720,
                DistanceUnit.KILOMETRES,
                null,
                TripAtReading(41, 250.0),
            ),
        )

        store.fillOdometerVehicle(truck)

        assertEquals(
            listOf(
                OdometerReading(
                    1_000,
                    102_720,
                    DistanceUnit.KILOMETRES,
                    truck,
                    TripAtReading(41, 250.0),
                ),
            ),
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
