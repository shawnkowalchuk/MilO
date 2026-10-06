package com.shawnkowalchuk.milo.app

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.rememberNavBackStack
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog
import com.shawnkowalchuk.milo.core.designsystem.component.MiloNavigationBar
import com.shawnkowalchuk.milo.core.designsystem.component.NavigationBarEntry
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme

/**
 * The root of the phone UI: applies the theme once, and frames every screen with the bottom
 * navigation bar. Which screen is showing is decided by the back stack, which lives here and is
 * handed to [MiloNavigation].
 *
 * It is also where a screen is left, by any of the three ways out (Android's Back, a screen's
 * own Back arrow, a button of the bottom bar), and so where the question is asked before
 * something typed and not saved is thrown away ([UnsavedWork]).
 */
@Composable
fun MiloApp(container: AppContainer) {
    MiloTheme {
        // Saved and restored by Navigation 3, so the screen that was showing comes back after
        // Android has put MilO away and brought it back.
        val backStack = rememberNavBackStack(HomeKey)
        val showing = topLevelOf(backStack)

        // Saved, so that turning the phone does not close the question.
        var unsavedWork by rememberSaveable(stateSaver = UnsavedWorkSaver) {
            mutableStateOf(UnsavedWork())
        }

        // Every way out of the screen on top comes through here. While that screen holds
        // something typed and not saved, the way is not taken: the question is asked first.
        fun leave(to: TopLevelDestination?, atOnce: () -> Unit) {
            val asked = unsavedWork.asked(to)
            if (asked == null) atOnce() else unsavedWork = asked
        }

        // The app draws behind the status and navigation bars (see MainActivity). Scaffold
        // reports how much room those bars and the bottom bar take, so the content starts and
        // ends clear of them.
        Scaffold(
            bottomBar = {
                MiloNavigationBar(
                    entries =
                        TopLevelDestination.entries.map { destination ->
                            NavigationBarEntry(
                                label = stringResource(destination.labelRes),
                                icon = destination.icon,
                                selected = destination == showing,
                                onClick = {
                                    leave(destination) {
                                        backStack.showTopLevel(destination.key, HomeKey)
                                    }
                                },
                            )
                        },
                )
            },
        ) { innerPadding ->
            MiloNavigation(
                container = container,
                backStack = backStack,
                leaveBack = { atOnce -> leave(to = null, atOnce) },
                onUnsavedWork = { held -> unsavedWork = unsavedWork.reported(held) },
                // The keyboard takes room from the screens and never slides them away: the
                // activity is not panned (adjustResize in the manifest), and the content ends
                // where the keyboard begins. The bars' room is marked as used first, so that
                // the keyboard's height is not added on top of the bottom bar it covers.
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .imePadding(),
            )
        }

        if (unsavedWork.asking) {
            ConfirmDialog(
                title = stringResource(R.string.leave_unsaved_title),
                text = stringResource(R.string.leave_unsaved_text),
                confirmLabel = stringResource(R.string.leave_unsaved_discard),
                dismissLabel = stringResource(R.string.leave_unsaved_keep),
                onConfirm = {
                    val to = unsavedWork.askedFor
                    unsavedWork = UnsavedWork()
                    backStack.leaveTop(to)
                },
                onDismiss = { unsavedWork = unsavedWork.kept() },
            )
        }
    }
}
