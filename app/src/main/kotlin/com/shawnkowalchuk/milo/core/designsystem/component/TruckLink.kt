package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.shawnkowalchuk.milo.core.designsystem.theme.FillAndText
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

// The drawing in the truck's tile: the phone, the line, the mark in its middle, the truck, in
// the design's three looks. Every measure is the design's. The line, its light and the
// spreading ring are drawn in TruckLinkDrawing.kt; what moves, how fast and how far is in
// TruckLinkMotion.kt.

/** The round patch at each end, and the icons on the two. */
private val EndSize = 48.dp
private val PhoneIconSize = 20.dp
private val TruckIconSize = 22.dp

/** The round mark in the middle of the line, and the icon on it. */
private val MarkSize = 36.dp
private val MarkIconSize = 16.dp

/**
 * The mark's outline while the two are not linked. Linked, the mark has no outline: it stands
 * clear of the line by a rim in the tile's colour instead.
 */
private val MarkOutline = 2.dp
private val MarkRim = 3.dp

/**
 * The phone, the line to the truck, and the truck, drawn for [look].
 *
 * It is a picture of what the words above it say, so a screen reader is told nothing here.
 *
 * **What moves, and when it does not.** The connecting and the connected look move; the idle
 * look never does. They move only while this drawing is at least partly inside the window and
 * the screen it is on is resumed: scrolled out of sight, behind another screen or with MilO in
 * the background there is no animation at all. With the phone's animations switched off
 * Compose holds every movement at its end, where the looks are drawn at rest. Every moving
 * value is read while drawing and nowhere else, so a frame redraws this picture and neither
 * lays it out nor composes anything again.
 */
@Composable
internal fun TruckLink(look: TruckLinkLook, modifier: Modifier = Modifier) {
    var inView by remember { mutableStateOf(false) }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val moving = inView && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    Row(
        modifier =
            modifier
                // What a scrolling screen cuts off is not in these bounds, so a drawing that
                // has been scrolled out of sight is not in view.
                .onGloballyPositioned { inView = !it.boundsInWindow().isEmpty }
                .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Every look is named, with no "else": a fourth look would not compile until it is drawn.
        when (look) {
            TruckLinkLook.IDLE -> IdleLink()
            TruckLinkLook.CONNECTING -> ConnectingLink(rememberConnectingMotion(moving))
            TruckLinkLook.CONNECTED -> ConnectedLink(rememberConnectedMotion(moving))
        }
    }
}

@Composable
private fun RowScope.IdleLink() {
    val colors = MiloTheme.colors
    val scheme = MaterialTheme.colorScheme
    EndPatch(MiloIcons.Phone, PhoneIconSize, colors.control)
    LineAndMark(line = { drawDashes(colors.idleOutline, turn = 0f) }) {
        // The tile's own colour, so that the dashes stop at the mark.
        OutlinedMark(fill = scheme.surfaceContainer, outline = colors.idleOutline) {
            LinkIcon(MiloIcons.Bluetooth, MarkIconSize, scheme.onSurfaceVariant)
        }
    }
    EndPatch(MiloIcons.Truck, TruckIconSize, FillAndText(colors.quietFill, colors.idleIcon))
}

@Composable
private fun RowScope.ConnectingLink(motion: ConnectingMotion) {
    val colors = MiloTheme.colors
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    EndPatch(MiloIcons.Phone, PhoneIconSize, colors.control)
    LineAndMark(line = { drawDashes(accent, turn = motion.dashes.value) }) {
        OutlinedMark(
            // The page's colour, darker than the tile: the mark is a dark hole in a lit ring.
            fill = scheme.background,
            outline = accent,
            // Drawn under the mark, and allowed to spread beyond it.
            modifier = Modifier.drawBehind { drawPing(accent, motion.ping.value) },
        ) {
            LinkIcon(
                icon = MiloIcons.Bluetooth,
                size = MarkIconSize,
                tint = accent,
                modifier = Modifier.graphicsLayer { alpha = markPulseAlpha(motion.pulse.value) },
            )
        }
    }
    EndPatch(MiloIcons.Truck, TruckIconSize, colors.control)
}

@Composable
private fun RowScope.ConnectedLink(motion: ConnectedMotion) {
    val scheme = MaterialTheme.colorScheme
    val lit = FillAndText(scheme.primary, scheme.onPrimary)
    val glint = MiloTheme.colors.linkGlint
    EndPatch(MiloIcons.Phone, PhoneIconSize, lit)
    LineAndMark(line = { drawLitLine(lit.fill, glint, glintStart(motion.glint.value)) }) {
        Box(
            modifier =
                Modifier
                    .size(MarkSize + MarkRim * 2)
                    // A rim in the tile's colour keeps the mark clear of the line.
                    .background(scheme.surfaceContainer, MiloTheme.shapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(MarkSize).background(lit.fill, MiloTheme.shapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                LinkIcon(MiloIcons.Link, MarkIconSize, lit.text)
            }
        }
    }
    EndPatch(
        icon = MiloIcons.Truck,
        iconSize = TruckIconSize,
        colors = lit,
        // Drawn under the truck's patch, and allowed to spread beyond it.
        modifier = Modifier.drawBehind { drawPing(lit.fill, motion.ping.value) },
    )
}

/** The stretch between the two ends: the line, and the round mark in its middle. */
@Composable
private fun RowScope.LineAndMark(line: DrawScope.() -> Unit, mark: @Composable () -> Unit) {
    Box(modifier = Modifier.weight(1f).height(EndSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize(), onDraw = line)
        mark()
    }
}

/** The mark while the two are not linked: a round patch with a line around it. */
@Composable
private fun OutlinedMark(
    fill: Color,
    outline: Color,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .size(MarkSize)
                .background(fill, MiloTheme.shapes.pill)
                .border(MarkOutline, outline, MiloTheme.shapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

/** The round patch at one end of the line, with its icon. */
@Composable
private fun EndPatch(
    icon: ImageVector,
    iconSize: Dp,
    colors: FillAndText,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(EndSize).background(colors.fill, MiloTheme.shapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        LinkIcon(icon, iconSize, colors.text)
    }
}

/** An icon of the drawing. The words above the drawing say what it shows. */
@Composable
private fun LinkIcon(icon: ImageVector, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint,
    )
}
