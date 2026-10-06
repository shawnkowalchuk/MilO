package com.shawnkowalchuk.milo.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The files an import must refuse: cut short, damaged, not an export at all, or written by a
 * newer MilO. Each starts from a file that is read without complaint.
 */
class ExportReaderTest {
    private val whole = fileOf()

    private fun problemOf(text: String): ExportProblem {
        val reading = read(text)
        assertTrue("The file was taken: $text", reading is ExportReading.Refused)
        return (reading as ExportReading.Refused).problem
    }

    private fun damage(text: String): String {
        val problem = problemOf(text)
        assertTrue("Not called damaged, but $problem", problem is ExportProblem.Damaged)
        return (problem as ExportProblem.Damaged).why
    }

    /** The whole file with one piece of its text replaced, which must be there exactly once. */
    private fun with(old: String, new: String): String {
        assertEquals("\"$old\" is not in the file exactly once", 2, whole.split(old).size)
        return whole.replace(old, new)
    }

    @Test
    fun `the file these tests start from is taken`() {
        assertTrue(read(whole) is ExportReading.Good)
    }

    @Test
    fun `a file cut short anywhere is refused, wherever the cut falls`() {
        // Every length from nothing to one character short of the whole file.
        for (length in 0 until whole.trimEnd().length) {
            val reading = read(whole.substring(0, length))
            assertTrue("Taken when cut after $length characters", reading is ExportReading.Refused)
        }
    }

    @Test
    fun `a file cut short after it has said what it is, is called damaged and not unknown`() {
        val cut = whole.substring(0, whole.indexOf("\"trips\": [") + 40)

        assertTrue(damage(cut).contains("not complete JSON"))
    }

    @Test
    fun `a file from a newer MilO is refused as that, whatever else it holds`() {
        assertEquals(
            ExportProblem.NewerVersion(2),
            problemOf(with("\"formatVersion\": 1,", "\"formatVersion\": 2,")),
        )
        // Also when its parts are ones this version cannot read at all.
        val newer =
            "{\"format\":\"milo-export\",\"formatVersion\":7,\"journeys\":[{\"a\":1}],\"x\":null}"
        assertEquals(ExportProblem.NewerVersion(7), problemOf(newer))
        // And when the version stands at the end, behind everything it could not read.
        val atTheEnd = "{\"new\":[{\"a\":[1,2]}],\"formatVersion\":3,\"format\":\"milo-export\"}"
        assertEquals(ExportProblem.NewerVersion(3), problemOf(atTheEnd))
    }

    @Test
    fun `a format version that is no version`() {
        for (version in listOf("0", "-1", "1.5", "\"1\"", "null", "true", "99999999999")) {
            damage(with("\"formatVersion\": 1,", "\"formatVersion\": $version,"))
        }
        damage("{\"format\":\"milo-export\"}")
    }

    @Test
    fun `what is no export of MilO's at all`() {
        val others =
            listOf(
                "",
                "   \n",
                "Mileage report for September",
                "%PDF-1.4 \u0000\u0001\u0002",
                "[1,2,3]",
                "{}",
                "{\"trips\":[]}",
                "{\"format\":\"other-app\",\"formatVersion\":1}",
                "{\"format\":12,\"formatVersion\":1}",
                "{\"format\": milo-export, \"formatVersion\": 1}",
                "\"milo-export\"",
                "{\"a\":\"" + "x".repeat(2_000_000) + "\"}",
            )

        for (text in others) assertEquals(text.take(40), ExportProblem.NotAnExport, problemOf(text))
    }

    @Test
    fun `a part that is missing`() {
        val keys = listOf("exportedAtMs", "appVersion", "contents", "settings", "pointColumns")

        for (key in keys) {
            val line = whole.lines().single { it.startsWith("\"$key\": ") }
            assertTrue(damage(whole.replace(line + "\n", "")).contains("no \"$key\""))
        }
    }

    @Test
    fun `a part this MilO does not know, and a part that stands twice`() {
        assertTrue(damage(with("\"about\": ", "\"extra\": 1,\n\"about\": ")).contains("extra"))
        val twice = with("\"about\": ", "\"appVersion\": \"0\",\n\"about\": ")
        assertTrue(damage(twice).contains("twice"))
    }

    @Test
    fun `a trip with a part missing, a part too many, or a value of the wrong kind`() {
        damage(with("{\"id\":1,\"startedAtMs\"", "{\"startedAtMs\""))
        damage(with("{\"id\":1,\"startedAtMs\"", "{\"id\":1,\"colour\":\"red\",\"startedAtMs\""))
        damage(with("{\"id\":1,\"startedAtMs\"", "{\"id\":\"one\",\"startedAtMs\""))
        damage(with("{\"id\":1,\"startedAtMs\"", "{\"id\":1.5,\"startedAtMs\""))
        damage(with("{\"id\":1,\"startedAtMs\"", "{\"id\":null,\"startedAtMs\""))
    }

    @Test
    fun `a trip that is still being recorded is refused, and named by its id`() {
        // The one deleted trip of the file, with the status of a trip in progress.
        val open = damage(with("\"status\":\"DELETED\"", "\"status\":\"OPEN\""))

        assertTrue(open, open.startsWith("Trip 4: "))
        assertTrue(open, open.contains("only a closed trip"))
    }

    @Test
    fun `two trips or two sent reports with one id`() {
        val twoTrips = with("{\"id\":2,\"startedAtMs\"", "{\"id\":1,\"startedAtMs\"")
        assertTrue(damage(twoTrips).contains("share"))
        assertTrue(damage(with("{\"id\":4,\"kind\"", "{\"id\":1,\"kind\"")).contains("share"))
    }

    @Test
    fun `a file that holds less, or more, than it says`() {
        val oneTripLess = whole.lines().filterNot { it.startsWith("{\"id\":7,") }.joinToString("\n")
        val lastRow = somePoints.last().toExportRow()
        val onePointLess = whole.replace(lastRow, "").replace("],\n\n]", "]\n]")

        assertTrue(damage(oneTripLess).contains("not all there"))
        damage(onePointLess)
        damage(with("\"points\":4,", "\"points\":5,"))
        damage(with("\"points\":4,", "\"points\":null,"))
        damage(with("\"settings\":true}", "\"settings\":false}"))
    }

    @Test
    fun `a point of a trip the file does not hold`() {
        val last = somePoints.last()
        val orphan = with(last.toExportRow(), last.copy(tripId = 8).toExportRow())

        assertTrue(damage(orphan).contains("trip 8"))
    }

    @Test
    fun `a point that cannot be is named by its place in the file`() {
        val bad = with(somePoints[2].toExportRow(), "[3,1,2,99.0,-113.3,null,null]")

        assertTrue(damage(bad).startsWith("Point number 3: "))
    }

    @Test
    fun `points with other columns than MilO writes`() {
        damage(with("\"speedMetresPerSecond\"]", "\"bearing\"]"))
    }

    @Test
    fun `something after the end of the document`() {
        damage(whole + "{}")
        damage(whole + "x")
        assertTrue(read(whole + " \n\t ") is ExportReading.Good)
    }

    @Test
    fun `a list that is not a list`() {
        damage(with("\"trips\": [", "\"trips\": null,\n\"spare\": ["))
        damage("{\"format\":\"milo-export\",\"formatVersion\":1,\"trips\":{}}")
    }
}
