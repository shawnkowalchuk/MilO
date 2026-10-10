package com.shawnkowalchuk.milo.app

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.rememberNavBackStack
import com.shawnkowalchuk.milo.R
import com.shawnkowalchuk.milo.core.designsystem.component.ConfirmDialog
import com.shawnkowalchuk.milo.core.designsystem.component.LocalMiloClock
import com.shawnkowalchuk.milo.core.designsystem.component.MascotGreeting
import com.shawnkowalchuk.milo.core.designsystem.component.MiloNavigationBar
import com.shawnkowalchuk.milo.core.designsystem.component.NavigationBarEntry
import com.shawnkowalchuk.milo.core.designsystem.theme.MiloTheme
import com.shawnkowalchuk.milo.data.settings.FirstRunStage
import com.shawnkowalchuk.milo.feature.greeting.Greeting
import com.shawnkowalchuk.milo.feature.greeting.GreetingViewModel
import com.shawnkowalchuk.milo.feature.greeting.greeting
import com.shawnkowalchuk.milo.feature.onboarding.OnboardingScreen
import com.shawnkowalchuk.milo.feature.onboarding.OnboardingViewModel
import com.shawnkowalchuk.milo.feature.whatsnew.WhatsNewNoticeViewModel
import java.time.YearMonth

/**
 * The root of the phone UI: applies the theme once, and frames every screen with the bottom
 * navigation bar. Which screen is showing is decided by the back stack, which lives here and is
 * handed to [MiloNavigation].
 *
 * It is also where a screen is left, by any of the three ways out (Android's Back, a screen's
 * own Back arrow, a button of the bottom bar), and so where the question is asked before
 * something typed and not saved is thrown away ([UnsavedWork]).
 *
 * @param reportToOpen the month whose Report screen a tap on the monthly reminder asked for,
 * or null. The screen is opened once, and [onReportOpened] says that the request is dealt with.
 * @param homeAsked true while a tap on the daily check's notification is waiting for the Home
 * screen. Home is shown once, and [onHomeShown] says that the request is dealt with.
 * @param greetingAsked true from a fresh start until the greeting has been shown or dropped
 * (`feature/greeting`); [onGreetingDone] says that it has.
 */
@Composable
fun MiloApp(
    container: AppContainer,
    reportToOpen: YearMonth?,
    onReportOpened: () -> Unit,
    homeAsked: Boolean,
    onHomeShown: () -> Unit,
    greetingAsked: Boolean,
    onGreetingDone: () -> Unit,
) {
    MiloTheme {
        // MilO's clock, for the few components that show a day by themselves (the header's
        // date, the Log screen's "today"). Never the phone's own (ADR-005).
        CompositionLocalProvider(LocalMiloClock provides container.clock) {
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

            // A tap on the monthly reminder is one more way out of the screen on top, and goes the
            // same way as the others: while that screen holds something typed and not saved, the
            // question comes first. "Discard" then clears the way, and the report is opened by the
            // second run of this effect; "Keep editing" drops the request (below).
            LaunchedEffect(reportToOpen, unsavedWork.unsaved) {
                if (reportToOpen != null) {
                    leave(TopLevelDestination.TRIPS) {
                        backStack.showReport(ReportKey(reportToOpen.year, reportToOpen.monthValue))
                        onReportOpened()
                    }
                }
            }

            // A tap on the daily check's notification leads to Home, by the same gate: it is what
            // a press of the bar's Home button does, so whatever was open on top is closed, and
            // unsaved work is asked about first.
            LaunchedEffect(homeAsked, unsavedWork.unsaved) {
                if (homeAsked) {
                    leave(TopLevelDestination.HOME) {
                        backStack.showTopLevel(HomeKey, HomeKey)
                        onHomeShown()
                    }
                }
            }

            // The first start (2026-10-08): the page that says what MilO does comes before every
            // other screen, without the bottom bar, until OK is pressed; Setup then has its Done
            // button, which leads to Settings. Nothing is drawn for the moment the settings take to
            // be read.
            val onboarding: OnboardingViewModel =
                viewModel(factory = onboardingViewModelFactory(container))
            val firstRun by onboarding.stage.collectAsState()

            // After an update, the What's new screen opens once by itself, on top of whatever
            // shows (2026-10-08). The first start's OK counts as having seen it: a fresh install
            // is told what MilO does by the first page instead.
            val whatsNew: WhatsNewNoticeViewModel =
                viewModel(factory = whatsNewNoticeViewModelFactory(container))
            val whatsNewDue by whatsNew.due.collectAsState()
            LaunchedEffect(whatsNewDue) {
                if (whatsNewDue) {
                    backStack.openOnTop(WhatsNewKey)
                    whatsNew.onShown()
                }
            }

            // The greeting (2026-10-09): on a fresh start the mascot waves over Home, unless
            // something else has that moment. One that is dropped is told so at once, so that it
            // does not show up later, over a screen Shawn has gone to since.
            val greetings: GreetingViewModel =
                viewModel(factory = greetingViewModelFactory(container))
            val mascotTrusted by greetings.trusted.collectAsState()
            val greeting =
                greeting(
                    asked = greetingAsked,
                    firstRun = firstRun,
                    onHome = backStack.lastOrNull() == HomeKey,
                    notificationTapped = reportToOpen != null || homeAsked,
                    trusted = mascotTrusted,
                )
            LaunchedEffect(greeting) {
                if (greeting == Greeting.DROPPED) onGreetingDone()
            }

            when (firstRun) {
                null -> Unit

                FirstRunStage.INTRO ->
                    OnboardingScreen(
                        onOk = {
                            onboarding.onOk()
                            whatsNew.onFirstStart()
                            backStack.openOnTop(SetupKey)
                        },
                    )

                else -> {
                    val setupDone: (() -> Unit)? =
                        if (firstRun == FirstRunStage.SETUP) {
                            {
                                onboarding.onSetupDone()
                                backStack.showTopLevel(SettingsKey, HomeKey)
                            }
                        } else {
                            null
                        }
                    // The app draws behind the status and navigation bars (see MainActivity).
                    // Scaffold reports how much room those bars and the bottom bar take, so the
                    // content starts and ends clear of them.
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
                            // The keyboard takes room from the screens and never slides them away:
                            // the activity is not panned (adjustResize in the manifest), and the
                            // content ends where the keyboard begins. The bars' room is marked as
                            // used first, so that the keyboard's height is not added on top of the
                            // bottom bar it covers.
                            modifier =
                                Modifier
                                    .padding(innerPadding)
                                    .consumeWindowInsets(innerPadding)
                                    .imePadding(),
                            onSetupDone = setupDone,
                        )
                    }
                }
            }

            // Over the screen and the bottom bar alike.
            if (greeting == Greeting.SHOWING) {
                MascotGreeting(
                    onDone = onGreetingDone,
                    beforeStart = greetings::starting,
                    onShown = greetings::onShown,
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
                    onDismiss = {
                        unsavedWork = unsavedWork.kept()
                        // Staying on the form is also the answer to a notification that was
                        // tapped, the reminder's or the daily check's.
                        onReportOpened()
                        onHomeShown()
                    },
                )
            }
        }
    }
}
