package com.shawnkowalchuk.milo.data.changelog

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The list of changes built into the app, as the file in the repository holds it. */
private val BUNDLED = File("src/main/assets/$CHANGELOG_ASSET")

private const val TWO_RELEASES = """
{
  "releases": [
    {
      "version": "0.2.0",
      "date": null,
      "changes": [ { "kind": "fixed", "title": "The odometer" } ]
    },
    {
      "version": "0.1.0",
      "date": "2026-10-09",
      "changes": [
        { "kind": "new", "title": "Trips", "body": "They start by themselves." },
        { "kind": "security", "title": "Newer libraries" }
      ]
    }
  ]
}
"""

/** The list of versions and their changes (2026-10-08): what is read, and what is refused. */
class ChangelogTest {
    @Test
    fun `a list is read in its order, with its dates, kinds and bodies`() {
        val changelog = parseChangelog(TWO_RELEASES)

        assertEquals(listOf("0.2.0", "0.1.0"), changelog.releases.map { it.version })
        assertNull(changelog.releases[0].releasedOn)
        assertEquals(LocalDate.of(2026, 10, 9), changelog.releases[1].releasedOn)
        assertEquals(
            listOf(ChangeKind.NEW, ChangeKind.SECURITY),
            changelog.releases[1].changes.map { it.kind },
        )
        assertEquals("They start by themselves.", changelog.releases[1].changes[0].body)
        assertNull(changelog.releases[1].changes[1].body)
    }

    @Test
    fun `a key the list does not know is a mistake and is refused`() {
        assertRefused(TWO_RELEASES.replace("\"body\"", "\"bodyy\""))
    }

    @Test
    fun `a kind the list does not know is refused`() {
        assertRefused(TWO_RELEASES.replace("\"fixed\"", "\"repaired\""))
    }

    @Test
    fun `a date that is not a day is refused`() {
        assertRefused(TWO_RELEASES.replace("2026-10-09", "9 October 2026"))
    }

    @Test
    fun `a version without the key for its date is refused`() {
        assertRefused(TWO_RELEASES.replace("\"date\": null,", ""))
    }

    @Test
    fun `text that is not JSON is refused`() {
        assertRefused("Version 0.1.0: trips")
    }

    @Test
    fun `the list built into the app can be read, and names each version once`() {
        val changelog = parseChangelog(BUNDLED.readText())

        assertTrue(changelog.releases.isNotEmpty())
        val versions = changelog.releases.map { it.version }
        assertEquals(versions.distinct(), versions)
        assertTrue(changelog.releases.all { it.changes.isNotEmpty() })
    }

    @Test
    fun `the source reads the file it opens`() = runTest {
        val source =
            ChangelogSource(
                open = { ByteArrayInputStream(TWO_RELEASES.toByteArray()) },
                io = StandardTestDispatcher(testScheduler),
            )

        assertEquals(2, source.read().releases.size)
    }

    @Test
    fun `a file that cannot be opened is said to be unreadable`() = runTest {
        val source =
            ChangelogSource(
                open = { throw IOException("no such asset") },
                io = StandardTestDispatcher(testScheduler),
            )

        try {
            source.read()
            fail("A missing file was read")
        } catch (expected: ChangelogUnreadableException) {
            // The message, not the cause: the coroutines library may hand on a copy whose cause
            // is the exception that was thrown.
            assertEquals("The list of changes could not be opened", expected.message)
        }
    }

    private fun assertRefused(text: String) {
        try {
            parseChangelog(text)
            fail("This was read as a list of changes: $text")
        } catch (expected: ChangelogUnreadableException) {
            // What the screen says instead of a list.
        }
    }
}
