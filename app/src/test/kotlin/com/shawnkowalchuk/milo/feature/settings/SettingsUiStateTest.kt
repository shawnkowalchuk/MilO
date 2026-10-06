package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.DayHours
import com.shawnkowalchuk.milo.data.settings.GRACE_PERIOD_CHOICE
import com.shawnkowalchuk.milo.data.settings.MINIMUM_TRIP_DISTANCE_CHOICE
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.platform.trip.OwnSoundRefusal
import java.time.DayOfWeek
import java.time.LocalTime
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
        problemDay: DayOfWeek? = null,
    ) = settingsUiState(settings, copyingSound, problem, problemDay)

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

    // ---- The work schedule ------------------------------------------------------------------------

    @Test
    fun `a fresh install shows Monday to Friday, 08 00 to 16 30, and saves the rest as Personal`() {
        val state = shown()

        assertEquals(DayOfWeek.entries, state.schedule.map { it.day })
        assertEquals(
            listOf(true, true, true, true, true, false, false),
            state.schedule.map { it.tracked },
        )
        for (day in state.schedule) {
            assertEquals(LocalTime.of(8, 0), day.start)
            assertEquals(LocalTime.of(16, 30), day.end)
        }
        assertFalse(state.ignoreOutsideSchedule)
    }

    @Test
    fun `while every tracked day has the same hours no day offers to copy them`() {
        assertTrue(shown().schedule.none { it.canCopy })
    }

    @Test
    fun `once one tracked day differs, each tracked day offers its hours to the others`() {
        val schedule =
            checkNotNull(DEFAULT_WORK_SCHEDULE.withStart(DayOfWeek.WEDNESDAY, LocalTime.of(6, 30)))

        val state = shown(MiloSettings(schedule = schedule))

        assertEquals(LocalTime.of(6, 30), state.schedule[2].start)
        assertEquals(
            listOf(true, true, true, true, true, false, false),
            state.schedule.map { it.canCopy },
        )
    }

    @Test
    fun `a day that is switched off is shown as off, and keeps its hours for later`() {
        val schedule =
            DEFAULT_WORK_SCHEDULE
                .with(DayOfWeek.FRIDAY, DayHours(false, LocalTime.of(7, 0), LocalTime.of(12, 0)))

        val friday = shown(MiloSettings(schedule = schedule)).schedule[4]

        assertEquals(
            ScheduleDay(
                day = DayOfWeek.FRIDAY,
                tracked = false,
                start = LocalTime.of(7, 0),
                end = LocalTime.of(12, 0),
                canCopy = false,
                hoursRefused = false,
            ),
            friday,
        )
    }

    @Test
    fun `the choice for trips outside the schedule is shown as stored`() {
        assertTrue(shown(MiloSettings(ignoreTripsOutsideSchedule = true)).ignoreOutsideSchedule)
    }

    @Test
    fun `refused hours are said under the day they were picked for, and under no other`() {
        val state =
            shown(
                problem = SettingsProblem.HOURS_END_NOT_AFTER_START,
                problemDay = DayOfWeek.FRIDAY,
            )

        assertEquals(SettingsProblem.HOURS_END_NOT_AFTER_START, state.problem)
        assertEquals(
            listOf(DayOfWeek.FRIDAY),
            state.schedule.filter { it.hoursRefused }.map { it.day },
        )
        // The schedule shown is the stored one: nothing was changed by the refused press.
        assertEquals(shown().schedule, state.schedule.map { it.copy(hoursRefused = false) })
    }

    @Test
    fun `the refusal can stand under any of the seven days`() {
        for (day in DayOfWeek.entries) {
            val settings = MiloSettings(schedule = DEFAULT_WORK_SCHEDULE.withTracked(day, true))

            val state =
                shown(
                    settings,
                    problem = SettingsProblem.HOURS_END_NOT_AFTER_START,
                    problemDay = day,
                )

            assertEquals(listOf(day), state.schedule.filter { it.hoursRefused }.map { it.day })
        }
    }

    @Test
    fun `no day says that its hours were refused when they were not`() {
        assertTrue(shown().schedule.none { it.hoursRefused })
        // A change that could not be stored is said at the top of the screen, whichever day it
        // was about.
        val notSaved =
            shown(problem = SettingsProblem.COULD_NOT_SAVE, problemDay = DayOfWeek.FRIDAY)
        assertTrue(notSaved.schedule.none { it.hoursRefused })
        // And the problems of the sound card are about no day at all.
        val sound = shown(problem = SettingsProblem.SOUND_NOT_PLAYABLE)
        assertTrue(sound.schedule.none { it.hoursRefused })
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
