package com.shawnkowalchuk.milo.core.designsystem.theme

import android.graphics.Color.TRANSPARENT
import androidx.activity.SystemBarStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

// Spacing, shapes and the extra text styles are the same wherever they are asked for, so a
// default is safe and lets a component be previewed on its own.
private val LocalMiloSpacing = staticCompositionLocalOf { MiloSpacing() }
private val LocalMiloShapes = staticCompositionLocalOf { MiloShapes() }
private val LocalMiloTextStyles = staticCompositionLocalOf { MiloTextStyles() }

// The colours have no default on purpose. A screen that forgot its theme would draw MilO's own
// colours beside Material's purple baseline for everything else, and nobody would notice before
// it is on the phone, so this fails loudly instead.
private val LocalMiloColors =
    staticCompositionLocalOf<MiloColors> {
        error("No MiloTheme found. Wrap the screen or preview in MiloTheme { }.")
    }
private val LocalMiloStatusColors =
    staticCompositionLocalOf<MiloStatusColors> {
        error("No MiloTheme found. Wrap the screen or preview in MiloTheme { }.")
    }

/**
 * The app's theme. Every screen and every preview is wrapped in it exactly once.
 *
 * **MilO has one look, and it is dark.** It does not follow the phone's light or dark setting:
 * the design the owner chose on 2026-10-06 ("Bento") is drawn dark and has no light variant, and
 * a light one made up here would be a second design nobody drew. Material You wallpaper colours
 * are not used either: the status dots have to mean the same thing on every screen, and a
 * wallpaper-derived palette can sit too close to any of them.
 */
@Composable
fun MiloTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMiloColors provides MiloExtraColors,
        LocalMiloStatusColors provides MiloStatus,
    ) {
        MaterialTheme(
            colorScheme = MiloColorScheme,
            typography = MiloTypography,
            shapes = MiloMaterialShapes,
            content = content,
        )
    }
}

/**
 * Access to the tokens Material does not have a slot for. What Material does cover (its colour
 * roles and its fifteen text styles) is read from `MaterialTheme` as usual.
 */
object MiloTheme {
    val spacing: MiloSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalMiloSpacing.current

    val shapes: MiloShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalMiloShapes.current

    val textStyles: MiloTextStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalMiloTextStyles.current

    val colors: MiloColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMiloColors.current

    val statusColors: MiloStatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMiloStatusColors.current
}

/**
 * How Android's own two bars are drawn over MilO, at the top (clock, battery) and at the bottom
 * (the gesture line or the three buttons): see-through, with light icons, always.
 *
 * "Always" is the point. Left to itself Android picks the icons by the phone's light or dark
 * setting, and with the phone set to light it would draw dark icons on MilO's dark page, where
 * they cannot be seen. MilO is dark whatever the phone is set to, so its bars say so themselves.
 * `MainActivity` hands this to `enableEdgeToEdge` for both bars.
 */
val MiloSystemBarStyle: SystemBarStyle = SystemBarStyle.dark(TRANSPARENT)
