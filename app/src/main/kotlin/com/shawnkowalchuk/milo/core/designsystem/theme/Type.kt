package com.shawnkowalchuk.milo.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

// Material 3's type scale is kept as it is, on the phone's own system font. Building a custom
// scale would mean maintaining fifteen text styles for an app with a handful of screens.
private val Baseline = Typography()

/**
 * The app's text styles. Screens pick a role (`MaterialTheme.typography.titleMedium`); sizes and
 * weights are only ever changed here.
 */
internal val MiloTypography: Typography =
    Baseline.copy(
        // Screen and section headings are read at a glance, often with the phone in a dashboard
        // mount, so they are heavier than Material's default to stand apart from body text.
        headlineSmall = Baseline.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = Baseline.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
