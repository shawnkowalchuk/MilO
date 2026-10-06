package com.shawnkowalchuk.milo.data.eventlog

import com.shawnkowalchuk.milo.data.report.ReportFileStore
import com.shawnkowalchuk.milo.platform.trip.FakeEventLogDao
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The event log as a text file: its name, its text, and the file itself in a real folder. */
class EventLogFilesTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val edmonton = ZoneId.of("America/Edmonton")
    private val dao = FakeEventLogDao()
    private val log = EventLogRepository(dao)
    private val folder: File get() = File(temporaryFolder.root, "reports")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(edmonton).toInstant().toEpochMilli()

    // ---- The text ---------------------------------------------------------------------------------

    @Test
    fun `the file is named by the day it was written, in the phone's time zone`() {
        assertEquals("MilO-log-2026-10-06.txt", eventLogFileName(at("2026-10-06T23:59"), edmonton))
        // The same moment is already the 7th in UTC.
        val utc = ZoneId.of("UTC")
        assertEquals("MilO-log-2026-10-07.txt", eventLogFileName(at("2026-10-06T23:59"), utc))
    }

    @Test
    fun `the first lines say what the file is, when it was written and how its times are meant`() {
        val header = eventLogFileHeader(at("2026-10-06T14:32:10"), edmonton, lineCount = 1_234)

        assertEquals(
            "MilO event log\n" +
                "Written 2026-10-06 14:32:10. Every time is in America/Edmonton.\n" +
                "1234 lines, oldest first. MilO removes lines older than 90 days, except the " +
                "newest 1000.\n\n",
            header,
        )
    }

    @Test
    fun `each entry is one line, and a detail follows on lines of its own, set in`() {
        val entries =
            listOf(
                EventLogEntry(1, at("2026-10-05T08:14:03"), EventCategory.TRIP, "Trip 12 started"),
                EventLogEntry(
                    id = 2,
                    atMs = at("2026-10-05T08:14:04"),
                    category = EventCategory.TRIGGER,
                    message = "Bluetooth receiver: ACL connected for the truck",
                    detail = "before: idle\nafter: recording",
                ),
            )

        assertEquals(
            "2026-10-05 08:14:03  TRIP  Trip 12 started\n" +
                "2026-10-05 08:14:04  TRIGGER  Bluetooth receiver: ACL connected for the truck\n" +
                "    before: idle\n" +
                "    after: recording\n",
            eventLogFileLines(entries, edmonton),
        )
    }

    @Test
    fun `no entries are no text`() {
        assertEquals("", eventLogFileLines(emptyList(), edmonton))
    }

    // ---- The file ---------------------------------------------------------------------------------

    @Test
    fun `the whole log is written, oldest first, into the folder that can be handed out`() =
        runBlocking {
            // Written newest first, and more lines than are read from the table at a time.
            for (i in 1_100 downTo 1) {
                log.add(at("2026-10-01T00:00") + i * 1_000L, EventCategory.TRIP, "line $i")
            }

            val file = EventLogFiles(
                log,
                ReportFileStore(folder),
            ).write(at("2026-10-06T14:32"), edmonton)

            assertEquals(File(folder, "MilO-log-2026-10-06.txt"), file)
            val lines = file.readLines()
            assertEquals("MilO event log", lines[0])
            assertTrue(lines[2].startsWith("1100 lines, oldest first."))
            assertEquals("", lines[3])
            val body = lines.drop(4)
            assertEquals(1_100, body.size)
            assertEquals("2026-10-01 00:00:01  TRIP  line 1", body.first())
            assertEquals("2026-10-01 00:18:20  TRIP  line 1100", body.last())
            // Complete or not there: nothing half-written is left beside it.
            assertEquals(listOf("MilO-log-2026-10-06.txt"), folder.list()?.toList())
        }

    @Test
    fun `a second file on the same day takes the place of the first`() = runBlocking {
        val files = EventLogFiles(log, ReportFileStore(folder))
        log.add(at("2026-10-06T08:00"), EventCategory.TRIP, "the first line")
        val first = files.write(at("2026-10-06T09:00"), edmonton)
        assertFalse(first.readText().contains("the second line"))

        log.add(at("2026-10-06T10:00"), EventCategory.TRIP, "the second line")
        val second = files.write(at("2026-10-06T11:00"), edmonton)

        assertEquals(first, second)
        assertTrue(second.readText().contains("the second line"))
        assertEquals(1, folder.list()?.size)
    }

    @Test
    fun `text that is not plain ASCII is written as UTF-8`() = runBlocking {
        log.add(at("2026-10-06T08:00"), EventCategory.TRIP, "from \"Église, Montréal\" → none")

        val file = EventLogFiles(
            log,
            ReportFileStore(folder),
        ).write(at("2026-10-06T09:00"), edmonton)

        assertTrue(file.readText(Charsets.UTF_8).contains("from \"Église, Montréal\" → none"))
    }
}
