package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.data.settings.GRACE_PERIOD_CHOICE
import com.shawnkowalchuk.milo.data.settings.MINIMUM_TRIP_DISTANCE_CHOICE
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.platform.trip.OwnSoundRefusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Settings screen shows for the settings as they are stored. */
class SettingsUiStateTest {
    private fun shown(
        settings: MiloSettings = MiloSettings(),
        copyingSound: Boolean = false,
        problem: SettingsProblem? = null,
    ) = settingsUiState(settings, copyingSound, problem)

    @Test
    fun `a fresh install shows the defaults from the brief`() {
        val state = shown()

        assertFalse(state.truckPaired)
        assertNull(state.truckName)
        assertEquals(120, state.gracePeriodSeconds)
        assertEquals(300, state.minimumDistanceMetres)
        assertTrue(state.soundEnabled)
        assertFalse(state.usesOwnSound)
        assertFalse(state.copyingSound)
        assertNull(state.problem)
    }

    @Test
    fun `at the defaults both numbers can go either way`() {
        val state = shown()

        assertTrue(state.canShortenGrace && state.canLengthenGrace)
        assertTrue(state.canLowerMinimum && state.canRaiseMinimum)
    }

    @Test
    fun `at the end of a range the button that leads out of it is switched off`() {
        val lowest =
            shown(
                MiloSettings(
                    gracePeriodSeconds = GRACE_PERIOD_CHOICE.min,
                    minimumTripDistanceMetres = MINIMUM_TRIP_DISTANCE_CHOICE.min,
                ),
            )
        val highest =
            shown(
                MiloSettings(
                    gracePeriodSeconds = GRACE_PERIOD_CHOICE.max,
                    minimumTripDistanceMetres = MINIMUM_TRIP_DISTANCE_CHOICE.max,
                ),
            )

        assertFalse(lowest.canShortenGrace || lowest.canLowerMinimum)
        assertTrue(lowest.canLengthenGrace && lowest.canRaiseMinimum)
        assertFalse(highest.canLengthenGrace || highest.canRaiseMinimum)
        assertTrue(highest.canShortenGrace && highest.canLowerMinimum)
    }

    @Test
    fun `a stored value outside the range is shown as it is, with the way back in`() {
        // Zero is a value the settings store accepts and the screen does not offer.
        val state = shown(MiloSettings(gracePeriodSeconds = 0, minimumTripDistanceMetres = 5_000))

        assertEquals(0, state.gracePeriodSeconds)
        assertTrue(state.canLengthenGrace)
        assertFalse(state.canShortenGrace)
        assertEquals(5_000, state.minimumDistanceMetres)
        assertTrue(state.canLowerMinimum)
        assertFalse(state.canRaiseMinimum)
    }

    @Test
    fun `a paired truck is shown by its name, or as paired without one`() {
        val named = shown(MiloSettings(truckAddress = "AA:BB:CC:DD:EE:FF", truckName = "F-150"))
        val unnamed = shown(MiloSettings(truckAddress = "AA:BB:CC:DD:EE:FF"))

        assertTrue(named.truckPaired)
        assertEquals("F-150", named.truckName)
        assertTrue(unnamed.truckPaired)
        assertNull(unnamed.truckName)
    }

    @Test
    fun `the sound in use is Shawn's own exactly while the settings name a file`() {
        val own =
            shown(
                MiloSettings(
                    customSoundUri = "file:/data/trip_sound/own_trip_start_sound_1",
                    customSoundName = "r2d2.mp3",
                ),
            )
        val ownWithoutName =
            shown(MiloSettings(customSoundUri = "file:/data/trip_sound/own_trip_start_sound_1"))

        assertTrue(own.usesOwnSound)
        assertEquals("r2d2.mp3", own.ownSoundName)
        assertTrue(ownWithoutName.usesOwnSound)
        assertNull(ownWithoutName.ownSoundName)
        assertFalse(shown().usesOwnSound)
    }

    @Test
    fun `the sound being switched off does not change which sound is in use`() {
        val state =
            shown(
                MiloSettings(
                    soundEnabled = false,
                    customSoundUri = "file:/data/trip_sound/own_trip_start_sound_1",
                ),
            )

        assertFalse(state.soundEnabled)
        assertTrue(state.usesOwnSound)
    }

    @Test
    fun `a copy under way and a problem are passed through as they are`() {
        assertTrue(shown(copyingSound = true).copyingSound)
        assertEquals(
            SettingsProblem.COULD_NOT_SAVE,
            shown(problem = SettingsProblem.COULD_NOT_SAVE).problem,
        )
    }

    @Test
    fun `each reason a picked file is refused has words of its own`() {
        assertEquals(
            SettingsProblem.SOUND_COULD_NOT_COPY,
            OwnSoundRefusal.COULD_NOT_COPY.asProblem(),
        )
        assertEquals(SettingsProblem.SOUND_TOO_LARGE, OwnSoundRefusal.TOO_LARGE.asProblem())
        assertEquals(SettingsProblem.SOUND_NOT_PLAYABLE, OwnSoundRefusal.NOT_PLAYABLE.asProblem())
    }
}
