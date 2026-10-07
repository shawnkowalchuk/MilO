package com.shawnkowalchuk.milo.feature.setup

import com.shawnkowalchuk.milo.platform.system.SetupRow
import com.shawnkowalchuk.milo.platform.system.SetupState

/**
 * What the tile at the top of Setup says about the checklist: how many of its rows are in each
 * state. Every row is in exactly one of the four, so the four add up to [total].
 *
 * It counts every row, the recommended ones too, as the design draws it. The home screen's
 * warning is a different question, "may a trip fail to start by itself?", and goes by the
 * required rows only (`needsAttention`). So this tile can say "1 to fix" for a recommended row
 * while Home shows no warning; while Home does warn, this tile never says that all is ready.
 *
 * @param ready in order: read from the phone, or confirmed by Shawn.
 * @param toFix not in order, as read from the phone.
 * @param toConfirm MilO cannot read the setting at all: Shawn sets it and says so.
 * @param notChecked MilO tried to find out and could not, or has not finished yet. Counted by
 * itself, because it is none of the other three: nothing says the setting is wrong, nothing
 * says it is right, and for the truck's row there is nothing for Shawn to confirm.
 */
data class SetupSummary(
    val total: Int,
    val ready: Int,
    val toFix: Int,
    val toConfirm: Int,
    val notChecked: Int,
) {
    /** True when there is nothing left to do. A checklist always has rows. */
    val allReady: Boolean get() = ready == total

    /** The share of the rows that are ready, from 0 to 1: how far the bar is filled. */
    val readyShare: Float get() = if (total == 0) 0f else ready.toFloat() / total
}

/** Counts the rows of the checklist by their state. */
fun setupSummary(rows: List<SetupRow>): SetupSummary = SetupSummary(
    total = rows.size,
    ready = rows.count { it.state == SetupState.OK },
    toFix = rows.count { it.state == SetupState.PROBLEM },
    toConfirm = rows.count { it.state == SetupState.NEEDS_CONFIRMATION },
    notChecked = rows.count { it.state == SetupState.UNKNOWN },
)

/**
 * The rows of one group in the order the screen lists them, as the owner's design draws it: the
 * rows that are to be fixed stand first, where the count above them says "2 to fix", and the
 * others follow. Each part keeps the checklist's own order, so a row whose sentence points to
 * "the row above" still has that row above it: the two are only ever to be fixed together.
 *
 * Only "to fix" moves a row. One that waits for Shawn's word, or that MilO could not read,
 * stays where the checklist has it, as on the drawing.
 */
fun toFixFirst(rows: List<SetupRow>): List<SetupRow> {
    val (toFix, others) = rows.partition { it.state == SetupState.PROBLEM }
    return toFix + others
}
