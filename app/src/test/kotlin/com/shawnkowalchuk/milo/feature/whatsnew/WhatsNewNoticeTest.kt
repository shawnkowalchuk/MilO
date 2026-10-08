package com.shawnkowalchuk.milo.feature.whatsnew

import com.shawnkowalchuk.milo.data.changelog.Change
import com.shawnkowalchuk.milo.data.changelog.ChangeKind
import com.shawnkowalchuk.milo.data.changelog.Changelog
import com.shawnkowalchuk.milo.data.changelog.Release
import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.platform.system.InstalledVersion
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The What's new screen (2026-10-08): when it opens by itself after an update, and how it
 * names the versions.
 */
class WhatsNewNoticeTest {
    @Test
    fun `after an update past the first start it is due once`() {
        val settings =
            MiloSettings(firstRunStage = FirstRunStage.DONE, whatsNewSeenVersion = "0.1.0")

        assertTrue(isDue(settings, installedVersion = "0.2.0"))
        assertFalse(isDue(settings.copy(whatsNewSeenVersion = "0.2.0"), installedVersion = "0.2.0"))
    }

    @Test
    fun `a phone that had MilO before the list existed sees it once`() {
        val settings = MiloSettings(firstRunStage = FirstRunStage.DONE, whatsNewSeenVersion = null)

        assertTrue(isDue(settings, installedVersion = "0.1.0"))
    }

    @Test
    fun `during the first start it is never due`() {
        for (stage in listOf(FirstRunStage.INTRO, FirstRunStage.SETUP)) {
            assertFalse(isDue(MiloSettings(firstRunStage = stage), installedVersion = "0.1.0"))
        }
    }

    @Test
    fun `the screen marks the version on this phone and keeps the list's order`() {
        val change = Change(ChangeKind.NEW, "Trips")
        val changelog =
            Changelog(
                listOf(
                    Release("0.2.0", null, listOf(change)),
                    Release("0.1.0", "2026-10-09", listOf(change)),
                ),
            )

        val state = whatsNewState(changelog, InstalledVersion(name = "0.1.0", code = 1))

        assertEquals(listOf("0.2.0", "0.1.0"), state.releases.map { it.version })
        assertEquals(listOf(false, true), state.releases.map { it.onThisPhone })
        assertEquals(listOf(null, LocalDate.of(2026, 10, 9)), state.releases.map { it.releasedOn })
    }
}
