package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii. Components take a role (`MaterialTheme.shapes.medium`), never a radius.
 *
 * The values are Material 3's own defaults, written out so the app's corners are decided here
 * and do not shift if a future Material release changes its defaults.
 */
internal val MiloShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )
