package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How the truck's tile is drawn: the design's three looks. Which one is shown, and for how
 * long, is the screen's to say.
 */
enum class TruckLinkLook {
    /** Nothing links the two: grey dashes, a grey Bluetooth mark, the truck faint. It is still. */
    IDLE,

    /**
     * The truck has just been found: lime dashes that run toward the truck, a Bluetooth mark
     * that pulses in a lime ring, and a ring that spreads from it. Both ends are lit a little.
     */
    CONNECTING,

    /**
     * Linked: a solid line in the accent colour with a light running along it, a chain link
     * in the middle, both ends lit, and a ring that spreads from the truck.
     */
    CONNECTED,
}

/**
 * The words of a [TruckTile]. Kept together because they are read together.
 *
 * @param title the state in a word or two: "Not connected".
 * @param sentence what that means for the next trip, or null to say nothing more.
 * @param vehicle the vehicle's name, written small at the end of the title's line, or null to
 * write nothing there: no vehicle is paired, or it is not known yet whether one is.
 */
@Immutable
data class TruckTileWords(val title: String, val sentence: String?, val vehicle: String?)

/**
 * The room the vehicle's name is never cut below, in times its own text size: about five
 * letters and the "…". A title that leaves less than this beside itself goes onto a second line.
 */
private const val NAME_LEAST_EMS = 4f

/** How much of the line the name may take beside a title that needs more than one line. */
private const val NAME_SHARE_OF_LINE = 0.4f

/**
 * The tile for the truck's Bluetooth connection, in its low form (since 2026-10-09; the owner
 * asked for a tile that is "not as tall", and chose this one of three that were drawn for him).
 * Top to bottom: the state in a word or two with the vehicle's name at the end of the same
 * line, one sentence, and a drawing as wide as the tile of the phone, the line to the truck,
 * and the truck.
 *
 * The drawing repeats what the words say, so a screen reader is read the words only, and all
 * of them as one thing: the title, the vehicle's name, the sentence. Two of the drawing's looks
 * move (see `TruckLink`). The drawing is as high in one look as in another, so the tile only
 * changes its height when its title or its sentence takes another number of lines.
 *
 * It lays its own panel out, and does not go through `Tile`: it stands 14 dp from the panel's
 * top and bottom, where a `Tile` as wide as the screen stands 16.
 *
 * @param press makes the whole tile a button, for a state there is something to do about.
 */
