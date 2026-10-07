package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

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
 * @param corner the small word at the tile's top corner that says what the tile is: "Truck".
 * @param phoneLabel under the phone's end of the line: "Your phone".
 * @param truckLabel under the truck's end: the truck's name.
 */
@Immutable
data class TruckTileWords(
    val title: String,
    val sentence: String?,
    val corner: String,
    val phoneLabel: String,
    val truckLabel: String,
)

/**
 * The design's tile for the truck's Bluetooth connection: the state in words, and under them a
 * drawing of the phone, the line to the truck, and the truck.
 *
 * The drawing repeats what the words say, so a screen reader is read the words only. Two of its
 * looks move (see `TruckLink`). The drawing is as high in one look as in another, so the tile
 * only changes its height when its sentence takes another number of lines.
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
    val connected = look == TruckLinkLook.CONNECTED
    Tile(
        modifier = modifier.fillMaxWidth(),
        press = press,
        gap = MiloTheme.spacing.controlPadding,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MiloTheme.spacing.rowGap)) {
            Column(
                modifier = Modifier.weight(1f).alignByBaseline(),
                verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.textGap),
            ) {
                Text(
                    text = words.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (connected) scheme.primary else scheme.onSurface,
                )
                if (words.sentence != null) {
                    Text(
                        text = words.sentence,
                        style = MiloTheme.textStyles.tileLabel,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            TileLabel(text = words.corner, modifier = Modifier.alignByBaseline())
        }
        Column(verticalArrangement = Arrangement.spacedBy(MiloTheme.spacing.small)) {
            TruckLink(look = look, modifier = Modifier.fillMaxWidth())
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = words.phoneLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = words.truckLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connected) scheme.onSurface else scheme.onSurfaceVariant,
                )
            }
        }
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
                            corner = "Truck",
                            phoneLabel = "Your phone",
                            truckLabel = "The truck",
                        ),
                )
                TruckTile(
                    look = TruckLinkLook.CONNECTING,
                    words =
                        TruckTileWords(
                            title = "Connecting…",
                            sentence = "Found the truck over Bluetooth",
                            corner = "Truck",
                            phoneLabel = "Your phone",
                            truckLabel = "The truck",
                        ),
                )
                TruckTile(
                    look = TruckLinkLook.CONNECTED,
                    words =
                        TruckTileWords(
                            title = "Connected",
                            sentence = "Trip recording started",
                            corner = "Truck",
                            phoneLabel = "Your phone",
                            truckLabel = "F-150",
                        ),
                )
            }
        }
    }
}
