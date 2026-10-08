package com.shawnkowalchuk.milo.core.report

// The pieces a page of the report is stacked from: the heading, a day's heading, the column
// titles, one trip, a subtotal, and the end of the report. Each piece is laid out by itself,
// from its own top, and knows how high it is; `layoutReport` decides which page it lands on.

/** The room between two columns. */
private const val GUTTER = 6f

/** The room above and below the text of a trip's row. */
private const val ROW_PADDING = 2f

/** Between the kilometres and the asterisk of a marked trip. */
private const val MARK_GAP = 2f

/** An address column is never narrower than this, however wide the other columns turn out. */
private const val MIN_ADDRESS_WIDTH = 60f

/** The room under a day, before the next one. */
internal const val DAY_GAP = 8f

/**
 * The table of a day stands this far in from the margins, so that its first and last columns
 * line up with the words of the day's heading on its tile.
 */
private const val TABLE_INSET = 8f

// The tiles, as the app draws them, with round corners, but set closer than on the screens:
// 6 apart, the words 10 to 12 from the edge (since 2026-10-08: "Same look, tighter",
// so the trips start higher on the first page). The corners are smaller than the screens'
// because the page is larger than a phone: what reads as round on a phone reads as a bubble
// on paper.
private const val TILE_GAP = 6f
private const val TILE_RADIUS = 10f
private const val TILE_PADDING = 12f
private const val SMALL_TILE_PADDING = 10f

/** The app's mark: a square of the accent with the app's initial, its corner as on Home. */
private const val MARK_SIZE = 28f
private const val MARK_RADIUS = 8f

/** Between the mark and the app's name. */
private const val NAME_GAP = 10f

/** How far a capital's middle stands above its baseline, as a share of the font size. */
private const val CAP_MIDDLE = 0.36f

/** The tile a day's heading stands on. */
private const val DAY_BAR_HEIGHT = 16f
private const val DAY_BAR_RADIUS = 5f

/** The room under the report's heading, before the first day. */
private const val SECTION_GAP = 10f

// Rules are drawn with a thickness of their own. A hairline (thickness 0) is one device pixel
// wide whatever the scale, and can print almost invisibly.
private const val THIN_RULE = 0.5f
private const val RULE = 0.75f

/** How long the two lines at the end of the report are, and the room between them. */
private const val SIGNATURE_LINE = 250f
private const val SIGNATURE_GAP = 40f
private const val DATE_LINE = 140f

/** The room above the signature line: enough to sign in. */
private const val SIGNATURE_ROOM = 36f

/** Between a line to write on and the word under it. */
private const val SIGNATURE_WORDS_GAP = 2f

/** A piece of a page: [items] placed from the piece's own top, and how high it is. */
internal class Block(val height: Float, val items: List<PageItem>)

/** Builds a [Block] from the top down. */
private class BlockBuilder {
    private val items = mutableListOf<PageItem>()

    /** How far down the block the builder has come. */
    var y = 0f
        private set

    /** Writes one line at the present height. The height stays: more can stand beside it. */
    fun text(
        text: String,
        x: Float,
        style: ReportTextStyle,
        rightAligned: Boolean = false,
        ink: ReportInk = ReportInk.DARK,
    ) {
        // A line's letters stand one font size under its top, which leaves the rest of the
        // leading under them, where the tails of "g" and "y" need it.
        textAt(text, x, y + style.size, style, rightAligned, ink)
    }

    /** Writes one line with its letters on [baseline], measured from the block's top. */
    fun textAt(
        text: String,
        x: Float,
        baseline: Float,
        style: ReportTextStyle,
        rightAligned: Boolean = false,
        ink: ReportInk = ReportInk.DARK,
    ) {
        items += PageItem.Text(text, x, baseline, style, rightAligned, ink)
    }

    /** Writes [lines] under each other from [firstBaseline], measured from the block's top. */
    fun column(
        lines: List<String>,
        x: Float,
        firstBaseline: Float,
        style: ReportTextStyle,
        ink: ReportInk = ReportInk.DARK,
        rightAligned: Boolean = false,
    ) {
        lines.forEachIndexed { index, line ->
            textAt(line, x, firstBaseline + index * style.leading, style, rightAligned, ink)
        }
    }

    /** Writes [lines] under each other from the present height, without moving on. */
    fun column(lines: List<String>, x: Float, style: ReportTextStyle, ink: ReportInk) {
        column(lines, x, y + style.size, style, ink)
    }

    fun down(height: Float) {
        y += height
    }

    fun rule(
        thickness: Float,
        fromX: Float = CONTENT_LEFT,
        toX: Float = CONTENT_RIGHT,
        ink: ReportInk = ReportInk.DARK,
    ) {
        items += PageItem.Rule(fromX, toX, y, thickness, ink)
    }

