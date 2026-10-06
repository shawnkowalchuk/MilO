package com.shawnkowalchuk.milo.core.report

// The pieces a page of the report is stacked from: the heading, a day's heading, the column
// titles, one trip, a subtotal, and the end of the report. Each piece is laid out by itself,
// from its own top, and knows how high it is; `layoutReport` decides which page it lands on.

/** The room between two columns. */
private const val GUTTER = 8f

/** The room above and below the text of a trip's row. */
private const val ROW_PADDING = 2f

/** Between the kilometres and the asterisk of a marked trip. */
private const val MARK_GAP = 2f

/** An address column is never narrower than this, however wide the other columns turn out. */
private const val MIN_ADDRESS_WIDTH = 60f

/** The room under a day, before the next one. */
internal const val DAY_GAP = 12f

// Rules are drawn with a thickness of their own. A hairline (thickness 0) is one device pixel
// wide whatever the scale, and can print almost invisibly.
private const val THIN_RULE = 0.5f
private const val RULE = 0.75f
private const val HEAVY_RULE = 1f

/** How long the two lines at the end of the report are, and the room between them. */
private const val SIGNATURE_LINE = 250f
private const val SIGNATURE_GAP = 40f
private const val DATE_LINE = 140f

/** The room above the signature line: enough to sign in. */
private const val SIGNATURE_ROOM = 44f

/** A piece of a page: [items] placed from the piece's own top, and how high it is. */
internal class Block(val height: Float, val items: List<PageItem>)

/** Builds a [Block] from the top down. */
private class BlockBuilder {
    private val items = mutableListOf<PageItem>()
    private var y = 0f

    /** Writes one line at the present height. The height stays: more can stand beside it. */
    fun text(text: String, x: Float, style: ReportTextStyle, rightAligned: Boolean = false) {
        // A line's letters stand one font size under its top, which leaves the rest of the
        // leading under them, where the tails of "g" and "y" need it.
        items += PageItem.Text(text, x, y + style.size, style, rightAligned)
    }

    /** Writes [lines] under each other from the present height, without moving on. */
    fun column(lines: List<String>, x: Float, style: ReportTextStyle) {
        lines.forEachIndexed { index, line ->
            items += PageItem.Text(line, x, y + style.size + index * style.leading, style)
        }
    }

    fun down(height: Float) {
        y += height
    }

    fun rule(thickness: Float, fromX: Float = CONTENT_LEFT, toX: Float = CONTENT_RIGHT) {
        items += PageItem.Rule(fromX, toX, y, thickness)
    }

    fun build(): Block = Block(y, items.toList())
}

private fun block(build: BlockBuilder.() -> Unit): Block = BlockBuilder().apply(build).build()

/**
 * Where the five columns stand. The two times and the kilometres are as wide as their widest
 * entry, so "10:45 p.m." fits as surely as "22:45", and the two addresses share what is left.
 *
 * @param kmRight where the kilometres end: they are written from the right, so that the
 * decimal points stand under each other.
 * @param markX where the asterisk of a marked trip stands, after the kilometres.
 */
internal data class Columns(
    val startX: Float,
    val endX: Float,
    val fromX: Float,
    val toX: Float,
    val addressWidth: Float,
    val kmRight: Float,
    val markX: Float,
    val sumLabelRight: Float,
)

internal fun columnsFor(report: PrintedReport, measure: TextMeasure): Columns {
    val words = report.words
    val rows = report.days.flatMap { it.rows }
    val cell = ReportTextStyle.CELL
    val title = ReportTextStyle.COLUMN
    val timeWidth =
        (
            rows.flatMap { listOf(it.start, it.end) }.map { measure.width(it, cell) } +
                measure.width(words.columnStart, title) +
                measure.width(words.columnEnd, title)
            ).max()
    val kmWidth =
        (
            rows.map { measure.width(it.km, cell) } +
                report.days.map { measure.width(it.subtotalKm, ReportTextStyle.SUM) } +
                measure.width(report.totalKm, ReportTextStyle.TOTAL) +
                measure.width(words.columnKm, title)
            ).max()
    val kmRight = CONTENT_RIGHT - measure.width(REPORT_MARK, cell) - MARK_GAP
    val fromX = CONTENT_LEFT + 2 * (timeWidth + GUTTER)
    val kmLeft = kmRight - kmWidth
    val addressWidth = ((kmLeft - GUTTER - fromX - GUTTER) / 2).coerceAtLeast(MIN_ADDRESS_WIDTH)
    return Columns(
        startX = CONTENT_LEFT,
        endX = CONTENT_LEFT + timeWidth + GUTTER,
        fromX = fromX,
        toX = fromX + addressWidth + GUTTER,
        addressWidth = addressWidth,
        kmRight = kmRight,
        markX = kmRight + MARK_GAP,
        sumLabelRight = kmLeft - GUTTER,
    )
}

