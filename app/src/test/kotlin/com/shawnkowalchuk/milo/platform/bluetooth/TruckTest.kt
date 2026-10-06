package com.shawnkowalchuk.milo.platform.bluetooth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.SettingsStore
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ADDRESS = "AA:BB:CC:DD:EE:FF"

/** Which device is the truck, and how a trigger finds that out on the spot. */
class TruckTest {
    private val truck = Truck(ADDRESS, "Work truck", associationId = 7)

    @Test
    fun `an event is the truck's if it names the truck's address, in any case`() {
        assertTrue(truck.isDevice(ADDRESS, associationId = null))
        assertTrue(truck.isDevice("aa:bb:cc:dd:ee:ff", associationId = null))
        assertFalse(truck.isDevice("11:22:33:44:55:66", associationId = null))
    }

    @Test
    fun `an event is the truck's if it names the truck's association`() {
        assertTrue(truck.isDevice(address = null, associationId = 7))
        assertFalse(truck.isDevice(address = null, associationId = 8))
    }

    @Test
    fun `an event that names nothing is not the truck's`() {
        assertFalse(truck.isDevice(address = null, associationId = null))
        // Android 12 has no association ids. No id on both sides is not a match.
        val withoutId = truck.copy(associationId = null)
        assertFalse(withoutId.isDevice(address = null, associationId = null))
    }

    @Test
    fun `an address is stored the way Android's Bluetooth classes accept it`() {
        assertEquals(ADDRESS, "aa:bb:cc:dd:ee:ff".asBluetoothAddress())
    }

    @Test
    fun `the settings hold a truck only once an address is stored`() {
        assertNull(MiloSettings().truck())
        assertEquals(
            Truck(ADDRESS, name = null, associationId = null),
            MiloSettings(truckAddress = ADDRESS).truck(),
        )
    }

    @Test
    fun `a trigger is told the paired truck at once`() {
        val settings = SettingsStore(FakeSettingsFile())
        runBlocking { settings.setTruck(ADDRESS, "Work truck", associationId = 7) }

        assertEquals(TruckLookup(truck), PairedTruck(settings).now())
    }

    @Test
    fun `before pairing there is no truck and no problem`() {
        val lookup = PairedTruck(SettingsStore(FakeSettingsFile())).now()

        assertEquals(TruckLookup(truck = null, problem = null), lookup)
    }

    @Test
    fun `an unreadable settings file is a problem, not no truck`() {
        // "No truck" would make the receiver ignore the truck's own broadcasts in silence.
        val lookup = PairedTruck(SettingsStore(UnreadableSettingsFile)).now()

        assertNull(lookup.truck)
        assertNotNull(lookup.problem)
    }
}

/** A settings file that cannot be read, as DataStore reports a corrupt one. */
private object UnreadableSettingsFile : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw IOException("corrupt") }

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences = throw IOException("corrupt")
}