    /** A tile from the present height down, [height] high. Drawn under what follows it. */
    fun box(left: Float, right: Float, height: Float, radius: Float, ink: ReportInk) {
        items += PageItem.Box(left, y, right, y + height, radius, ink)
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
                measure.width(words.columnKm, title)
            ).max()
    val startX = CONTENT_LEFT + TABLE_INSET
    val kmRight = CONTENT_RIGHT - TABLE_INSET - measure.width(REPORT_MARK, cell) - MARK_GAP
    val fromX = startX + 2 * (timeWidth + GUTTER)
    val kmLeft = kmRight - kmWidth
    val addressWidth = ((kmLeft - GUTTER - fromX - GUTTER) / 2).coerceAtLeast(MIN_ADDRESS_WIDTH)
    return Columns(
        startX = startX,
        endX = startX + timeWidth + GUTTER,
        fromX = fromX,
        toX = fromX + addressWidth + GUTTER,
        addressWidth = addressWidth,
        kmRight = kmRight,
        markX = kmRight + MARK_GAP,
        sumLabelRight = kmLeft - GUTTER,
    )
}

/**
 * The top of the first page, as the app's own screens start: the dark tile with the app's
 * mark, its name and the period; the period's Business and Personal kilometres on two tiles
 * side by side, Business in the accent as on the Trips screen; who the report is from; the
 * truck's odometer at the start and the end of the period, on a tile like the sender's, once
 * a reading has been typed in; and the notes.
 */
internal fun headingBlock(report: PrintedReport, measure: TextMeasure): Block = block {
    brandTile(report, measure)
    down(TILE_GAP)
    tallyTiles(report, measure)
    down(TILE_GAP)
    senderTile(report.fields, measure)
    down(TILE_GAP)
    if (report.odometer.isNotEmpty()) {
        senderTile(report.odometer, measure)
        down(TILE_GAP)
    }
    for (note in report.notes) {
        val lines = wrap(note, CONTENT_RIGHT - CONTENT_LEFT, ReportTextStyle.NOTE, measure)
        column(lines, CONTENT_LEFT, ReportTextStyle.NOTE, ReportInk.QUIET)
        down(lines.size * ReportTextStyle.NOTE.leading)
    }
    down(SECTION_GAP)
}

/**
 * The dark tile at the top: the app's mark, "MilO" over "Mileage report" at the start, and the
 * period over the day the report was made at the end, as a screen's top line is laid out. A
 * period too long for its room (a date range) takes more lines, and the tile grows.
 */
private fun BlockBuilder.brandTile(report: PrintedReport, measure: TextMeasure) {
    val words = report.words
    val name = ReportTextStyle.BRAND
    val line = ReportTextStyle.BRAND_LINE
    val period = ReportTextStyle.PERIOD
    val markLeft = CONTENT_LEFT + TILE_PADDING
    val nameX = markLeft + MARK_SIZE + NAME_GAP
    val nameEnd =
        nameX + maxOf(measure.width(words.appName, name), measure.width(words.title, line))
    val periodRight = CONTENT_RIGHT - TILE_PADDING
    val periodLines = wrap(report.period, periodRight - nameEnd - TILE_GAP * 2, period, measure)

    // The two lines at the start stand beside the mark, the period's first line level with
    // the app's name, and the day it was made under the period's last line.
    val firstBaseline = TILE_PADDING + name.size
    val secondBaseline = firstBaseline + line.size + line.size * 2 / 3
    val generatedBaseline = secondBaseline + (periodLines.size - 1) * period.leading
    val height = maxOf(MARK_SIZE, generatedBaseline - TILE_PADDING + line.size / 3) +
        TILE_PADDING * 2

    val top = y
    box(CONTENT_LEFT, CONTENT_RIGHT, height, TILE_RADIUS, ReportInk.DARK)
    down(TILE_PADDING)
    box(markLeft, markLeft + MARK_SIZE, MARK_SIZE, MARK_RADIUS, ReportInk.ACCENT)
    val mark = ReportTextStyle.MARK
    textAt(
        words.appMark,
        markLeft + (MARK_SIZE - measure.width(words.appMark, mark)) / 2,
        y + MARK_SIZE / 2 + mark.size * CAP_MIDDLE,
        mark,
    )
    textAt(words.appName, nameX, top + firstBaseline, name, ink = ReportInk.ON_DARK)
    textAt(words.title, nameX, top + secondBaseline, line, ink = ReportInk.ON_DARK_QUIET)
    column(
        periodLines,
        periodRight,
        top + firstBaseline,
        period,
        ReportInk.ON_DARK,
        rightAligned = true,
    )
    textAt(
        report.generated,
        periodRight,
        top + generatedBaseline,
        line,
        rightAligned = true,
        ink = ReportInk.ON_DARK_QUIET,
    )
    down(height - TILE_PADDING)
}

