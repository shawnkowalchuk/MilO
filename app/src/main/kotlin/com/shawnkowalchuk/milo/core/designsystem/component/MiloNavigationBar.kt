package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One destination of the [MiloNavigationBar].
 *
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
 * It is Material 3's navigation bar with the theme's colours and nothing changed. Every entry
 * shows its label as well as its icon, so nobody has to learn what an icon means.
 */
@Composable
fun MiloNavigationBar(entries: List<NavigationBarEntry>, modifier: Modifier = Modifier) {
    NavigationBar(modifier = modifier) {
        for (entry in entries) {
            NavigationBarItem(
                selected = entry.selected,
                onClick = entry.onClick,
                // The label beside it says the same thing, so the icon is not read out twice.
                icon = { Icon(imageVector = entry.icon, contentDescription = null) },
                label = { Text(text = entry.label) },
            )
        }
    }
}