@Composable
fun TruckTile(
    look: TruckLinkLook,
    words: TruckTileWords,
    modifier: Modifier = Modifier,
    press: TilePress? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val spacing = MiloTheme.spacing
    val connected = look == TruckLinkLook.CONNECTED
    // A tile that is a button is read as one thing already. A second "one thing" inside it
    // would take its words away from the button.
    val readAsOne = if (press == null) Modifier.semantics(mergeDescendants = true) {} else Modifier
    TileSurface(kind = TileKind.PLAIN, press = press, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                readAsOne.padding(
                    horizontal = spacing.gutter,
                    vertical = spacing.controlPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(spacing.tileGap),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.textGap)) {
                TitleLine(words.title, words.vehicle, connected)
                if (words.sentence != null) {
                    Text(
                        text = words.sentence,
                        style = MiloTheme.textStyles.tileLabel,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            TruckLink(look = look, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * The tile's first line: the title at its start and the vehicle's name at its end, both
 * standing on the title's first line of letters.
 *
 * **The title comes first.** It stays on one line whenever that leaves the name its least room
 * ([NAME_LEAST_EMS]), and the name then has what is left of the line: a name that is longer is
 * cut with "…" at its end, and never sends the title onto a second line. A title that cannot
 * stay on one line breaks, and the name may then take up to [NAME_SHARE_OF_LINE] of the line,
 * unless that would cost the title one more line than the name's least room does.
 */
@Composable
private fun TitleLine(title: String, vehicle: String?, connected: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val nameStyle = MaterialTheme.typography.labelMedium
    val gap = MiloTheme.spacing.rowGap
    Layout(
        content = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (connected) scheme.primary else scheme.onSurface,
            )
            if (vehicle != null) {
                Text(
                    text = vehicle,
                    style = nameStyle,
                    color = if (connected) scheme.onSurface else scheme.onSurfaceVariant,
                    // A name that is cut still ends at the tile's edge, where a whole one does.
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val title = measurables.first()
        val name = measurables.getOrNull(1)
        if (name == null) {
            val placed = title.measure(Constraints(maxWidth = width))
            layout(width, placed.height) { placed.placeRelative(0, 0) }
        } else {
            val least = (nameStyle.fontSize.toPx() * NAME_LEAST_EMS).roundToInt()
            titleAndName(title, name, width, gap.roundToPx(), least)
        }
    }
}

/** Measures and places the two parts of [TitleLine], by the rule that is written there. */
private fun MeasureScope.titleAndName(
    title: Measurable,
    name: Measurable,
    width: Int,
    gap: Int,
    nameLeast: Int,
): MeasureResult {
    val nameWanted = name.maxIntrinsicWidth(Constraints.Infinity)
    val leftOver = width - gap - title.maxIntrinsicWidth(Constraints.Infinity)
    val least = min(nameWanted, nameLeast)
    val share = min(nameWanted, (width * NAME_SHARE_OF_LINE).roundToInt())

    // How high the title is beside a name that is given this much of the line.
    fun titleHeightBeside(nameRoom: Int) =
        title.minIntrinsicHeight((width - gap - nameRoom).coerceAtLeast(0))

    val nameRoom =
        when {
            leftOver >= least -> min(nameWanted, leftOver)
            titleHeightBeside(share) > titleHeightBeside(least) -> least
            else -> share
        }
    val placedName = name.measure(Constraints(maxWidth = nameRoom))
    val titleRoom = (width - gap - placedName.width).coerceAtLeast(0)
    val placedTitle = title.measure(Constraints(maxWidth = titleRoom))
    // The smaller letters of the name stand on the line the title's first letters stand on.
    val nameDown = (placedTitle[FirstBaseline] - placedName[FirstBaseline]).coerceAtLeast(0)
    val height = max(placedTitle.height, nameDown + placedName.height)
    return layout(width, height) {
        placedTitle.placeRelative(0, 0)
        placedName.placeRelative(width - placedName.width, nameDown)
    }
}

// Sample text is written inline because a preview is never shown to a user or shipped in a
// screen; putting it in strings.xml would add resources the app does not use.
@Preview
@Composable
private fun TruckTilePreview() {
    MiloTheme {
        Surface {
            TileColumn {
                TruckTile(
                    look = TruckLinkLook.IDLE,
                    words =
                        TruckTileWords(
                            title = "Not connected",
                            sentence = "A trip starts by itself when the truck connects",
                            vehicle = "The truck",
                        ),
                )
                TruckTile(
                    look = TruckLinkLook.CONNECTING,
                    words =
                        TruckTileWords(
                            title = "Connecting…",
                            sentence = "Found the truck over Bluetooth",
                            vehicle = "F-150",
                        ),
                )
                TruckTile(
                    look = TruckLinkLook.CONNECTED,
                    words =
                        TruckTileWords(
                            title = "Connected",
                            sentence = "Trip recording started",
                            vehicle = "F-150",
                        ),
                )
            }
        }
    }
}

/** A title that needs two lines, and a name that is too long for what a title leaves. */
@Preview
@Composable
private fun TruckTileLongWordsPreview() {
    MiloTheme {
        Surface {
            TileColumn {
                TruckTile(
                    look = TruckLinkLook.CONNECTED,
                    words =
                        TruckTileWords(
                            title = "Truck connected. MilO has stopped watching it",
                            sentence =
                                "It has not moved for a long time. Press Start trip before " +
                                    "you drive off.",
                            vehicle = "F-150",
                        ),
                )
                TruckTile(
                    look = TruckLinkLook.CONNECTED,
                    words =
                        TruckTileWords(
                            title = "Truck connected and parked",
                            sentence = "A trip starts by itself when the truck moves",
                            vehicle = "The contractor's long-named work van",
                        ),
                )
                TruckTile(
                    look = TruckLinkLook.IDLE,
                    words =
                        TruckTileWords(
                            title = "No truck paired",
                            sentence = "Tap to pair one, so a trip can start by itself.",
                            vehicle = null,
                        ),
                    press = TilePress("pair the truck") {},
                )
            }
        }
    }
}
