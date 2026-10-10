package com.shawnkowalchuk.milo.data.clock

import com.shawnkowalchuk.milo.core.clock.ClockAnchor
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The file MilO's clock keeps its anchor in between two processes. */
class ClockAnchorStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val folder by lazy { File(temporaryFolder.root, "clock") }
    private val store by lazy { ClockAnchorStore(folder) }
    private val anchor =
        ClockAnchor(
            bootCount = 212,
            wallMs = 1_791_419_562_000L,
            elapsedMs = 2_751_334_339L,
            onProbationSinceMs = null,
        )

    @Test
    fun `what is written is read back`() {
        store.write(anchor)

        assertEquals(anchor, ClockAnchorStore(folder).read())
    }

    @Test
    fun `an anchor on probation is read back on probation, since the same moment`() {
        val unproven = anchor.copy(onProbationSinceMs = 2_751_214_339L)

        store.write(unproven)

        assertEquals(unproven, ClockAnchorStore(folder).read())
        assertEquals(
            "1 212 1791419562000 2751334339 2751214339\n",
            File(folder, "anchor").readText(),
        )
    }

    @Test
    fun `a line that was cut short anywhere is no anchor`() {
        // A cut can leave four whole numbers and the front part of the last word, or a last
        // number that is only the front part of the real one. Each would read as some anchor;
        // none is the one that was written.
        for (written in listOf(anchor, anchor.copy(onProbationSinceMs = 2_751_214_339L))) {
            store.write(written)
            val whole = File(folder, "anchor").readText()
            assertEquals(written, store.read())

            for (kept in 0 until whole.length) {
                File(folder, "anchor").writeText(whole.take(kept))
                assertNull("the first $kept characters of \"${whole.trim()}\"", store.read())
            }
        }
    }

    @Test
    fun `a new anchor takes the place of the one before, and leaves no second file`() {
        store.write(anchor)
        val later = anchor.copy(
            wallMs = anchor.wallMs + 300_000,
            elapsedMs =
                anchor.elapsedMs + 300_000,
        )

        store.write(later)

        assertEquals(later, store.read())
        assertEquals(listOf("anchor"), folder.list().orEmpty().toList())
    }

    @Test
    fun `with no file there is no anchor`() {
        assertNull(store.read())
        assertFalse(folder.exists())
    }

    @Test
    fun `a file that does not hold an anchor reads as none`() {
        val unreadable =
            listOf(
                "",
                "\n",
                "not an anchor at all",
                // Cut short, as a write that was not atomic could leave it.
                "1 212 1791419562",
                "1 212 1791419562000",
                // One word too many, a number that is none, and a layout this build cannot know.
                "1 212 1791419562000 2751334339 7",
                "1 212 1791419562000 soon",
                "1 two 1791419562000 2751334339",
                "2 212 1791419562000 2751334339",
                // The same with a whole line's end: a word short, a word too many, a last word
                // that is neither a number nor "proven", and the unknown layout.
                "1 212 1791419562000 2751334339\n",
                "1 212 1791419562000 2751334339 proven 7\n",
                "1 212 1791419562000 2751334339 soon\n",
                "1 two 1791419562000 2751334339 proven\n",
                "2 212 1791419562000 2751334339 proven\n",
                // A whole anchor with no line end: it may be the front part of a longer one.
                "1 212 1791419562000 2751334339 proven",
                "1 212 1791419562000 2751334339 2751214339",
            )
        folder.mkdirs()

        for (text in unreadable) {
            File(folder, "anchor").writeText(text)
            assertNull("\"$text\"", store.read())
        }
    }

    @Test
    fun `a file left half written beside the anchor does not disturb it`() {
        store.write(anchor)
        File(folder, "anchor.new").writeText("1 212 17914")

        assertEquals(anchor, store.read())
        // And the next write replaces both.
        store.write(anchor.copy(bootCount = 213))
        assertEquals(213, store.read()?.bootCount)
    }

    @Test
    fun `a folder that is no folder reads as no anchor, and refuses a write loudly`() {
        temporaryFolder.root.mkdirs()
        folder.writeText("a file where the folder should be")

        assertNull(store.read())
        assertThrows(IOException::class.java) { store.write(anchor) }
    }
}
