package com.shawnkowalchuk.milo.data.transfer

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The walk through a JSON document by its brackets and quotation marks alone. */
class JsonScannerTest {
    private fun scan(text: String) = JsonScanner(StringReader(text)).also { it.beginObject() }

    @Test
    fun `keys and values come one after the other, each value as the text it is`() {
        val scanner =
            scan("""{ "a" : 1, "b":"two" ,"c":{"d":[1,{"e":null}]},"f":[ ], "g":-1.5e3 }""")

        assertEquals("a", scanner.nextKey())
        assertEquals("1", scanner.value())
        assertEquals("b", scanner.nextKey())
        assertEquals("\"two\"", scanner.value())
        assertEquals("c", scanner.nextKey())
        assertEquals("""{"d":[1,{"e":null}]}""", scanner.value())
        assertEquals("f", scanner.nextKey())
        assertEquals("[ ]", scanner.value())
        assertEquals("g", scanner.nextKey())
        assertEquals("-1.5e3", scanner.value())
        assertNull(scanner.nextKey())
        scanner.endDocument()
    }

    @Test
    fun `brackets, commas and quotation marks inside a text are part of the text`() {
        val tricky = """"a ] } , \" \\" """.trim()
        val scanner = scan("""{"k":$tricky,"l":[$tricky,{"m":$tricky}]}""")

        assertEquals("k", scanner.nextKey())
        assertEquals(tricky, scanner.value())
        assertEquals("l", scanner.nextKey())
        assertTrue(scanner.beginList())
        assertEquals(tricky, scanner.nextElement())
        assertEquals("""{"m":$tricky}""", scanner.nextElement())
        assertNull(scanner.nextElement())
        assertNull(scanner.nextKey())
    }

    @Test
    fun `the elements of a list come one after the other, and a list can be null or empty`() {
        val scanner = scan("""{"a":[ {"x":1} , [2,3],4 ],"b":null,"c":[]}""")

        scanner.nextKey()
        assertTrue(scanner.beginList())
        assertEquals("""{"x":1}""", scanner.nextElement())
        assertEquals("[2,3]", scanner.nextElement())
        assertEquals("4", scanner.nextElement())
        assertNull(scanner.nextElement())
        scanner.nextKey()
        assertFalse(scanner.beginList())
        scanner.nextKey()
        assertTrue(scanner.beginList())
        assertNull(scanner.nextElement())
        assertNull(scanner.nextKey())
    }

    @Test
    fun `a value can be passed over without being kept, however long it is`() {
        val long = "[" + "[1,\"]\"],".repeat(300_000) + "0]"
        val scanner = scan("""{"big":$long,"after":true}""")

        scanner.nextKey()
        scanner.skipValue()
        assertEquals("after", scanner.nextKey())
        assertEquals("true", scanner.value())
    }

    @Test
    fun `a single value longer than any MilO writes is refused before it fills the memory`() {
        val scanner = scan("""{"k":"${"x".repeat(1_000_001)}"}""")

        scanner.nextKey()
        assertThrows(BrokenJsonException::class.java) { scanner.value() }
    }

    @Test
    fun `what is out of place is refused`() {
        val broken =
            listOf(
                """{"a":1,}""",
                """{"a" 1}""",
                """{"a":}""",
                """{a:1}""",
                """{"a":1 "b":2}""",
                """{"a":[1,2""",
                """{"a":"unclosed""",
                """{"a":"ends in a backslash\""",
            )

        for (text in broken) {
            assertThrows(text, BrokenJsonException::class.java) {
                val scanner = scan(text)
                while (scanner.nextKey() != null) scanner.value()
                scanner.endDocument()
            }
        }
        assertThrows(BrokenJsonException::class.java) { scan("[1]") }
        assertThrows(BrokenJsonException::class.java) { scan("") }
    }

    @Test
    fun `a list that is cut short, or has a comma too many`() {
        for (text in listOf("""{"a":[1,]}""", """{"a":[1 2]}""", """{"a":[1,""")) {
            assertThrows(text, BrokenJsonException::class.java) {
                val scanner = scan(text)
                scanner.nextKey()
                scanner.beginList()
                var elements = 0
                while (scanner.nextElement() != null) elements++
            }
        }
    }
}
