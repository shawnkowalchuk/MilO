package com.shawnkowalchuk.milo.data.sound

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * MilO's own copy of the chosen trip-start sound, against a real folder: a new copy never
 * touches the one in use, a failed copy leaves nothing behind, and old copies do not pile up.
 */
class OwnSoundStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val folder: File by lazy { File(temporaryFolder.root, "trip_sound") }
    private val store by lazy { OwnSoundStore(folder) }

    private fun audio(text: String): InputStream = ByteArrayInputStream(text.toByteArray())

    /** Every copy in the folder, read from the folder itself, in the order of their names. */
    private fun copies(): List<File> = folder.listFiles().orEmpty().sortedBy { it.name }

    @Test
    fun `a copy holds exactly what was read`() {
        val copy = store.writeCopy(audio("beep boop"), stamp = 1_000)

        assertArrayEquals("beep boop".toByteArray(), copy.readBytes())
        assertEquals(folder, copy.parentFile)
    }

    @Test
    fun `the folder is created when the first copy is written`() {
        assertFalse(folder.exists())

        store.writeCopy(audio("beep"), stamp = 1_000)

        assertTrue(folder.isDirectory)
    }

    @Test
    fun `a new copy is written beside the one in use, not over it`() {
        val inUse = store.writeCopy(audio("the old sound"), stamp = 1_000)

        val next = store.writeCopy(audio("the new sound"), stamp = 2_000)

        assertNotEquals(inUse, next)
        assertEquals("the old sound", inUse.readText())
        assertEquals("the new sound", next.readText())
    }

    @Test
    fun `two copies made at the same moment still get names of their own`() {
        // The stamp is the time, and a clock can stand still or be set back.
        val first = store.writeCopy(audio("one"), stamp = 1_000)
        val second = store.writeCopy(audio("two"), stamp = 1_000)

        assertNotEquals(first, second)
        assertEquals("one", first.readText())
    }

    @Test
    fun `keeping only the copy in use removes every other one`() {
        val old = store.writeCopy(audio("old"), stamp = 1_000)
        val older = store.writeCopy(audio("older"), stamp = 500)
        val inUse = store.writeCopy(audio("new"), stamp = 2_000)

        val removed = store.keepOnly(inUse)

        assertEquals(2, removed)
        assertEquals(listOf(inUse), copies())
        assertFalse(old.exists())
        assertFalse(older.exists())
        assertEquals("new", inUse.readText())
    }

    @Test
    fun `keeping none removes them all`() {
        store.writeCopy(audio("one"), stamp = 1_000)
        store.writeCopy(audio("two"), stamp = 2_000)

        assertEquals(2, store.keepOnly(null))

        assertEquals(emptyList<File>(), copies())
    }

    @Test
    fun `there is nothing to remove before the first copy`() {
        assertEquals(0, store.keepOnly(null))
        assertEquals(emptyList<File>(), copies())
    }

    @Test
    fun `a file over the limit is refused, and nothing of it is kept`() {
        val refused =
            assertThrows(SoundTooLargeException::class.java) {
                store.writeCopy(audio("0123456789"), stamp = 1_000, maxBytes = 9)
            }

        assertEquals(9, refused.limitBytes)
        assertEquals(emptyList<File>(), copies())
    }

    @Test
    fun `a file exactly at the limit is accepted`() {
        val copy = store.writeCopy(audio("0123456789"), stamp = 1_000, maxBytes = 10)

        assertEquals(10, copy.length())
    }

    @Test
    fun `an empty file is refused`() {
        assertThrows(IOException::class.java) { store.writeCopy(audio(""), stamp = 1_000) }

        assertEquals(emptyList<File>(), copies())
    }

    @Test
    fun `a file that breaks off half-way leaves nothing behind, and the old copy stays`() {
        val inUse = store.writeCopy(audio("the old sound"), stamp = 1_000)
        val breaksOff =
            object : InputStream() {
                private var served = 0

                override fun read(): Int =
                    if (served++ < 4) 'x'.code else throw IOException("the file went away")
            }

        assertThrows(IOException::class.java) { store.writeCopy(breaksOff, stamp = 2_000) }

        assertEquals(listOf(inUse), copies())
        assertEquals("the old sound", inUse.readText())
    }

    @Test
    fun `a discarded copy is gone, and discarding it twice is harmless`() {
        val inUse = store.writeCopy(audio("the old sound"), stamp = 1_000)
        val unplayable = store.writeCopy(audio("not audio"), stamp = 2_000)

        store.discard(unplayable)
        store.discard(unplayable)

        assertEquals(listOf(inUse), copies())
    }
}
