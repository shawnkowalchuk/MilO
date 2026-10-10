package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.runtime.staticCompositionLocalOf

/** What a preview is given for "now": Wednesday 7 October 2026, 18:00 UTC. A preview has no phone. */
private const val PREVIEW_NOW_MS = 1_791_396_000_000L

/**
 * The time of day for the few components that show one by themselves (the header's date, the
 * Log screen's "today"): MilO's clock, in milliseconds, never the phone's (ADR-005).
 *
 * `MiloApp` provides it once, from the app's container, for every screen. The default is a
 * fixed moment, so that a component can be previewed on its own; `WallClockReadersTest` checks
 * that `MiloApp` provides the real one, because a screen without it would show that fixed day.
 */
val LocalMiloClock = staticCompositionLocalOf<() -> Long> { { PREVIEW_NOW_MS } }