/** The top of the first page: the title, who and what the report is for, and the notes. */
internal fun headingBlock(report: PrintedReport, measure: TextMeasure): Block = block {
    val label = ReportTextStyle.LABEL
    val value = ReportTextStyle.VALUE
    text(report.words.title, CONTENT_LEFT, ReportTextStyle.TITLE)
    down(ReportTextStyle.TITLE.leading + GUTTER)
    val valueX = CONTENT_LEFT + report.fields.maxOf { measure.width(it.first, label) } + GUTTER * 2
    for ((name, content) in report.fields) {
        // A long vehicle description is wrapped under itself, beside its label.
        val lines = wrap(content, CONTENT_RIGHT - valueX, value, measure)
        text(name, CONTENT_LEFT, label)
        column(lines, valueX, value)
        down(lines.size * value.leading)
    }
    down(GUTTER / 2)
    for (note in report.notes) {
        val lines = wrap(note, CONTENT_RIGHT - CONTENT_LEFT, ReportTextStyle.NOTE, measure)
        column(lines, CONTENT_LEFT, ReportTextStyle.NOTE)
        down(lines.size * ReportTextStyle.NOTE.leading)
    }
    down(GUTTER)
    rule(HEAVY_RULE)
    down(GUTTER * 2)
}

/** A day's heading: its date, or its date and "(continued)" on a following page. */
internal fun dayHeadingBlock(heading: String): Block = block {
    text(heading, CONTENT_LEFT, ReportTextStyle.DAY)
    down(ReportTextStyle.DAY.leading + ROW_PADDING)
}

/** The titles of the five columns, with a rule under them. */
internal fun columnTitlesBlock(words: ReportWords, columns: Columns): Block = block {
    val style = ReportTextStyle.COLUMN
    text(words.columnStart, columns.startX, style)
    text(words.columnEnd, columns.endX, style)
    text(words.columnFrom, columns.fromX, style)
    text(words.columnTo, columns.toX, style)
    text(words.columnKm, columns.kmRight, style, rightAligned = true)
    down(style.leading + ROW_PADDING)
    rule(RULE)
    down(ROW_PADDING)
}

/**
 * One trip. Its two addresses are wrapped inside their columns, and the row is as high as the
 * longer of them needs: a row is never cut, so it is laid out whole.
 */
internal fun rowBlock(row: PrintedRow, columns: Columns, measure: TextMeasure): Block = block {
    val style = ReportTextStyle.CELL
    val from = wrap(row.from, columns.addressWidth, style, measure)
    val to = wrap(row.to, columns.addressWidth, style, measure)
    down(ROW_PADDING)
    text(row.start, columns.startX, style)
    text(row.end, columns.endX, style)
    column(from, columns.fromX, style)
    column(to, columns.toX, style)
    text(row.km, columns.kmRight, style, rightAligned = true)
    if (row.marked) text(REPORT_MARK, columns.markX, style)
    down(maxOf(from.size, to.size) * style.leading + ROW_PADDING)
}

/** A day's subtotal, under a thin rule, with the kilometres under the column they add up. */
internal fun subtotalBlock(day: PrintedDay, words: ReportWords, columns: Columns): Block = block {
    val style = ReportTextStyle.SUM
    down(ROW_PADDING)
    rule(THIN_RULE)
    down(ROW_PADDING)
    text(words.subtotal, columns.sumLabelRight, style, rightAligned = true)
    text(day.subtotalKm, columns.kmRight, style, rightAligned = true)
    down(style.leading)
}

/** Said in place of the days when the period has no Business trip. */
internal fun emptyBlock(note: String): Block = block {
    text(note, CONTENT_LEFT, ReportTextStyle.CELL)
    down(ReportTextStyle.CELL.leading + DAY_GAP)
}

/**
 * The end of the report: the period's total, what the asterisk means if a trip carries one, and
 * a blank line to sign on with one for the date beside it. Kept in one piece, so the signature
 * is never on a page without the total it signs for.
 */
internal fun closingBlock(report: PrintedReport, columns: Columns, measure: TextMeasure): Block =
    block {
        val note = ReportTextStyle.NOTE
        rule(HEAVY_RULE)
        down(GUTTER / 2)
        text(report.totalLabel, CONTENT_LEFT, ReportTextStyle.TOTAL)
        text(report.totalKm, columns.kmRight, ReportTextStyle.TOTAL, rightAligned = true)
        down(ReportTextStyle.TOTAL.leading)
        text(report.tripCount, CONTENT_LEFT, note)
        down(note.leading)
        report.legend?.let { legend ->
            down(GUTTER / 2)
            val lines = wrap(legend, CONTENT_RIGHT - CONTENT_LEFT, note, measure)
            column(lines, CONTENT_LEFT, note)
            down(lines.size * note.leading)
        }
        down(SIGNATURE_ROOM)
        val dateX = CONTENT_LEFT + SIGNATURE_LINE + SIGNATURE_GAP
        rule(RULE, toX = CONTENT_LEFT + SIGNATURE_LINE)
        rule(RULE, fromX = dateX, toX = dateX + DATE_LINE)
        down(ROW_PADDING)
        text(report.words.signature, CONTENT_LEFT, note)
        text(report.words.signatureDate, dateX, note)
        down(note.leading)
    }
