package com.shawnkowalchuk.milo.data.transfer

import java.io.Reader

/** The text is not JSON a reader can follow: it ends too early, or something is out of place. */
internal class BrokenJsonException(message: String) : Exception(message)

/**
 * The longest single value that is read into memory: one trip, one sent report, the settings.
 * Each is a few hundred characters. The limit is what keeps a file that is not an export (a
 * video, a text with one quotation mark in it) from being read into memory whole.
 */
private const val MAX_VALUE_CHARS = 1_000_000

private const val BUFFER_CHARS = 16 * 1024
private const val END = -1

/** The invisible mark some programs put in front of a text file they save. */
private const val BYTE_ORDER_MARK = 0xFEFF

/**
 * Walks through one JSON object without ever holding more of it than one value.
 *
 * An export with its raw GPS points is tens of megabytes, a million rows and more, and read
 * into memory whole it would be several times that. So the document is taken apart here, by
 * its brackets and quotation marks alone: the keys of the object one after the other, and of a
 * value that is a list, its elements one after the other, each as the piece of text it is. What
 * a piece means is the caller's business; it hands each one to the JSON library.
 *
 * Nothing here depends on how the file is laid out in lines, so a file that was opened and
 * saved again by another program is read like one MilO wrote.
 *
 * @param source read from start to end, once. The caller closes it.
 */
internal class JsonScanner(private val source: Reader) {
    private val buffer = CharArray(BUFFER_CHARS)
    private var filled = 0
    private var at = 0
    private var firstKey = true
    private var firstElement = true

    /** Enters the object the whole document is. */
    fun beginObject() {
        if (peek() == BYTE_ORDER_MARK) at++
        expect('{')
    }

    /** The next key of the object, or null once the object is closed. */
    fun nextKey(): String? {
        skipSpace()
        if (peek() == '}'.code) {
            at++
            return null
        }
        if (!firstKey) expect(',')
        firstKey = false
        skipSpace()
        if (peek() != '"'.code) throw broken("a key was expected")
        val key = StringBuilder().also { takeString(it) }
        expect(':')
        // Keys are plain words; the quotation marks are all there is to take off.
        return key.substring(1, key.length - 1)
    }

    /** The value that follows a key or stands in a list, whole, as the text it is. */
    fun value(): String = StringBuilder().also { take(it) }.toString()

    /** Passes over the value that follows, however long it is, without keeping it. */
    fun skipValue() {
        take(into = null)
    }

    /**
     * Enters the list that follows.
     *
     * @return false if what follows is `null` and not a list. It has been passed over then.
     */
    fun beginList(): Boolean {
        skipSpace()
        if (peek() == 'n'.code) {
            if (value() != "null") throw broken("a list or null was expected")
            return false
        }
        expect('[')
        firstElement = true
        return true
    }

    /** The next element of the list as text, or null once the list is closed. */
    fun nextElement(): String? {
        skipSpace()
        if (peek() == ']'.code) {
            at++
            return null
        }
        if (!firstElement) expect(',')
        firstElement = false
        return value()
    }

    /** After the object has closed: nothing but blank space may follow. */
    fun endDocument() {
        skipSpace()
        if (peek() != END) throw broken("something follows the end of the document")
    }

    private fun take(into: StringBuilder?) {
        skipSpace()
        when (peek()) {
            '"'.code -> takeString(into)
            '{'.code, '['.code -> takeNested(into)
            else -> takeWord(into)
        }
    }

    /** A text in quotation marks. A backslash takes the character after it along, unread. */
    private fun takeString(into: StringBuilder?) {
        keep(into, next())
        while (true) {
            val char = next()
            if (char == END) throw broken("it ends inside a text")
            keep(into, char)
            if (char == '\\'.code) {
                val escaped = next()
                if (escaped == END) throw broken("it ends inside a text")
                keep(into, escaped)
            } else if (char == '"'.code) {
                return
            }
        }
    }

    /** An object or a list, to the bracket that closes it. Brackets inside a text do not count. */
    private fun takeNested(into: StringBuilder?) {
        var depth = 0
        while (true) {
            when (peek()) {
                END -> throw broken("it ends before a bracket is closed")

                '"'.code -> takeString(into)

                else -> {
                    val char = next()
                    keep(into, char)
                    if (char == '{'.code || char == '['.code) depth++
                    if (char == '}'.code || char == ']'.code) depth--
                    if (depth == 0) return
                }
            }
        }
    }

    /** A number, `true`, `false` or `null`: everything up to what can only come after it. */
    private fun takeWord(into: StringBuilder?) {
        var taken = 0
        while (true) {
            val char = peek()
            if (char == END || char.toChar().isWhitespace() || char.toChar() in ",]}") break
            keep(into, next())
            taken++
        }
        if (taken == 0) throw broken("a value was expected")
    }

    private fun keep(into: StringBuilder?, char: Int) {
        if (into == null) return
        if (into.length >= MAX_VALUE_CHARS) throw broken("one value is longer than any MilO writes")
        into.append(char.toChar())
    }

    private fun expect(wanted: Char) {
        skipSpace()
        if (next() != wanted.code) throw broken("\"$wanted\" was expected")
    }

    private fun skipSpace() {
        while (peek() != END && peek().toChar().isWhitespace()) at++
    }

    private fun peek(): Int {
        if (at >= filled) {
            filled = source.read(buffer)
            at = 0
            if (filled <= 0) {
                filled = 0
                return END
            }
        }
        return buffer[at].code
    }

    private fun next(): Int = peek().also { if (it != END) at++ }

    private fun broken(what: String) = BrokenJsonException("The text is not complete JSON: $what")
}
