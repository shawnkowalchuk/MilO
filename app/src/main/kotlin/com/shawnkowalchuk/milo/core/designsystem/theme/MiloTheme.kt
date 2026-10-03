package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

// Spacing does not depend on light or dark mode, so a default is safe and lets a component be
// previewed on its own.
private val LocalMiloSpacing = staticCompositionLocalOf { MiloSpacing() }

// Status colours do depend on the mode. Defaulting to the light pair would silently draw the
// wrong green in dark mode whenever a screen forgot its theme, so this fails loudly instead.
private val LocalMiloStatusColors =
    staticCompositionLocalOf<MiloStatusColors> {
        error("No MiloTheme found. Wrap the screen or preview in MiloTheme { }.")
    }

/**
 * The app's theme. Every screen and every preview is wrapped in it exactly once.
 *
 * It follows the phone's light or dark setting. Material You wallpaper colours are deliberately
 * not used: the green and red status indicators have to mean the same thing on every screen,
 * and a wallpaper-derived palette can sit too close to either of them.
 */
@Composable
fun MiloTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMiloSpacing provides MiloSpacing(),
        LocalMiloStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = MiloTypography,
            shapes = MiloShapes,
            content = content,
        )
    }
}

/**
 * Access to the tokens Material does not have a slot for. Colours, type and shapes that Material
 * does cover are read from `MaterialTheme` as usual.
 */
object MiloTheme {
    val spacing: MiloSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalMiloSpacing.current

    val statusColors: MiloStatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMiloStatusColors.current
}