/**
 * The period's kilometres on two tiles side by side: Business, the trips the report lists, on
 * the accent, and Personal, which it does not list, on a quiet tile. Each has its label, the
 * kilometres in large figures, and how many trips they add up.
 */
private fun BlockBuilder.tallyTiles(report: PrintedReport, measure: TextMeasure) {
    val label = ReportTextStyle.TILE_LABEL
    val figure = ReportTextStyle.FIGURE
    val unit = ReportTextStyle.FIGURE_UNIT
    val trips = ReportTextStyle.TILE_LINE
    val width = (CONTENT_RIGHT - CONTENT_LEFT - TILE_GAP) / 2
    val labelBaseline = SMALL_TILE_PADDING + label.size
    val figureBaseline = labelBaseline + figure.size + label.size / 2
    val tripsBaseline = figureBaseline + trips.leading + trips.size / 2
    val height = tripsBaseline + SMALL_TILE_PADDING
    val top = y
    listOf(report.business, report.personal).forEachIndexed { index, tally ->
        val business = index == 0
        val left = CONTENT_LEFT + index * (width + TILE_GAP)
        val x = left + SMALL_TILE_PADDING
        val quiet = if (business) ReportInk.DARK else ReportInk.QUIET
        val fill = if (business) ReportInk.ACCENT else ReportInk.PANEL
        box(left, left + width, height, TILE_RADIUS, fill)
        textAt(tally.label, x, top + labelBaseline, label, ink = quiet)
        textAt(tally.km, x, top + figureBaseline, figure)
        val unitX = x + measure.width(tally.km, figure) + GUTTER / 2
        textAt(report.words.columnKm, unitX, top + figureBaseline, unit)
        textAt(tally.trips, x, top + tripsBaseline, trips, ink = quiet)
    }
    down(height)
}

/**
 * Who the report is from, on a quiet tile: each detail under its small label, side by side in
 * equal columns. A long one (a vehicle described with its plate) wraps inside its column. The
 * odometer's two figures are set on a tile of this kind too.
 */
private fun BlockBuilder.senderTile(fields: List<Pair<String, String>>, measure: TextMeasure) {
    val label = ReportTextStyle.LABEL
    val value = ReportTextStyle.VALUE
    val inside = CONTENT_RIGHT - CONTENT_LEFT - SMALL_TILE_PADDING * 2
    val columnWidth = (inside - (fields.size - 1) * SMALL_TILE_PADDING) / fields.size
    val values = fields.map { (_, content) -> wrap(content, columnWidth, value, measure) }
    val labelBaseline = SMALL_TILE_PADDING + label.size
    val valueBaseline = labelBaseline + value.leading
    val height =
        valueBaseline + (values.maxOf { it.size } - 1) * value.leading + SMALL_TILE_PADDING
    val top = y
    box(CONTENT_LEFT, CONTENT_RIGHT, height, TILE_RADIUS, ReportInk.PANEL)
    fields.forEachIndexed { index, (name, _) ->
        val x = CONTENT_LEFT + SMALL_TILE_PADDING + index * (columnWidth + SMALL_TILE_PADDING)
        textAt(name, x, top + labelBaseline, label, ink = ReportInk.QUIET)
        column(values[index], x, top + valueBaseline, value)
    }
    down(height)
}

/**
 * A day's heading, its date or its date and "(continued)" on a following page, on a quiet tile
 * as wide as the table.
 */
internal fun dayHeadingBlock(heading: String): Block = block {
    val style = ReportTextStyle.DAY
    box(CONTENT_LEFT, CONTENT_RIGHT, DAY_BAR_HEIGHT, DAY_BAR_RADIUS, ReportInk.PANEL)
    textAt(heading, CONTENT_LEFT + TABLE_INSET, DAY_BAR_HEIGHT / 2 + style.size * CAP_MIDDLE, style)
    down(DAY_BAR_HEIGHT + ROW_PADDING * 2)
}

/** The titles of the five columns, small and quiet, once on each page above its first day. */
internal fun columnTitlesBlock(words: ReportWords, columns: Columns): Block = block {
    val style = ReportTextStyle.COLUMN
    val quiet = ReportInk.QUIET
    text(words.columnStart, columns.startX, style, ink = quiet)
    text(words.columnEnd, columns.endX, style, ink = quiet)
    text(words.columnFrom, columns.fromX, style, ink = quiet)
    text(words.columnTo, columns.toX, style, ink = quiet)
    text(words.columnKm, columns.kmRight, style, rightAligned = true, ink = quiet)
    down(style.leading + ROW_PADDING)
}

