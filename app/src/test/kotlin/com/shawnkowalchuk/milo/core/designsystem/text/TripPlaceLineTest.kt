package com.shawnkowalchuk.milo.core.designsystem.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "From → to" with the words that stand in for a missing address set apart, as the design
 * writes a trip: which part of the line is quiet, and that the sentence itself is kept.
 */
class TripPlaceLineTest {
    private val quiet = SpanStyle(color = Color.Gray)

    // The sentence as strings.xml has it, with the two marks where the sides belong.
    private val sentence = "$FROM_MARK → $TO_MARK"

    private fun address(line: String) = SideWords(line, standIn = false)

    private fun standIn(words: String) = SideWords(words, standIn = true)

    @Test
    fun `two addresses are written in the line's own style`() {
        val line = fromToLine(sentence, address("Shop"), address("Home"), quiet)

        assertEquals("Shop → Home", line.text)
        assertEquals(0, line.spanStyles.size)
    }

    @Test
    fun `the words for a missing end are quiet, and the address beside them is not`() {
        val line =
            fromToLine(sentence, address("Shop"), standIn("looking up the address…"), quiet)

        assertEquals("Shop → looking up the address…", line.text)
        val span = line.spanStyles.single()
        assertEquals(quiet, span.item)
        assertEquals("looking up the address…", line.text.substring(span.start, span.end))
    }

    @Test
    fun `the words for a missing start are quiet too`() {
        val line = fromToLine(sentence, standIn("no address found"), address("Shop"), quiet)

        assertEquals("no address found → Shop", line.text)
        val span = line.spanStyles.single()
        assertEquals("no address found", line.text.substring(span.start, span.end))
    }

    @Test
    fun `a sentence that names the end first keeps its own order`() {
        // No such language is in MilO today. The order is the sentence's, never this code's.
        val line =
            fromToLine("$TO_MARK ← $FROM_MARK", address("Shop"), standIn("looking up…"), quiet)

        assertEquals("looking up… ← Shop", line.text)
        val span = line.spanStyles.single()
        assertEquals(0, span.start)
        assertEquals("looking up…".length, span.end)
    }

    @Test
    fun `an address that holds the same words as a stand-in is not greyed`() {
        // What is quiet is decided by what a side is, not by looking for its words in the line.
        val line = fromToLine(sentence, address("no address found"), address("Shop"), quiet)

        assertEquals("no address found → Shop", line.text)
        assertEquals(0, line.spanStyles.size)
    }
}
