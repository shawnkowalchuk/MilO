package com.shawnkowalchuk.milo.core.clock

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Nothing in MilO reads the phone's wall clock but MilO's own clock** (ADR-005). The owner
 * sets the phone's date a day ahead for a few seconds to get lives in a game, and every part of
 * MilO that asked the phone for the time in those seconds got tomorrow. So the time is handed
 * down from one place (`AppContainer.clock`, which is `TrustedClock.now`), and this test reads
 * the source tree and fails when a second reader appears.
 *
 * It looks at code only: comments and the text inside quotation marks are left out, so a
 * sentence about the phone's clock is not a reading of it. Code inside a string's `${...}` is
 * code, and is looked at. The phone's time ZONE is not guarded: `ZoneId.systemDefault()` is
 * read wherever a day is worked out.
 */
class WallClockReadersTest {
    /** Every way a line of Kotlin can ask the phone what time it is. */
    private val asksThePhone =
        listOf(
            Regex("""\bcurrentTimeMillis\b"""),
            Regex("""\b(LocalDate|LocalTime|LocalDateTime|ZonedDateTime|OffsetDateTime)\.now\("""),
            Regex("""\b(Instant|YearMonth|Year|MonthDay|OffsetTime)\.now\("""),
            Regex("""\bClock\.system"""),
            // Kotlin's own clock (`kotlin.time.Clock.System`), with a capital S.
            Regex("""\bClock\.System\b"""),
            Regex("""\bCalendar\.getInstance\("""),
            Regex("""\bGregorianCalendar\("""),
            Regex("""\bDate\(\)"""),
            Regex("""\bcurrentNetworkTimeClock\b"""),
            Regex("""\bcurrentGnssTimeClock\b"""),
        )

    /** The ways to go by the phone's clock without asking it for the time in so many words. */
    private val goesByThePhone =
        listOf(
            // Android's `DateUtils` asks "is this today?" and "how long ago?" of the phone's clock.
            Regex("""\bDateUtils\b"""),
            // Measures a length of time as the difference of two readings of the phone's clock.
            Regex("""\bmeasureTimeMillis\b"""),
            // A file's date is the phone's time at the moment the file was written.
            Regex("""\blastModified\b"""),
            Regex("""\bgetLastModifiedTime\b"""),
            // A notification's "when": Android works the time since then out by the phone's clock.
            Regex("""\bsetWhen\("""),
        )

    private val wallClockReads = asksThePhone + goesByThePhone

    /**
     * The files that may hold one of the above, with how many lines of each do, and the reason.
     * One file reads the phone's clock. The other three use something Android or the file
     * system stamps by it, and each is named with what a jump of the date can do there
     * (FINDINGS_LOG 2026-10-07, "What Android stamps itself still goes by the phone's clock").
     */
    private val allowed =
        mapOf(
            // The Android side of MilO's clock: where `TrustedClock` is handed the phone's
            // clock as one of its two inputs, to hold its own time against.
            "com/shawnkowalchuk/milo/platform/clock/PhoneClock.kt" to 1,
            // The date of a crash file that cannot be read in full: the only time there is
            // for it. The import dates its line no later than the moment it is imported.
            "com/shawnkowalchuk/milo/data/crash/CrashFileStore.kt" to 1,
            // Old report files are cleared from the cache by their date, held against a moment
            // worked out from MilO's clock. A file written inside a jump is kept a day longer.
            "com/shawnkowalchuk/milo/data/report/ReportFileStore.kt" to 1,
            // The trip notification's running time, which Android counts up by itself. Posted
            // inside a jump it reads a day long until it is next posted (tagged as debt there).
            "com/shawnkowalchuk/milo/platform/trip/TripNotifications.kt" to 1,
        )

    private val sources = File("src/main/kotlin")

    @Test
    fun `only MilO's own clock reads the phone's wall clock`() {
        val readers =
            sources.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    val path = file.relativeTo(sources).invariantSeparatorsPath
                    readingLinesOf(file.readText()).map { line -> "$path:$line" }
                }
                .toList()

