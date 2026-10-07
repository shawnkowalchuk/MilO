package com.shawnkowalchuk.milo.app

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

// Leaving a screen takes away whatever was typed on it and not saved, and what takes something
// away asks first. Three ways lead out of a screen that is opened on top of another: Android's
// Back, the screen's own Back arrow, and the buttons of the bottom bar. Only the app carries
// all three out, so the question is asked here, once, and not by the screen, which sees only
// its own arrow. Pure values and functions, so they are tested without a phone.

/**
 * Whether the screen on top holds unsaved work, and the question that is asked before it is
 * left then.
 *
 * @param unsaved true while the screen on top holds something typed and not saved. The screen
 * says so itself (today only the edit screen does).
 * @param asking true while "Leave without saving?" is on the screen.
 * @param askedFor where the way out that is being asked about leads: a screen of the bottom
 * bar, or null for Back, Android's or the screen's own arrow.
 */
internal data class UnsavedWork(
    val unsaved: Boolean = false,
    val asking: Boolean = false,
    val askedFor: TopLevelDestination? = null,
)

/**
 * The screen on top has said whether it holds unsaved work. Once it holds none (it was saved,
 * put back to what it opened with, or it is gone) there is nothing left to ask about, so a
 * question that is still open is closed as well.
 */
internal fun UnsavedWork.reported(held: Boolean): UnsavedWork =
    if (held) copy(unsaved = true) else UnsavedWork()

/**
 * A way out was pressed.
 *
 * @param to a screen of the bottom bar, or null for Back.
 * @return the state with the question asked; or null if nothing typed is at stake, and the way
 * out is taken at once.
 */
internal fun UnsavedWork.asked(to: TopLevelDestination?): UnsavedWork? =
    if (unsaved) copy(asking = true, askedFor = to) else null

/** "Keep editing", Back, or a press beside the question: the screen stays, with all of it. */
internal fun UnsavedWork.kept(): UnsavedWork = copy(asking = false, askedFor = null)

/**
 * Saves it with the rest of the screens, so that turning the phone does not close the question.
 * After Android has ended MilO in the background the screen comes back without what was typed,
 * says so at once, and [reported] clears what was saved here.
 */
internal val UnsavedWorkSaver: Saver<UnsavedWork, Any> =
    listSaver(
        save = { listOf(it.unsaved, it.asking, it.askedFor?.name) },
        restore = { saved ->
            UnsavedWork(
                unsaved = saved[0] as Boolean,
                asking = saved[1] as Boolean,
                // By name, and an unknown one as Back: the bar's buttons were renamed once
                // (SETUP became SETTINGS on 2026-10-07), and a name saved by an older build
                // must not crash the screens, which would take the trip service with them.
                askedFor =
                    (saved[2] as String?)?.let { name ->
                        TopLevelDestination.entries.firstOrNull { it.name == name }
                    },
            )
        },
    )
