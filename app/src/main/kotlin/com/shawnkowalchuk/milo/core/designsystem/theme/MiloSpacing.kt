package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing scale. Every gap and padding in the app is one of these steps, reached through
 * `MiloTheme.spacing`, so screens line up with each other without anyone measuring.
 *
 * The steps follow Material's 4 dp grid. Add a step here if a design truly needs one; do not
 * write a dp value in a screen.
 */
@Immutable
data class MiloSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
)