        val notAllowed = readers.filterNot { it.substringBeforeLast(':') in allowed }
        assertEquals(
            "These lines read the phone's wall clock. Hand them MilO's clock instead " +
                "(AppContainer.clock), or the phone's date trick reaches them (ADR-005)",
            emptyList<String>(),
            notAllowed,
        )
        // And each allowed file holds exactly as many such lines as is written down here, so
        // that a second use in it is looked at too.
        for ((path, count) in allowed) {
            assertEquals(path, count, readers.count { it.substringBeforeLast(':') == path })
        }
    }

    @Test
    fun `the one file that asks the phone for the time is MilO's own clock`() {
        // The allow-list above also lets three files use a date that something else stamped.
        // None of them may ask the phone for the time itself.
        val askers =
            sources.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { file ->
                    codeLinesOf(file.readText()).any { line ->
                        asksThePhone.any { it.containsMatchIn(line) }
                    }
                }
                .map { it.relativeTo(sources).invariantSeparatorsPath }
                .toList()

        assertEquals(listOf("com/shawnkowalchuk/milo/platform/clock/PhoneClock.kt"), askers)
    }

    @Test
    fun `the app's root hands MilO's clock to the screens that show a day by themselves`() {
        // `LocalMiloClock` has a fixed moment as its default, for previews. A root that did
        // not provide the real clock would show that day in every header.
        val root = File(sources, "com/shawnkowalchuk/milo/app/MiloApp.kt").readText()

        assertTrue(root.contains("LocalMiloClock provides container.clock"))
    }

    @Test
    fun `the test itself tells code from comments and quoted text`() {
        val text =
            """
            // System.currentTimeMillis() in a comment
            /** KDoc: LocalDate.now() */
            val said = "Instant.now() in a string" // and Date() after it
            /*
             * Calendar.getInstance() in a block
             */
            val read = System.currentTimeMillis()
            val kotlins = Clock.System.now()
            """.trimIndent()

        assertEquals(listOf(7, 8), readingLinesOf(text))
    }

    @Test
    fun `the test finds a reading inside a template, and the readers that do not say so`() {
        val dollar = "$"
        val quotes = "\"\"\""
        val text =
            """
            val told = "at $dollar{System.currentTimeMillis()} it began"
            val plain = "a price of ${dollar}5 and a brace { in text: Instant.now()"
            val nested = "outer $dollar{"inner $dollar{LocalDate.now()} text"} and Date() in text"
            val after = "closed $dollar{name}" + Instant.now()
            val raw = ${quotes}Calendar.getInstance() in raw text, "quoted" too$quotes
            val rawTold = ${quotes}at $dollar{Instant.now()}$quotes
            val brace = '}' to '"'
            val today = DateUtils.isToday(file.lastModified())
            val took = measureTimeMillis { work() }
            val date = Files.getLastModifiedTime(path)
            builder.setWhen(startedAtMs)
            val quiet = "DateUtils, measureTimeMillis and lastModified in text"
            """.trimIndent()

        assertEquals(listOf(1, 3, 4, 6, 8, 9, 10, 11), readingLinesOf(text))
    }

    /** The numbers of the lines of [source], from 1, whose code reads the phone's wall clock. */
    private fun readingLinesOf(source: String): List<Int> =
        codeLinesOf(source).mapIndexedNotNull { index, line ->
            (index + 1).takeIf { wallClockReads.any { it.containsMatchIn(line) } }
        }

    /** What [codeLinesOf] is inside of, innermost last. */
    private sealed interface Within {
        /** Between two quotation marks. */
        data object Text : Within

        /** Between two triple quotation marks, where a backslash is only a backslash. */
        data object RawText : Within

        /** In the code of a `${...}` inside a text, with [openBraces] of its own still open. */
        class Template(var openBraces: Int = 0) : Within
    }

    /**
     * The lines of [source] with comments and the text of string literals blanked out, and the
     * code inside a string's `${...}` kept. Simple on purpose: it knows `//`, block comments,
     * quoted and raw text, templates and character literals, which is what MilO's sources are
     * made of.
     */
    private fun codeLinesOf(source: String): List<String> {
        val code = StringBuilder()
        val within = ArrayDeque<Within>()
        var index = 0
        var inBlockComment = false
        var inLineComment = false
        while (index < source.length) {
            val char = source[index]
            val next = source.getOrNull(index + 1)
            val innermost = within.lastOrNull()
            when {
                char == '\n' -> {
                    inLineComment = false
                    code.append(char)
                }

                inLineComment -> Unit

                inBlockComment -> if (char == '*' && next == '/') {
                    inBlockComment = false
                    index++
                }

                innermost is Within.Text || innermost is Within.RawText -> when {
                    char == '$' && next == '{' -> {
                        within.addLast(Within.Template())
                        // A blank, so that the code does not run into what stood before it.
                        code.append(' ')
                        index++
                    }

                    innermost is Within.Text && char == '\\' -> index++

                    innermost is Within.Text && char == '"' -> within.removeLast()

                    innermost is Within.RawText && source.startsWith(RAW_QUOTES, index) -> {
                        within.removeLast()
                        index += RAW_QUOTES.length - 1
                    }
                }

                // A character literal: a quotation mark or a brace in one opens nothing.
                char == '\'' -> index = source.indexOf('\'', index + if (next == '\\') 3 else 2)
                    .takeIf { it >= 0 } ?: source.length

                char == '/' && next == '/' -> inLineComment = true

                char == '/' && next == '*' -> {
                    inBlockComment = true
                    index++
                }

                source.startsWith(RAW_QUOTES, index) -> {
                    within.addLast(Within.RawText)
                    index += RAW_QUOTES.length - 1
                }

                char == '"' -> within.addLast(Within.Text)

                innermost is Within.Template && char == '{' -> {
                    innermost.openBraces++
                    code.append(char)
                }

                innermost is Within.Template && char == '}' -> if (innermost.openBraces == 0) {
                    within.removeLast()
                    code.append(' ')
                } else {
                    innermost.openBraces--
                    code.append(char)
                }

                else -> code.append(char)
            }
            index++
        }
        return code.lines()
    }

    private companion object {
        /** What opens and closes a raw text. */
        const val RAW_QUOTES = "\"\"\""
    }
}
