package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/** How high the design draws the bar. Each of its buttons is that high too, so well over 48 dp. */
private val BarHeight = 64.dp

/** The size of each of its icons, as drawn. */
private val IconSize = 22.dp

/**
 * How far the bar floats above the bottom edge of the screen, as drawn. On a phone whose own
 * gesture line or buttons need more room than this, the bar sits above them instead.
 */
private val FloatAboveEdge = WindowInsets(bottom = 20.dp)

/**
 * One destination of the [MiloNavigationBar].
 *
 * @param label the screen's name. The bar draws no words, so this is what a screen reader says
 * for the icon.
 * @param selected true for the destination that is showing. Exactly one entry is selected.
 */
@Immutable
data class NavigationBarEntry(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * The bar at the bottom of the phone UI: one button for each top-level screen, always visible.
 *
 * It is the design's floating bar: a rounded tile that stands clear of the screen's edges, with
 * icons and no words. The icon of the screen that is showing is drawn in the main text colour
 * and the others in the grey of secondary text. A screen reader is told each screen's name, that
 * the buttons are tabs, and which one is selected, as with Material's own bar.
 *
 * The room Android's own bottom bar takes (the gesture line or the three buttons) is left free
 * under it, so the two never lie on top of each other.
 */
@Composable
fun MiloNavigationBar(entries: List<NavigationBarEntry>, modifier: Modifier = Modifier) {
    val spacing = MiloTheme.spacing
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(NavigationBarDefaults.windowInsets.union(FloatAboveEdge))
                .padding(start = spacing.gutter, end = spacing.gutter, top = spacing.rowGap),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(BarHeight),
            shape = MiloTheme.shapes.smallTile,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = spacing.buttonGap).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (entry in entries) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .selectable(
                                    selected = entry.selected,
                                    role = Role.Tab,
                                    onClick = entry.onClick,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = entry.label,
                            modifier = Modifier.size(IconSize),
                            tint =
                                if (entry.selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }
    }
}
