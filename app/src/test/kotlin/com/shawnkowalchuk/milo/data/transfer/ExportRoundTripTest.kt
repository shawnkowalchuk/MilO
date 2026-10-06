package com.shawnkowalchuk.milo.data.transfer

import com.shawnkowalchuk.milo.core.schedule.DEFAULT_WORK_SCHEDULE
import com.shawnkowalchuk.milo.core.schedule.TripCategory
import com.shawnkowalchuk.milo.core.trip.TripStartCause
import com.shawnkowalchuk.milo.core.trip.TripStatus
import com.shawnkowalchuk.milo.data.MILO_DATABASE_VERSION
import com.shawnkowalchuk.milo.data.point.RawPoint
import com.shawnkowalchuk.milo.data.report.SentReportKind
import com.shawnkowalchuk.milo.data.settings.MiloSettings
import com.shawnkowalchuk.milo.data.settings.transferred
import com.shawnkowalchuk.milo.data.trip.Trip
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An export that is read back holds exactly what was written: the one property the export is
 * for. Each kind of trip MilO can hold is written and read, with the sent reports, the settings
 * and the raw points.
 */
class ExportRoundTripTest {
    private fun good(text: String, points: MutableList<RawPoint>? = null): CheckedExport {
        val reading = read(text, points)
        assertTrue("The file was refused: $reading", reading is ExportReading.Good)
        return (reading as ExportReading.Good).export
    }

    private fun roundTrip(trip: Trip): Trip = good(fileOf(sourceOf(listOf(trip), points = null)))
        .trips
        .single()

    @Test
    fun `a trip MilO recorded comes back as it was stored`() {
        assertEquals(recordedTrip, roundTrip(recordedTrip))
    }

    @Test
    fun `a trip added by hand comes back with its marks and without a position`() {
        val back = roundTrip(tripAddedByHand)

        assertEquals(tripAddedByHand, back)
        assertTrue(back.addedByHand && back.startAddressByHand && back.endAddressByHand)
        assertNull(back.startLatitude)
    }

    @Test
    fun `an edited trip comes back with what MilO recorded of it, so it can still be restored`() {
        val back = roundTrip(editedTrip)

        assertEquals(editedTrip, back)
        assertEquals(editedTrip.recordedDistanceMetres, back.recordedDistanceMetres)
        assertEquals(editedTrip.recordedStartedAtMs, back.recordedStartedAtMs)
        assertEquals("Client's site\nGate 4", back.endAddress)
    }

    @Test
    fun `a deleted trip comes back deleted, so it can still be put back on the Trips screen`() {
        assertEquals(TripStatus.DELETED, roundTrip(deletedTrip).status)
        assertEquals(deletedTrip, roundTrip(deletedTrip))
    }

    @Test
    fun `a discarded trip comes back discarded, and one that was ignored says so`() {
        assertEquals(discardedTrip, roundTrip(discardedTrip))
        assertEquals(ignoredTrip, roundTrip(ignoredTrip))
        assertTrue(roundTrip(ignoredTrip).ignoredOutsideSchedule)
    }

    @Test
    fun `a trip sorted by hand stays marked as sorted by hand`() {
        val back = roundTrip(tripSortedByHand)

        assertEquals(tripSortedByHand, back)
        assertEquals(TripCategory.PERSONAL, back.category)
        assertTrue(back.categorySetByHand)
    }

    @Test
    fun `a trip that was never sorted, and one that ends before it starts, are taken as stored`() {
        assertEquals(oddTrip, roundTrip(oddTrip))
        assertNull(roundTrip(oddTrip).category)
    }

    @Test
    fun `a finished trip without an end, which storage should never hold, is still carried`() {
        val endless = recordedTrip.copy(endedAtMs = null)

        assertEquals(endless, roundTrip(endless))
    }

