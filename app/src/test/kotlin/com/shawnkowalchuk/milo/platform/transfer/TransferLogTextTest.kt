package com.shawnkowalchuk.milo.platform.transfer

import com.shawnkowalchuk.milo.data.transfer.ChangedWhileExportingException
import com.shawnkowalchuk.milo.data.transfer.CheckedExport
import com.shawnkowalchuk.milo.data.transfer.ExportContents
import com.shawnkowalchuk.milo.data.transfer.ExportProblem
import com.shawnkowalchuk.milo.data.transfer.PointsLeftOut
import com.shawnkowalchuk.milo.data.transfer.changedSettings
import com.shawnkowalchuk.milo.data.transfer.everyKindOfTrip
import com.shawnkowalchuk.milo.data.transfer.exportFileName
import com.shawnkowalchuk.milo.data.transfer.fileOf
import com.shawnkowalchuk.milo.data.transfer.sentReports
import com.shawnkowalchuk.milo.data.transfer.somePoints
import com.shawnkowalchuk.milo.data.transfer.sourceOf
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The event log's lines about an export and an import, and two small things beside them. */
class TransferLogTextTest {
    private val file = CheckedExport(1, "0.1.0", changedSettings, sentReports, everyKindOfTrip, 4)
    private val asked = ImportOffer(1, 8, 3, 4, true, 2, 1, safetyCopyAtMs = null)

    private fun done(points: PointsTaken, settings: SettingsTaken) =
        ImportDone(8, 3, if (points == PointsTaken.TAKEN) 4 else 0, points, settings, false)

    @Test
    fun `an export's line says how many of each thing the file holds`() {
        assertEquals(
            "All data exported to a file that was picked: 8 trips, 3 sent reports, 4 raw GPS " +
                "points and the settings. Where the file is kept is up to the app it was " +
                "saved with",
            exportedLine(ExportContents(8, 3, 4, settings = true)),
        )
        val bare = exportedLine(ExportContents(0, 0, null, settings = false))
        assertTrue(bare, bare.contains("no raw GPS points (they were left out)"))
        assertTrue(bare, bare.contains("no settings: they could not be read"))
    }

    @Test
    fun `a refused file's line says why, in the words of the check`() {
        val damaged = importRefusedLine(ExportProblem.Damaged("Trip 4: its status is \"OPEN\""))

        assertTrue(damaged, damaged.endsWith("nothing was changed: Trip 4: its status is \"OPEN\""))
        assertTrue(importRefusedLine(ExportProblem.NotAnExport).contains("not an export"))
        assertTrue(importRefusedLine(ExportProblem.NewerVersion(3)).contains("format version 3"))
    }

    @Test
    fun `an import's line says what replaced what, and where the old data is`() {
        val line =
            importedLine(file, done(PointsTaken.TAKEN, SettingsTaken.TAKEN), asked, "copy.json")

        assertEquals(
            "Data imported from an export written by MilO 0.1.0: 8 trips and 3 sent reports " +
                "replaced the 2 trips and 1 sent reports this phone held. 4 raw GPS points " +
                "were stored with them. The file's settings are in force. What the phone held " +
                "before is in the safety copy copy.json, inside MilO",
            line,
        )
    }

    @Test
    fun `an import's line says what could not be done`() {
        val failed = done(PointsTaken.NOT_STORED, SettingsTaken.NOT_STORED)
        val bare = done(PointsTaken.NONE_IN_FILE, SettingsTaken.NONE_IN_FILE)

        val line = importedLine(file, failed, asked, "copy.json")
        assertTrue(line, line.contains("raw GPS points could NOT be stored"))
        assertTrue(line, line.contains("settings could NOT be stored"))
        val quiet = importedLine(file, bare, asked, "copy.json")
        assertTrue(quiet, quiet.contains("holds no raw GPS points"))
        assertTrue(quiet, quiet.contains("holds no settings"))
    }

    @Test
    fun `no line about an export or an import names a person, an address or a position`() {
        val lines =
            listOf(
                exportedLine(ExportContents(8, 3, 4, settings = true)),
                importedLine(file, done(PointsTaken.TAKEN, SettingsTaken.TAKEN), asked, "c.json"),
            )

        for (line in lines) {
            assertFalse(line, line.contains("Sam Driver"))
            assertFalse(line, line.contains("Shop Rd"))
            assertFalse(line, line.contains("53.5"))
            assertFalse(line, line.contains("accounts@"))
        }
    }

    @Test
    fun `an export is offered under a name with the day it is written, in the phone's zone`() {
        val justBeforeMidnightInEdmonton = 1_791_352_740_000L

        assertEquals(
            "MilO-export-2026-10-06.json",
            exportFileName(justBeforeMidnightInEdmonton, ZoneId.of("America/Edmonton")),
        )
        assertEquals(
            "MilO-export-2026-10-07.json",
            exportFileName(justBeforeMidnightInEdmonton, ZoneId.of("UTC")),
        )
    }

    @Test
    fun `an export whose points are not what was counted is stopped, not written short`() {
        assertThrows(ChangedWhileExportingException::class.java) {
            fileOf(sourceOf(pointCount = somePoints.size + 1L))
        }
        assertThrows(ChangedWhileExportingException::class.java) {
            fileOf(sourceOf(pointCount = somePoints.size - 1L))
        }
    }

    @Test
    fun `a point of a trip the file does not hold is not written into it`() {
        // The first fix of a trip that started in between, or one that something left behind.
        val stray = somePoints.first().copy(tripId = 77)

        val text = fileOf(sourceOf(points = somePoints + stray, pointCount = 4))

        assertEquals(fileOf(), text)
    }

    @Test
    fun `the line of the transaction that replaces the trips is one line, and says what follows`() {
        val line = importBegunLine(file, asked, "copy.json", leftOut = null)

        assertEquals(
            "An import has replaced the 2 trips and 1 sent reports this phone held with the " +
                "8 trips and 3 sent reports of an export written by MilO 0.1.0. Its settings " +
                "and its raw GPS points are stored next. If no later line says that the data " +
                "was imported, MilO was ended before that was done, and finishes it at its " +
                "next start. What the phone held before is in the safety copy copy.json, " +
                "inside MilO",
            line,
        )
        val strays = PointsLeftOut(212, (1L..12L).toList())
        val withStrays = importBegunLine(file, asked, "copy.json", strays)
        assertTrue(withStrays, withStrays.contains("Left out of that copy: 212 raw GPS points"))
        assertTrue(
            withStrays,
            withStrays.endsWith("(trip ids: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 and 2 more)"),
        )
        assertFalse(withStrays.contains("\n"))
    }

    @Test
    fun `what a later start writes about an import that was cut off`() {
        val finished =
            finishedLaterLine(file, done(PointsTaken.TAKEN, SettingsTaken.TAKEN), "c.json")

        assertTrue(
            finished,
            finished.startsWith("MilO was ended in the middle of an import, and has"),
        )
        assertTrue(finished, finished.contains("4 raw GPS points were stored with them"))
        assertTrue(finished, finished.endsWith("safety copy c.json, inside MilO"))
        assertTrue(cutOffBeforeReplacingLine().contains("Everything on this phone is as it was"))
        assertTrue(cutOffPointsRemovedLine(5_400).contains("the 5400 raw GPS points of the trips"))
        assertTrue(cutOffLeftLine().contains("left as it is"))
    }
}
