package com.shawnkowalchuk.milo.feature.settings

import com.shawnkowalchuk.milo.data.settings.LastExport
import com.shawnkowalchuk.milo.data.transfer.ExportContents
import com.shawnkowalchuk.milo.platform.transfer.CutOffImport
import com.shawnkowalchuk.milo.platform.transfer.ImportDone
import com.shawnkowalchuk.milo.platform.transfer.ImportOffer
import com.shawnkowalchuk.milo.platform.transfer.PointsTaken
import com.shawnkowalchuk.milo.platform.transfer.SettingsTaken
import com.shawnkowalchuk.milo.platform.transfer.TransferOutcome
import com.shawnkowalchuk.milo.platform.transfer.TransferRefusal
import com.shawnkowalchuk.milo.platform.transfer.TransferStatus
import com.shawnkowalchuk.milo.platform.transfer.TransferWork
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the card for backup, export and import shows, and when its buttons can be pressed. */
class DataUiStateTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val offer = ImportOffer(1, 8, 3, 4, true, 2, 1, safetyCopyAtMs = null)

    private fun card(
        status: TransferStatus = TransferStatus(),
        tripInProgress: Boolean = false,
        noFilePicker: Boolean = false,
    ) = dataCardState(
        lastExport = LastExport(5, withPoints = true),
        status = status,
        tripInProgress = tripInProgress,
        includePoints = true,
        safetyCopiesAtMs = listOf(9, 7),
        noFilePicker = noFilePicker,
        zone = zone,
    )

    private fun imported(
        pointsTaken: PointsTaken = PointsTaken.TAKEN,
        settings: SettingsTaken = SettingsTaken.TAKEN,
        truckNeedsPairing: Boolean = false,
    ) = TransferOutcome.Imported(ImportDone(8, 3, 4, pointsTaken, settings, truckNeedsPairing))

    @Test
    fun `with nothing under way the buttons can be pressed, and the card shows what is stored`() {
        val state = card()

        assertTrue(state.canStart)
        assertEquals(LastExport(5, withPoints = true), state.lastExport)
        assertEquals(listOf(9L, 7L), state.safetyCopiesAtMs)
        assertTrue(state.includePoints)
        assertTrue(state.lines.isEmpty())
    }

    @Test
    fun `the buttons wait while something is being done, asked, or recorded`() {
        for (work in TransferWork.entries) {
            assertFalse(card(TransferStatus(working = work)).canStart)
        }
        assertFalse(card(TransferStatus(offer = offer)).canStart)
        assertFalse(card(tripInProgress = true).canStart)
        assertEquals(offer, card(TransferStatus(offer = offer)).offer)
    }

    @Test
    fun `an export says what it wrote, and that the settings are missing if they are`() {
        val whole = TransferOutcome.Exported(ExportContents(8, 3, 4, settings = true))
        val without = TransferOutcome.Exported(ExportContents(8, 3, null, settings = false))

        assertEquals(listOf(OutcomeLine.Exported(8, 3, 4)), outcomeLines(whole))
        assertEquals(
            listOf(OutcomeLine.Exported(8, 3, null), OutcomeLine.ExportedWithoutSettings),
            outcomeLines(without),
        )
        assertEquals(OutcomeTone.PROBLEM, OutcomeLine.ExportedWithoutSettings.tone)
    }

    @Test
    fun `an export that passed over points says how many, as a problem`() {
        val contents = ExportContents(8, 3, 4, settings = true)

        val lines = outcomeLines(TransferOutcome.Exported(contents, pointsLeftOut = 212))

        assertEquals(listOf(OutcomeLine.Exported(8, 3, 4), OutcomeLine.PointsLeftOut(212)), lines)
        assertEquals(OutcomeTone.PROBLEM, lines.last().tone)
    }

    @Test
    fun `an import that a later start finished says that first, and then what it did`() {
        val done = ImportDone(8, 3, 4, PointsTaken.TAKEN, SettingsTaken.TAKEN, false, true)

        val lines = outcomeLines(TransferOutcome.Imported(done))

        assertEquals(
            listOf(
                OutcomeLine.FinishedAfterCutOff,
                OutcomeLine.Imported(8, 3, 4, PointsTaken.TAKEN),
                OutcomeLine.SafetyCopyKept,
            ),
            lines,
        )
        assertTrue(lines.all { it.tone == OutcomeTone.DONE })
    }

    @Test
    fun `an import that was cut off and not finished is one sentence, as a problem`() {
        for (what in CutOffImport.entries) {
            val lines = outcomeLines(TransferOutcome.CutOff(what))

            assertEquals(listOf(OutcomeLine.CutOff(what)), lines)
            assertEquals(OutcomeTone.PROBLEM, lines.single().tone)
        }
    }

    @Test
    fun `an import that did everything says so, and that the old data is kept`() {
        val lines = outcomeLines(imported())

        assertEquals(
            listOf(OutcomeLine.Imported(8, 3, 4, PointsTaken.TAKEN), OutcomeLine.SafetyCopyKept),
            lines,
        )
        assertTrue(lines.all { it.tone == OutcomeTone.DONE })
    }

    @Test
    fun `a truck that has to be paired again is something left to do, not a failure`() {
        val lines = outcomeLines(imported(truckNeedsPairing = true))

        assertTrue(OutcomeLine.PairTruckAgain in lines)
        assertEquals(OutcomeTone.TO_DO, OutcomeLine.PairTruckAgain.tone)
    }

    @Test
    fun `points or settings that could not be stored are each said as a problem`() {
        val lines = outcomeLines(imported(PointsTaken.NOT_STORED, SettingsTaken.NOT_STORED))

        assertEquals(OutcomeTone.PROBLEM, lines.first().tone)
        assertTrue(OutcomeLine.SettingsNotStored in lines)
        assertEquals(OutcomeTone.PROBLEM, OutcomeLine.SettingsNotStored.tone)
        // The old data is kept all the same, and the card says so last.
        assertEquals(OutcomeLine.SafetyCopyKept, lines.last())
    }

    @Test
    fun `a file without points or settings is said without alarm`() {
        val lines = outcomeLines(imported(PointsTaken.NONE_IN_FILE, SettingsTaken.NONE_IN_FILE))

        assertTrue(OutcomeLine.SettingsKept in lines)
        assertTrue(lines.all { it.tone == OutcomeTone.DONE })
    }

    @Test
    fun `every refusal is one sentence, as a problem`() {
        val refusals =
            listOf(
                TransferRefusal.TripInProgress,
                TransferRefusal.ExportNotWritten,
                TransferRefusal.ExportLeftUnfinished,
                TransferRefusal.FileNotRead,
                TransferRefusal.FileTooLarge,
                TransferRefusal.NotAnExport,
                TransferRefusal.NewerVersion(3),
                TransferRefusal.Damaged,
                TransferRefusal.NoSafetyCopy,
                TransferRefusal.SafetyCopyNotWritten,
                TransferRefusal.NothingReplaced,
            )

        for (why in refusals) {
            val lines = outcomeLines(TransferOutcome.Refused(why))
            assertEquals(listOf(OutcomeLine.Refused(why)), lines)
            assertEquals(OutcomeTone.PROBLEM, lines.single().tone)
        }
    }

    @Test
    fun `a phone without a file picker is said in place of the last outcome`() {
        val exported = TransferOutcome.Exported(ExportContents(8, 3, 4, settings = true))

        val state = card(TransferStatus(outcome = exported), noFilePicker = true)

        assertEquals(listOf(OutcomeLine.NoFilePicker), state.lines)
        assertTrue(state.canStart)
    }
}