    @Test
    fun `every kind together, with the sent reports, the settings and the raw points`() {
        val points = mutableListOf<RawPoint>()

        val back = good(fileOf(), points)

        assertEquals(everyKindOfTrip, back.trips)
        assertEquals(sentReports, back.sentReports)
        assertEquals(changedSettings, back.settings)
        assertEquals(somePoints.withoutIds(), points)
        assertEquals(somePoints.size.toLong(), back.pointCount)
        assertEquals(EXPORTED_AT_MS, back.exportedAtMs)
        assertEquals("0.1.0", back.appVersion)
    }

    @Test
    fun `the sent reports keep their ids, their days and the gaps in their revisions`() {
        val back = good(fileOf()).sentReports

        assertEquals(listOf(1L, 4L, 5L), back.map { it.id })
        assertEquals(listOf(0, 2, 0), back.map { it.revision })
        assertEquals(SentReportKind.RANGE, back.last().kind)
        assertEquals(sentReports, back)
    }

    @Test
    fun `settings as they are out of the box come back as they are out of the box`() {
        val untouched = MiloSettings().transferred()

        val back = good(fileOf(sourceOf(settings = untouched))).settings

        assertEquals(untouched, back)
        assertEquals(DEFAULT_WORK_SCHEDULE, back?.schedule)
        assertNull(back?.truck)
        assertNull(back?.accountantEmail)
    }

    @Test
    fun `a truck without a name, and a file without settings`() {
        val nameless = changedSettings.copy(truck = changedSettings.truck?.copy(name = null))

        assertEquals(nameless, good(fileOf(sourceOf(settings = nameless))).settings)
        assertNull(good(fileOf(sourceOf(settings = null))).settings)
    }

    @Test
    fun `an export without the raw points says so, and one with none of them is not the same`() {
        val leftOut = good(fileOf(sourceOf(points = null)))
        val none = good(fileOf(sourceOf(points = emptyList())))

        assertNull(leftOut.pointCount)
        assertEquals(0L, none.pointCount)
        assertEquals(everyKindOfTrip, leftOut.trips)
    }

    @Test
    fun `an export of a phone that holds nothing yet can be read`() {
        val empty = good(fileOf(sourceOf(emptyList(), emptyList(), points = emptyList())))

        assertEquals(emptyList<Trip>(), empty.trips)
        assertEquals(0L, empty.pointCount)
    }

    @Test
    fun `a position and a distance come back to the last digit`() {
        val exact =
            recordedTrip.copy(
                distanceMetres = 0.1 + 0.2,
                startLatitude = 53.123456789012345,
                startLongitude = -113.98765432109876,
            )

        assertEquals(exact, roundTrip(exact))
    }

    @Test
    fun `the file is one JSON document, with what it holds said near its top`() {
        val text = fileOf()

        val document = Json.parseToJsonElement(text).jsonObject
        assertEquals("milo-export", document.getValue("format").jsonPrimitive.content)
        assertEquals("1", document.getValue("formatVersion").jsonPrimitive.content)
        val exportedAt = document.getValue("exportedAt").jsonPrimitive.content
        assertEquals("2026-10-06T14:02:11-06:00", exportedAt)
        val contents = document.getValue("contents").jsonObject
        assertEquals("8", contents.getValue("trips").jsonPrimitive.content)
        assertEquals("3", contents.getValue("sentReports").jsonPrimitive.content)
        assertEquals("4", contents.getValue("points").jsonPrimitive.content)
        assertEquals(8, document.getValue("trips").jsonArray.size)
        assertEquals(4, document.getValue("points").jsonArray.size)
        // What it is, in its first two lines, for whoever opens it in an editor.
        assertTrue(text.startsWith("{\n\"format\": \"milo-export\",\n\"formatVersion\": 1,\n"))
    }

    @Test
    fun `a file that another program saved again, in another order and layout, reads the same`() {
        val document = Json.parseToJsonElement(fileOf()).jsonObject
        val reordered = document.entries.sortedBy { it.key }.joinToString(",\n  ", "{\n  ", "\n}") {
            "\"${it.key}\" :\t${it.value}"
        }
        val points = mutableListOf<RawPoint>()

        val back = good("﻿$reordered\n\n", points)

        assertEquals(everyKindOfTrip, back.trips)
        assertEquals(sentReports, back.sentReports)
        assertEquals(changedSettings, back.settings)
        assertEquals(somePoints.withoutIds(), points)
    }

