package com.shawnkowalchuk.milo.data.settings

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.shawnkowalchuk.milo.platform.trip.FakeSettingsFile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sounds of Shawn's own, as a list to choose from (since 2026-10-07), and the two sounds
 * that choose from it (since 2026-10-08).
 */
class SoundListStorageTest {
    private val file = FakeSettingsFile()
    private val store = SettingsStore(file)

    private val chirpless = "file:/data/trip_sound/own_trip_start_sound_1"
    private val mario = "file:/data/trip_sound/own_trip_start_sound_2"

    @Test
    fun `out of the box the list is empty and the chirp plays`() = runTest {
        assertTrue(store.current().ownSounds.isEmpty())
        assertNull(store.current().customSoundUri)
    }

    @Test
    fun `an added sound joins the list and plays`() = runTest {
        store.addOwnSound(TripSound.CONNECT, chirpless, "r2d2.mp3")
        store.addOwnSound(TripSound.CONNECT, mario, "mario-1-up.mp3")

        val now = store.current()
        assertEquals(
            listOf(OwnSound(chirpless, "r2d2.mp3"), OwnSound(mario, "mario-1-up.mp3")),
            now.ownSounds,
        )
        assertEquals(mario, now.customSoundUri)
        assertEquals("mario-1-up.mp3", now.customSoundName)
    }

    @Test
    fun `a sound on the list is chosen again by its copy, and one not on it is not`() = runTest {
        store.addOwnSound(TripSound.CONNECT, chirpless, "r2d2.mp3")
        store.addOwnSound(TripSound.CONNECT, mario, null)

        assertTrue(store.chooseOwnSound(TripSound.CONNECT, chirpless))
        assertEquals("r2d2.mp3", store.current().customSoundName)

        assertFalse(store.chooseOwnSound(TripSound.CONNECT, "file:/data/trip_sound/gone"))
        assertEquals(chirpless, store.current().customSoundUri)
    }

    @Test
    fun `removing the sound in use brings the chirp back, and the others stay`() = runTest {
        store.addOwnSound(TripSound.CONNECT, chirpless, "r2d2.mp3")
        store.addOwnSound(TripSound.CONNECT, mario, "mario-1-up.mp3")

        store.removeOwnSound(mario)

        val now = store.current()
        assertNull(now.customSoundUri)
        assertNull(now.customSoundName)
        assertEquals(listOf(OwnSound(chirpless, "r2d2.mp3")), now.ownSounds)
    }

    @Test
    fun `the one sound chosen before there was a list is on it`() = runTest {
        // What a build from before 2026-10-07 left in the file.
        file.edit {
            it[stringPreferencesKey("custom_sound_uri")] = chirpless
            it[stringPreferencesKey("custom_sound_name")] = "r2d2.mp3"
        }

        assertEquals(listOf(OwnSound(chirpless, "r2d2.mp3")), store.current().ownSounds)
        store.addOwnSound(TripSound.CONNECT, mario, "mario-1-up.mp3")
        assertEquals(2, store.current().ownSounds.size)
    }

    @Test
    fun `an entry the setters cannot have written is skipped`() = runTest {
        file.edit {
            it[stringSetPreferencesKey("own_sounds")] =
                setOf("no tab", "\tname", "$mario\t")
        }

        assertEquals(listOf(OwnSound(mario, null)), store.current().ownSounds)
    }

    // ---- Two sounds, one list -------------------------------------------------------------------

    @Test
    fun `out of the box both sounds are on and play their built-in sound`() = runTest {
        for (which in TripSound.entries) {
            assertEquals(
                SoundChoice(enabled = true, ownUri = null, ownName = null),
                store.current().sound(which),
            )
        }
    }

    @Test
    fun `a sound added for the trip-start sound is on the one list, and the connect sound stays`() =
        runTest {
            store.addOwnSound(TripSound.CONNECT, chirpless, "r2d2.mp3")
            store.addOwnSound(TripSound.DRIVING_OFF, mario, "mario-1-up.mp3")

            val now = store.current()
            assertEquals(2, now.ownSounds.size)
            assertEquals(chirpless, now.sound(TripSound.CONNECT).ownUri)
            assertEquals(mario, now.sound(TripSound.DRIVING_OFF).ownUri)
            assertEquals("mario-1-up.mp3", now.drivingOffSoundName)

            // Either can take the other's sound.
            assertTrue(store.chooseOwnSound(TripSound.DRIVING_OFF, chirpless))
            assertEquals(chirpless, store.current().sound(TripSound.DRIVING_OFF).ownUri)
        }

    @Test
    fun `removing a sound both play brings back the built-in sound of each`() = runTest {
        store.addOwnSound(TripSound.CONNECT, mario, "mario-1-up.mp3")
        store.chooseOwnSound(TripSound.DRIVING_OFF, mario)

        store.removeOwnSound(mario)

        val now = store.current()
        assertNull(now.sound(TripSound.CONNECT).ownUri)
        assertNull(now.sound(TripSound.DRIVING_OFF).ownUri)
        assertNull(now.drivingOffSoundName)
        assertTrue(now.ownSounds.isEmpty())
    }

    @Test
    fun `the switch of one sound leaves the other alone`() = runTest {
        store.setSoundEnabled(TripSound.DRIVING_OFF, false)

        assertFalse(store.current().sound(TripSound.DRIVING_OFF).enabled)
        assertTrue(store.current().sound(TripSound.CONNECT).enabled)
    }
}
