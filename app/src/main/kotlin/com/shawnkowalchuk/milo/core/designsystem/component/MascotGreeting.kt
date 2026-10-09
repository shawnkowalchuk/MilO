package com.shawnkowalchuk.milo.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.shawnkowalchuk.milo.R

/**
 * MilO's greeting: the mascot, large, in the middle of the screen, over everything behind it
 * dimmed. It fades in, waves once ([MascotClip.WAVE]), fades out, and says [onDone].
 *
 * **Nothing shows, and the screen behind works as ever, until the mascot can be seen** (see
 * [Mascot]): a screen that dims and stays empty for a moment would look broken. From then on a
 * tap anywhere, or Android's Back, ends the greeting early with the same fade, and nothing
 * behind it can be pressed.
 *
 * With the phone's animations switched off, and on a phone that cannot draw the mascot,
 * [onDone] is called with nothing having been seen.
 *
 * Whether there is a greeting at all is not decided here (`feature/greeting`).
 *
 * @param beforeStart awaited before the mascot's drawing begins, and [onShown] is called once
 * it has been seen: between the two lies everything that can go wrong in native code.
 */
@Composable
fun MascotGreeting(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    beforeStart: suspend () -> Unit = {},
    onShown: () -> Unit = {},
) {
    var seen by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val fade = remember { Animatable(0f) }
    // The effect below outlives a recomposition; this keeps it calling the newest function.
    val newestOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(seen, leaving) {
        if (leaving) {
            fade.animateTo(0f, tween(LEAVE_MS))
            newestOnDone()
        } else if (seen) {
            fade.animateTo(1f, tween(ARRIVE_MS))
        }
    }
    BackHandler(enabled = seen && !leaving) { leaving = true }

    val says = stringResource(R.string.mascot_greeting)
    val skip = stringResource(R.string.mascot_greeting_skip)
    val pressable =
        if (seen) {
            Modifier
                .clickable(
                    interactionSource = null,
                    // No ripple: the tap is on the whole screen, not on a button.
                    indication = null,
                    onClickLabel = skip,
                ) { leaving = true }
                .semantics { contentDescription = says }
        } else {
            Modifier
        }
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fade.value }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = DIM))
                .then(pressable),
        contentAlignment = Alignment.Center,
    ) {
        Mascot(
            clip = MascotClip.WAVE,
            onFinished = { leaving = true },
            modifier = Modifier.fillMaxWidth(MASCOT_WIDTH).aspectRatio(1f),
            beforeStart = beforeStart,
            onShown = {
                seen = true
                onShown()
            },
        )
    }
}

/** How dark the screen behind the mascot gets: enough that the mascot is what is looked at. */
private const val DIM = 0.72f

/** The part of the screen's width the mascot's square takes. */
private const val MASCOT_WIDTH = 0.86f

private const val ARRIVE_MS = 180
private const val LEAVE_MS = 260
