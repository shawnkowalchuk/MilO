package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// This file is where the app's colour values are written.
// Screens and components ask for a colour by its role (MaterialTheme.colorScheme.primary,
// MiloTheme.statusColors.ok), never by its value, so light and dark mode both stay correct and
// a colour change happens here once.
//
// There are two exceptions, each explained where it is written:
//   - the launcher icon drawables (res/drawable/ic_launcher_*.xml), which the launcher draws
//     outside Compose and so cannot read these tokens;
//   - the placeholder fill in component/StatusIcons.kt, which Icon() always replaces with a tint.
//
// Every Material role is set explicitly. Any role left out falls back to Material's purple
// baseline, which then shows up unannounced the first time a component uses that role.

internal val LightColorScheme: ColorScheme =
    lightColorScheme(
        primary = Color(0xFF415F91),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD6E3FF),
        onPrimaryContainer = Color(0xFF284777),
        inversePrimary = Color(0xFFAAC7FF),
        secondary = Color(0xFF565F71),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDAE2F9),
        onSecondaryContainer = Color(0xFF3E4759),
        tertiary = Color(0xFF705575),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFAD8FD),
        onTertiaryContainer = Color(0xFF573E5C),
        background = Color(0xFFF9F9FF),
        onBackground = Color(0xFF191C20),
        surface = Color(0xFFF9F9FF),
        onSurface = Color(0xFF191C20),
        surfaceVariant = Color(0xFFE0E2EC),
        onSurfaceVariant = Color(0xFF44474E),
        surfaceTint = Color(0xFF415F91),
        inverseSurface = Color(0xFF2E3036),
        inverseOnSurface = Color(0xFFF0F0F7),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF93000A),
        outline = Color(0xFF74777F),
        outlineVariant = Color(0xFFC4C6D0),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFFF9F9FF),
        surfaceDim = Color(0xFFD9D9E0),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF3F3FA),
        surfaceContainer = Color(0xFFEDEDF4),
        surfaceContainerHigh = Color(0xFFE7E8EE),
        surfaceContainerHighest = Color(0xFFE2E2E9),
        primaryFixed = Color(0xFFD6E3FF),
        primaryFixedDim = Color(0xFFAAC7FF),
        onPrimaryFixed = Color(0xFF001B3E),
        onPrimaryFixedVariant = Color(0xFF284777),
        secondaryFixed = Color(0xFFDAE2F9),
        secondaryFixedDim = Color(0xFFBEC6DC),
        onSecondaryFixed = Color(0xFF131C2B),
        onSecondaryFixedVariant = Color(0xFF3E4759),
        tertiaryFixed = Color(0xFFFAD8FD),
        tertiaryFixedDim = Color(0xFFDDBCE0),
        onTertiaryFixed = Color(0xFF28132E),
        onTertiaryFixedVariant = Color(0xFF573E5C),
    )

internal val DarkColorScheme: ColorScheme =
    darkColorScheme(
        primary = Color(0xFFAAC7FF),
        onPrimary = Color(0xFF0A305F),
        primaryContainer = Color(0xFF284777),
        onPrimaryContainer = Color(0xFFD6E3FF),
        inversePrimary = Color(0xFF415F91),
        secondary = Color(0xFFBEC6DC),
        onSecondary = Color(0xFF283141),
        secondaryContainer = Color(0xFF3E4759),
        onSecondaryContainer = Color(0xFFDAE2F9),
        tertiary = Color(0xFFDDBCE0),
        onTertiary = Color(0xFF3F2844),
        tertiaryContainer = Color(0xFF573E5C),
        onTertiaryContainer = Color(0xFFFAD8FD),
        background = Color(0xFF111318),
        onBackground = Color(0xFFE2E2E9),
        surface = Color(0xFF111318),
        onSurface = Color(0xFFE2E2E9),
        surfaceVariant = Color(0xFF44474E),
        onSurfaceVariant = Color(0xFFC4C6D0),
        surfaceTint = Color(0xFFAAC7FF),
        inverseSurface = Color(0xFFE2E2E9),
        inverseOnSurface = Color(0xFF2E3036),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8E9099),
        outlineVariant = Color(0xFF44474E),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF37393E),
        surfaceDim = Color(0xFF111318),
        surfaceContainerLowest = Color(0xFF0C0E13),
        surfaceContainerLow = Color(0xFF191C20),
        surfaceContainer = Color(0xFF1D2024),
        surfaceContainerHigh = Color(0xFF282A2F),
        surfaceContainerHighest = Color(0xFF33353A),
        // The "fixed" roles are the one group that is meant to be identical in both schemes.
        primaryFixed = Color(0xFFD6E3FF),
        primaryFixedDim = Color(0xFFAAC7FF),
        onPrimaryFixed = Color(0xFF001B3E),
        onPrimaryFixedVariant = Color(0xFF284777),
        secondaryFixed = Color(0xFFDAE2F9),
        secondaryFixedDim = Color(0xFFBEC6DC),
        onSecondaryFixed = Color(0xFF131C2B),
        onSecondaryFixedVariant = Color(0xFF3E4759),
        tertiaryFixed = Color(0xFFFAD8FD),
        tertiaryFixedDim = Color(0xFFDDBCE0),
        onTertiaryFixed = Color(0xFF28132E),
        onTertiaryFixedVariant = Color(0xFF573E5C),
    )

/**
 * The two colours behind every "is this working?" indicator in the app.
 *
 * Material has a role for "error" but none for "all good", so the pair lives here rather than
 * borrowing an unrelated Material role for green. Reach it through `MiloTheme.statusColors`.
 */
@Immutable
data class MiloStatusColors(val ok: Color, val problem: Color)

// Each shade was picked to stay readable against the card surface of its own scheme, which is
// why light and dark mode do not share one green and one red. The reds match the scheme's
// error role so "problem" and Material's own error states look the same.
internal val LightStatusColors =
    MiloStatusColors(
        ok = Color(0xFF1E6B34),
        problem = Color(0xFFBA1A1A),
    )

internal val DarkStatusColors =
    MiloStatusColors(
        ok = Color(0xFF8BD89A),
        problem = Color(0xFFFFB4AB),
    )
