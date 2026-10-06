package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Calls [onCameToFront] when the screen is first shown and every time Shawn is looking at it
 * again. A screen that shows something Android reports no changes of (a permission, a setting,
 * the phone's paired devices) reads it again at that moment.
 *
 * Two signs are needed, because neither covers the other:
 * - **The screen resumes:** it is entered, or MilO returns from a settings screen, another app
 *   or one of Android's own dialogs.
 * - **MilO's window gets the focus back:** the quick settings panel and the notification shade
 *   cover MilO without pausing it, so switching Location or Bluetooth there resumes nothing.
 *
 * Coming back from a settings screen gives both signs, so [onCameToFront] must be safe to call
 * twice in a row.
 */
@Composable
fun CameToFrontEffect(onCameToFront: () -> Unit) {
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onCameToFront() }

    val windowInfo = LocalWindowInfo.current
    // The effect below outlives a recomposition; this keeps it calling the newest function.
    val newestOnCameToFront by rememberUpdatedState(onCameToFront)
    LaunchedEffect(windowInfo) {
        snapshotFlow { windowInfo.isWindowFocused }
            .focusRegained()
            .collect { newestOnCameToFront() }
    }
}

/**
 * Turns "does the window have the focus?" into the moments it got the focus back.
 *
 * The first value is how the screen was entered, not a return: entering is the resume's job.
 * Losing the focus is not a return either.
 */
internal fun Flow<Boolean>.focusRegained(): Flow<Unit> = drop(1).filter { it }.map { }
