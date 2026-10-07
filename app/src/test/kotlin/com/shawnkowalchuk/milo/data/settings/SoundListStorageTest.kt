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

/** The trip-start sounds of Shawn's own, as a list to choose from (since 2026-10-07). */
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
        store.addOwnSound(chirpless, "r2d2.mp3")
        store.addOwnSound(mario, "mario-1-up.mp3")

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
        store.addOwnSound(chirpless, "r2d2.mp3")
        store.addOwnSound(mario, null)

        assertTrue(store.chooseOwnSound(chirpless))
        assertEquals("r2d2.mp3", store.current().customSoundName)

        assertFalse(store.chooseOwnSound("file:/data/trip_sound/gone"))
        assertEquals(chirpless, store.current().customSoundUri)
    }

    @Test
    fun `removing the sound in use brings the chirp back, and the others stay`() = runTest {
        store.addOwnSound(chirpless, "r2d2.mp3")
        store.addOwnSound(mario, "mario-1-up.mp3")

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
        store.addOwnSound(mario, "mario-1-up.mp3")
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
}