    @Test
    fun `an accuracy or a speed that is not a number is written as none`() {
        val odd = RawPoint(1, 1, 5, 6, 53.5, -113.5, Float.NaN, Float.POSITIVE_INFINITY)
        val points = mutableListOf<RawPoint>()

        good(fileOf(sourceOf(points = listOf(odd))), points)

        val asRead = odd.copy(id = 0, accuracyMetres = null, speedMetresPerSecond = null)
        assertEquals(listOf(asRead), points)
    }

    @Test
    fun `a position that is not a number stops the export, and is never left out in silence`() {
        val broken = RawPoint(1, 1, 5, 6, Double.NaN, -113.5, 4f, 1f)

        assertThrows(IllegalStateException::class.java) {
            fileOf(sourceOf(points = listOf(broken)))
        }
    }

    @Test
    fun `a trip that is still being recorded cannot be written into an export`() {
        val open = recordedTrip.copy(status = TripStatus.OPEN, endedAtMs = null)

        assertThrows(IllegalArgumentException::class.java) { open.toExported() }
    }

    @Test
    fun `every stored constant has a word in the file, and every word means its constant`() {
        assertEquals(TripStatus.entries.toSet() - TripStatus.OPEN, STATUS_WORDS.keys)
        assertEquals(TripStartCause.entries.toSet(), START_CAUSE_WORDS.keys)
        assertEquals(TripCategory.entries.toSet(), CATEGORY_WORDS.keys)
        assertEquals(SentReportKind.entries.toSet(), REPORT_KIND_WORDS.keys)
        // Pinned: a file written today must be read by every later version.
        assertEquals(setOf("FINISHED", "DISCARDED", "DELETED"), STATUS_WORDS.values.toSet())
        assertEquals(setOf("TRUCK", "MANUAL"), START_CAUSE_WORDS.values.toSet())
        assertEquals(setOf("BUSINESS", "PERSONAL"), CATEGORY_WORDS.values.toSet())
        assertEquals(setOf("MONTH", "RANGE"), REPORT_KIND_WORDS.values.toSet())
        assertEquals("monday", DAY_WORDS.values.first())
    }

    /**
     * The export against the tables themselves, as Room exports them to `app/schemas` (the
     * tests run with the `app` folder as their working directory). Whoever adds a column to
     * `trips`, `sent_reports` or `raw_points` meets this test: the column has to be carried by
     * the export, under a new format version, or it is lost by every export and import.
     */
    @Test
    fun `the file carries every column of the three tables that means something in it`() {
        fun columns(database: String, version: Int, table: String): Set<String> {
            val file = File("schemas/com.shawnkowalchuk.milo.data.$database/$version.json")
            val entities =
                Json
                    .parseToJsonElement(file.readText())
                    .jsonObject
                    .getValue("database")
                    .jsonObject
                    .getValue("entities")
                    .jsonArray
            val entity =
                entities.single {
                    it.jsonObject.getValue("tableName").jsonPrimitive.content == table
                }
            return entity.jsonObject.getValue("fields").jsonArray
                .map { it.jsonObject.getValue("columnName").jsonPrimitive.content }
                .toSet()
        }
        val document = Json.parseToJsonElement(fileOf()).jsonObject
        val trip = document.getValue("trips").jsonArray.first().jsonObject.keys
        val report = document.getValue("sentReports").jsonArray.first().jsonObject.keys

        // Empty on every closed trip: the grace period of a trip that is being recorded.
        val graceColumns = setOf("graceStartedAtMs", "graceDeadlineMs")
        assertEquals(columns("MiloDatabase", MILO_DATABASE_VERSION, "trips") - graceColumns, trip)
        assertEquals(columns("MiloDatabase", MILO_DATABASE_VERSION, "sent_reports"), report)
        // A point's id only says in which order the fixes arrived, which the rows' order does.
        val pointColumns = columns("PointsDatabase", 1, "raw_points") - "id"
        assertEquals(pointColumns, EXPORT_POINT_COLUMNS.toSet())
    }
}