/**
 * One trip, under a fine rule. Its two addresses are wrapped inside their columns, and the row
 * is as high as the longer of them needs: a row is never cut, so it is laid out whole.
 */
internal fun rowBlock(row: PrintedRow, columns: Columns, measure: TextMeasure): Block = block {
    val style = ReportTextStyle.CELL
    val from = wrap(row.from, columns.addressWidth, style, measure)
    val to = wrap(row.to, columns.addressWidth, style, measure)
    rule(THIN_RULE, columns.startX, CONTENT_RIGHT - TABLE_INSET, ReportInk.HAIRLINE)
    down(ROW_PADDING)
    text(row.start, columns.startX, style)
    text(row.end, columns.endX, style)
    column(from, columns.fromX, style, ReportInk.DARK)
    column(to, columns.toX, style, ReportInk.DARK)
    text(row.km, columns.kmRight, style, rightAligned = true)
    if (row.marked) text(REPORT_MARK, columns.markX, style)
    down(maxOf(from.size, to.size) * style.leading + ROW_PADDING)
}

/** A day's subtotal, under a rule, with the kilometres under the column they add up. */
internal fun subtotalBlock(day: PrintedDay, words: ReportWords, columns: Columns): Block = block {
    val style = ReportTextStyle.SUM
    rule(RULE, columns.startX, CONTENT_RIGHT - TABLE_INSET)
    down(ROW_PADDING)
    text(words.subtotal, columns.sumLabelRight, style, rightAligned = true, ink = ReportInk.QUIET)
    text(day.subtotalKm, columns.kmRight, style, rightAligned = true)
    down(style.leading)
}

/** Said in place of the days when the period has no Business trip. */
internal fun emptyBlock(note: String): Block = block {
    text(note, CONTENT_LEFT, ReportTextStyle.CELL, ink = ReportInk.QUIET)
    down(ReportTextStyle.CELL.leading + DAY_GAP)
}

/**
 * The end of the report: the period's total on a dark tile, like the one the report starts
 * with, in the accent and under the kilometres column; what the asterisk means if a trip
 * carries one; and a blank line to sign on with one for the date beside it. Kept in one piece,
 * so the signature is never on a page without the total it signs for.
 */
internal fun closingBlock(report: PrintedReport, columns: Columns, measure: TextMeasure): Block =
    block {
        val note = ReportTextStyle.NOTE
        val label = ReportTextStyle.TOTAL_LABEL
        val count = ReportTextStyle.BRAND_LINE
        val total = ReportTextStyle.TOTAL
        val labelX = CONTENT_LEFT + TILE_PADDING
        val room = columns.kmRight - measure.width(report.totalKm, total) - GUTTER * 2 - labelX
        val labelLines = wrap(report.totalLabel, room, label, measure)
        val labelBaseline = TILE_PADDING + label.size
        val countBaseline = labelBaseline + (labelLines.size - 1) * label.leading + count.leading
        val wordsBottom = countBaseline + count.size / 3
        val height = maxOf(wordsBottom, TILE_PADDING + total.size) + TILE_PADDING
        val top = y
        box(CONTENT_LEFT, CONTENT_RIGHT, height, TILE_RADIUS, ReportInk.DARK)
        column(labelLines, labelX, top + labelBaseline, label, ReportInk.ON_DARK)
        textAt(report.tripCount, labelX, top + countBaseline, count, ink = ReportInk.ON_DARK_QUIET)
        // Level with the middle of the words beside it.
        textAt(
            report.totalKm,
            columns.kmRight,
            top + height / 2 + total.size * CAP_MIDDLE,
            total,
            rightAligned = true,
            ink = ReportInk.ACCENT,
        )
        down(height)
        report.legend?.let { legend ->
            down(TILE_GAP)
            val lines = wrap(legend, CONTENT_RIGHT - CONTENT_LEFT, note, measure)
            column(lines, CONTENT_LEFT, note, ReportInk.QUIET)
            down(lines.size * note.leading)
        }
        down(SIGNATURE_ROOM)
        val dateX = CONTENT_LEFT + SIGNATURE_LINE + SIGNATURE_GAP
        rule(RULE, toX = CONTENT_LEFT + SIGNATURE_LINE)
        rule(RULE, fromX = dateX, toX = dateX + DATE_LINE)
        down(SIGNATURE_WORDS_GAP)
        text(report.words.signature, CONTENT_LEFT, note, ink = ReportInk.QUIET)
        text(report.words.signatureDate, dateX, note, ink = ReportInk.QUIET)
        down(note.leading)
    }
