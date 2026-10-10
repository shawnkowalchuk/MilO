package com.shawnkowalchuk.milo.feature.greeting

import com.shawnkowalchuk.milo.data.settings.FirstRunStage

/** Where MilO's greeting, the mascot's walk and wave over Home, stands. */
enum class Greeting {
    /** No greeting is asked for: this start has had its greeting, or was given none. */
    NONE,

    /** One is asked for, and the settings that decide it have not been read yet. */
    WAITING,

    /** The mascot is on the screen. */
    SHOWING,

    /** One was asked for and something else has the screen: this start goes without. */
    DROPPED,
}

/**
 * Decides the greeting (2026-10-09, Shawn: "Greeting over Home"). MilO greets once for every
 * fresh start of its screen, and only when nothing else wants that moment:
 *
 * - **not during the first start:** the page that says what MilO does, and Setup after it,
 *   have the screen to themselves;
 * - **not unless Home is the screen on top:** What's new, which opens by itself after an
 *   update, comes first, and so does any screen a notification opened;
 * - **not after a tap on a notification:** that tap asked for something, and gets it at once.
 *
 * A greeting that is dropped does not come later. One that is showing is dropped as well when
 * any of this changes under it, which is how What's new, opening a moment late, ends it.
 *
 * @param asked true from a fresh start of the activity until the greeting was shown or dropped.
 * @param firstRun how far the first start has come, or null while the settings are being read.
 * @param onHome true while Home is the screen on top.
 * @param notificationTapped true while a tapped notification waits to be dealt with.
 */
fun greeting(
    asked: Boolean,
    firstRun: FirstRunStage?,
    onHome: Boolean,
    notificationTapped: Boolean,
): Greeting = when {
    !asked -> Greeting.NONE
    firstRun == null -> Greeting.WAITING
    firstRun == FirstRunStage.DONE && onHome && !notificationTapped -> Greeting.SHOWING
    else -> Greeting.DROPPED
}
